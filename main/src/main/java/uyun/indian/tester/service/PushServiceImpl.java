package uyun.indian.tester.service;


import com.codahale.metrics.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uyun.indian.tester.*;
import uyun.indian.tester.report.Reporter;
import uyun.indian.tester.service.api.PushService;
import uyun.indian.writer.api.WriterService;
import uyun.indian.writer.api.entity.Datapoint;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;


/**
 * Created by JasonSong on 2017/12/21.
 * <p>
 * 性能测试
 */

@Service
public class PushServiceImpl implements PushService {
    @Autowired
    WriterService indianWriterService;
    @Value("${tenant.id}")
    String tenantId;
    @Value("${total.type}")
    int totalType;
    @Value("${writer.thread}")
    int writerThread;
    @Value("${total.key}")
    int totalKey;
    @Value("${total.value}")
    int totalValue;
    @Value("${data.duration}")
    int dataDuration;
    @Value("${writer.duration}")
    int writerDuration;
    @Value("${cycle}")
    long cycle;
    @Value("${max.resource}")
    int maxResource;
    @Autowired
    Reporter reporter;
    private static final Logger logger = LoggerFactory.getLogger(PushServiceImpl.class);
    public static final String METRIC_NAME = "TestMetric";
    final static long ONE_SECOND = 1000;
    MetricRegistry METRIC_REGISTRY;
    PushTestThread pushTestThread;
    PushThread pushThread;
    volatile com.codahale.metrics.Histogram histogram;
    volatile com.codahale.metrics.Meter meter;
    double errorPer = 0;
    AtomicInteger totalError;
    AtomicInteger pushSucTime;
    AtomicBoolean printNoProvider = new AtomicBoolean();

    Map<String, String> tags = new HashMap<>();

    @PostConstruct
    void init() {
        for (int i = 0; i < totalKey; i++) {
            for (int j = 0; j < totalValue; j++) {
                tags.put("key" + i, "value" + j);
            }
        }
    }

    /**
     * push线程
     * <p>
     * return
     */

    private class PushTestThread extends Thread {
        Map<String, String> tags;
        String metric;
        long time;
        long start;

        public PushTestThread(Map<String, String> tags, String metric, long time, long start) {
            this.tags = tags;
            this.metric = metric;
            this.time = time;
            this.start = start;
        }

        @Override
        public void run() {
            for (int i = 0; i < totalType && System.currentTimeMillis() - start <= cycle * ONE_SECOND; i++) {
                long callDuration;
                try {
                    long startTime = System.nanoTime();
                    tags.put("ip", "" + (int) (Math.random() * writerThread));
                    indianWriterService.push(tenantId, new Datapoint("testTps" + metric, tags, time, Math.random() * 1000));
                    callDuration = System.nanoTime() - startTime;
                    time += 100;
                    if (callDuration <= 2 * ONE_SECOND * 1000 * 1000) {
                        meter.mark();
                        histogram.update(callDuration);
                    } else {
                        totalError.incrementAndGet();
                    }
                } catch (Throwable t) {
                    totalError.incrementAndGet();
                    logger.warn("Push test occur error ", t);
                    if (t.toString().contains("No provider available") && !printNoProvider.get()) {
                        printNoProvider.set(true);
                        System.out.println("没有可用的指标库writer进程！");
                        cycle = 0;
                    }
                }
            }
        }
    }

    private class PushThread extends Thread {
        String metric;
        long pushTime;
        long end;
        int maxRes;

        public PushThread(String metric, long time, int maxRes, long end) {
            this.metric = metric;
            this.pushTime = time;
            this.end = end;
            this.maxRes = maxRes;
        }

        @Override
        public void run() {
            Datapoint[] datapoints = new Datapoint[maxRes];
            for (int i = 0; i < maxRes && System.currentTimeMillis() < end; i++) {
                pushTime--;
                tags.put("ip", "" + i);
                datapoints[i] = new Datapoint(metric, tags, pushTime, Math.random() * 1000);
            }
            try {
                indianWriterService.push(tenantId, datapoints);
                pushSucTime.addAndGet(maxRes);
            } catch (Throwable t) {
                logger.warn("Push  occur error ", t);
            }
        }
    }

    synchronized public int push() {
        System.out.println("tenantId:" + tenantId);
        long currentTime = System.currentTimeMillis();
        totalError = new AtomicInteger();
        METRIC_REGISTRY = new MetricRegistry();
        meter = METRIC_REGISTRY.meter(name(PushServiceImpl.class, "push meter"));
        histogram = METRIC_REGISTRY.histogram(name(PushServiceImpl.class, "push histogram"));
        ExecutorService e = Executors.newFixedThreadPool(writerThread);

        //数据上报时间
        long pushTime = currentTime - (dataDuration + 1) * 24 * 60 * 60 * ONE_SECOND;
        long start = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - start <= cycle * ONE_SECOND) {
                for (int i = 0; i < writerThread; i++) {
                    tags.put("ip", "" + i);
                    tags.put("object", "" + i);
                    pushTestThread = new PushTestThread(tags, METRIC_NAME, pushTime, start);
                    e.execute(pushTestThread);
                    pushTime += 15 * ONE_SECOND;
                }
            }
            e.shutdown();
            Thread.sleep(2 * ONE_SECOND);
        } catch (Throwable t) {
            logger.warn("", t);
        } finally {
            if (!e.isShutdown())
                e.shutdownNow();
        }
        errorPer = (double) totalError.get() / (double) (totalError.get() + meter.getCount());
        reporter.report(histogram, meter, errorPer, RequestType.PUSH, (cycle));
        int maxRes = (int) histogram.getCount() / 100;
        return maxRes < maxResource ? maxRes : maxResource;
    }

    synchronized public void pushData(int maxRes) {
        pushSucTime = new AtomicInteger();
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - dataDuration * 24 * 60 * 60 * ONE_SECOND;
        long end = currentTime + writerDuration * 60 * ONE_SECOND;
        long pushTime = currentTime;
        int pushLoop = 0;
        if (maxRes <= 0) {
            maxRes = 1;
        }
        ExecutorService e = Executors.newFixedThreadPool(500);
        SimpleDateFormat format = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss SSS");
        System.out.println(format.format(new Date(currentTime)) + " ,开始写入数据...");
        try {
            java.util.Timer myTimer = new java.util.Timer();
            myTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    System.out.println("到" + format.format(new Date(System.currentTimeMillis()))
                            + " 为止，共写入了" + pushSucTime.get() + "条数据");
                }
            }, 20 * ONE_SECOND, 20 * ONE_SECOND);
            while (System.currentTimeMillis() < end && pushTime > startTime) {
                pushThread = new PushThread(METRIC_NAME, pushTime, maxRes, end);
                e.execute(pushThread);
                pushTime -= 15 * ONE_SECOND;
                pushLoop++;
                //每上传10W条数据，sleep 1 s。
                if (pushLoop * maxRes > 100000) {
                    Thread.sleep(ONE_SECOND);
                    pushLoop = 0;
                }
            }
            myTimer.cancel();
            System.out.println("数据写入结束，写入了 "
                    + maxRes + " 台设备的数据。在"
                    + format.format(new Date(pushTime)) + " 到"
                    + format.format(new Date(currentTime)) + "这段时间内。共写入了" +
                    pushSucTime.get() + "条数据");
            e.shutdown();
        } catch (Throwable t) {
            logger.warn("", t);
        } finally {
            if (!e.isShutdown())
                e.shutdownNow();
        }
    }
}
