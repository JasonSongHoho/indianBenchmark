package uyun.indian.tester.service;


import com.codahale.metrics.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uyun.indian.reader.api.ReaderService;
import uyun.indian.reader.api.entity.*;
import uyun.indian.tester.*;
import uyun.indian.tester.report.Reporter;
import uyun.indian.tester.service.api.QueryService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;
import static uyun.indian.tester.service.PushServiceImpl.METRIC_NAME;


/**
 * Created by JasonSong on 2017/12/21.
 * <p>
 * 性能测试
 */

@Service
public class QueryServiceImpl implements QueryService {
    @Autowired
    ReaderService readerService;
    @Value("${tenant.id}")
    String tenantId;
    @Value("${reader.thread}")
    int readerThread;
    @Value("${data.duration}")
    int dataDuration;
    @Value("${reader.timeout}")
    long readerTimeout;
    @Autowired
    Reporter reporter;
    private static final Logger logger = LoggerFactory.getLogger(QueryServiceImpl.class);
    MetricRegistry METRIC_REGISTRY;
    QueryThread queryThread;
    volatile com.codahale.metrics.Histogram histogram;
    volatile com.codahale.metrics.Meter meter;
    final long ONE_SECOND = 1000;
    double errorPer = 0;

    AtomicInteger totalError;
    AtomicBoolean printNoProvider = new AtomicBoolean();

    /**
     * query 线程
     * <p>
     * return
     */
    private class QueryThread extends Thread {

        Map<String, String> tags;
        long duration;
        DatapointQuery datapointQuery = new DatapointQuery();

        public QueryThread(Map<String, String> tags, String metric, long startTime, long endTime, long duration) {
            this.tags = tags;
            this.duration = duration;

            DatapointQueryTime datapointQueryTime = new DatapointQueryTime();
            datapointQueryTime.setStart(startTime);
            datapointQueryTime.setEnd(endTime);
            datapointQueryTime.setInterval(1);
            if (endTime - startTime <= 24 * 60 * 60 * ONE_SECOND) {
                datapointQueryTime.setInterval_unit("seconds");
            } else {
                datapointQueryTime.setInterval_unit("hour");
            }
            datapointQueryTime.setAggregator(Aggregator.AVG);
            datapointQueryTime.setAlign_sampling(true);
            DatapointQueryGroupBy datapointQueryGroupBy = new DatapointQueryGroupBy();
            List<String> list = new ArrayList<>();
            list.add("ip");
            datapointQueryGroupBy.setTagKeys(list);
            datapointQuery.setTime(datapointQueryTime);
            datapointQuery.setUseCache(false);
            datapointQuery.setTags(tags);
            datapointQuery.setGroupBy(datapointQueryGroupBy);
            datapointQuery.setMetric(metric);
        }

        @Override
        public void run() {
            Long callDuration;
            Long callStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - callStart <= duration) {
                try {
                    long startTime = System.nanoTime();
                    readerService.query(tenantId, datapointQuery);
                    callDuration = System.nanoTime() - startTime;
                    if (callDuration <= readerTimeout * 1000 * 1000) {
                        meter.mark();
                        histogram.update(callDuration);
                    } else {
                        logger.warn("Query test timeout ");
                        totalError.incrementAndGet();
                    }
                } catch (Throwable t) {
                    logger.warn("Query test occur error ", t);
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


    synchronized public void query(Long duration, Long last, Boolean isGroupBy) {
        Long endTime = System.currentTimeMillis() - 60 * 60 * ONE_SECOND;
        Long startTime = endTime - last;
        totalError = new AtomicInteger();
        METRIC_REGISTRY = new MetricRegistry();
        histogram = METRIC_REGISTRY.histogram(name(QueryServiceImpl.class, "query histogram"));
        meter = METRIC_REGISTRY.meter(name(QueryServiceImpl.class, "query meter"));
        ExecutorService e = Executors.newFixedThreadPool(readerThread);
        long startExecuteTime = System.currentTimeMillis();
        try {
            Map<String, String> tags = new HashMap<>();
            for (int i = 0; i < readerThread; i++) {
                if (!isGroupBy) {
                    tags.put("ip", "" + i);
                }
                queryThread = new QueryThread(tags, METRIC_NAME, startTime, endTime, duration);
                e.execute(queryThread);
            }
            java.util.Timer myTimer = new java.util.Timer();
            myTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    long runDuration = System.currentTimeMillis() - startExecuteTime;
                    if (runDuration >= duration) {
                        e.shutdownNow();
                        errorPer = (double) totalError.get() / (double) (totalError.get() + meter.getCount());
                        Double lastLong = new Double(last) / (60 * 60 * ONE_SECOND);
                        reporter.report(histogram, meter, errorPer, RequestType.QUERY, runDuration, lastLong, isGroupBy);
                        myTimer.cancel();
                    }
                }
            }, ONE_SECOND, ONE_SECOND);
        } catch (Throwable t) {
            logger.warn("query occur error ", t);
        }

    }

    public void queryGroup(List<Long> lastList) {
        Long duration;
        for (Long last : lastList) {
            try {
                duration = 60 * ONE_SECOND;
                query(duration, last, false);
                Thread.sleep(duration + 5 * ONE_SECOND);
                if (last > 7 * 24 * 60 * 60 * ONE_SECOND) {
                    //若查询时间超过7天，查询持续时间为 readerTimeout
                    duration = readerTimeout;
                } else if (last > 24 * 60 * 60 * ONE_SECOND) {
                    //若查询时间超过24小时，查询持续时间为 6分钟
                    long tmpDuration = duration * 6;
                    duration = tmpDuration < readerTimeout ? tmpDuration : readerTimeout;
                } else if (last > 60 * 60 * ONE_SECOND) {
                    //若查询时间超过1小时，查询持续时间为 2分钟
                    long tmpDuration = duration * 2;
                    duration = tmpDuration < readerTimeout ? tmpDuration : readerTimeout;
                }
                query(duration, last, true);
                Thread.sleep(duration + 10 * ONE_SECOND);
            } catch (InterruptedException e) {
                logger.warn("", e);
            }
        }
    }


}
