package uyun.indian.tester.service;


import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uyun.indian.reader.api.ReaderService;
import uyun.indian.tester.report.Reporter;
import uyun.indian.tester.RequestType;
import uyun.indian.tester.service.api.QueryLastService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;


/**
 * Created by JasonSong on 2018/1/9.
 */

@Service
public class QueryLastServiceImpl implements QueryLastService {

    @Value("${tenant.id}")
    String tenantId;
    @Value("${readerLast.thread}")
    int readerLastThread;
    @Autowired
    Reporter reporter;
    @Autowired
    ReaderService readerService;
    QueryLastThread queryLastThread;
    volatile com.codahale.metrics.Histogram histogram;
    volatile com.codahale.metrics.Meter meter;
    final long ONE_SECOND = 1000;
    double errorPer = 0;
    private static final Logger logger = LoggerFactory.getLogger(QueryLastServiceImpl.class);
    MetricRegistry METRIC_REGISTRY;

    AtomicInteger totalError;
    AtomicBoolean printNoProvider = new AtomicBoolean();

    /**
     * query last 线程
     * <p>
     * return
     */

    private class QueryLastThread extends Thread {

        String cachedTagKey;
        String cachedTagValue;
        Long duration;

        public QueryLastThread(String cachedTagKey, String cachedTagValue, Long duration) {
            this.cachedTagKey = cachedTagKey;
            this.cachedTagValue = cachedTagValue;
            this.duration = duration;
        }

        @Override
        public void run() {
            Long callStart = System.currentTimeMillis();
            long callDuration;
            while (totalError.get() == 0 && System.currentTimeMillis() - callStart <= duration) {
                try {
                    long startTime = System.nanoTime();
                    readerService.query(tenantId, cachedTagKey, cachedTagValue);
                    callDuration = System.nanoTime() - startTime;
                    if (callDuration <= 10 * ONE_SECOND * 1000 * 1000) {
                        meter.mark();
                        histogram.update(callDuration);
                    } else {
                        logger.warn("QueryLast test timeout ");
                        totalError.incrementAndGet();
                    }
                } catch (Throwable t) {
                    logger.warn("QueryLast test occur error ", t);
                    totalError.incrementAndGet();
                    if (t.toString().contains("No provider available") && !printNoProvider.get()) {
                        printNoProvider.set(true);
                        System.out.println("没有可用的指标库reader进程！");
                        System.exit(0);
                    }
                }
            }
        }
    }

    @Override
    public void queryLast() {
        String cachedTagKey = "object";
        String cachedTagValue = "1";
        queryLast(cachedTagKey, cachedTagValue, 60 * ONE_SECOND);
    }

    @Override
    synchronized public void queryLast(String cachedTagKey, String cachedTagValue, Long duration) {
        totalError = new AtomicInteger();
        METRIC_REGISTRY = new MetricRegistry();
        meter = METRIC_REGISTRY.meter(name(QueryLastServiceImpl.class, "queryLastMeter"));
        histogram = METRIC_REGISTRY.histogram(name(QueryLastServiceImpl.class, "queryLast"));
        ExecutorService e = Executors.newFixedThreadPool(readerLastThread);
        try {
            for (int i = 0; i < readerLastThread; i++) {
                queryLastThread = new QueryLastThread(cachedTagKey, cachedTagValue, duration);
                e.execute(queryLastThread);
            }
            java.util.Timer myTimer = new java.util.Timer();
            long startScheduleTime = System.currentTimeMillis();
            myTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    long runDuration = System.currentTimeMillis() - startScheduleTime;
                    if (totalError.get() > 0 || runDuration >= duration) {
                        e.shutdownNow();
                        errorPer = (double) totalError.get() / (double) (totalError.get() + meter.getCount());
                        reporter.report(histogram, meter, errorPer, RequestType.QUERYLAST, runDuration);
                        myTimer.cancel();
                    }
                }
            }, ONE_SECOND, ONE_SECOND);
        } catch (Throwable t) {
            logger.warn("queryLast occur error ", t);
        }
    }
}
