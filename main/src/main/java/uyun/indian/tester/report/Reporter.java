package uyun.indian.tester.report;

import com.codahale.metrics.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uyun.indian.tester.RequestType;


import java.io.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Locale.CHINESE;
import static java.util.concurrent.TimeUnit.SECONDS;

@Component
public class Reporter {

    @Value("${total.type:100}")
    int totalType;
    @Value("${result.path:testResult}")
    String filePath;
    @Value("${writer.thread}")
    int writerThread;
    @Value("${readerLast.thread}")
    int readerLastThread;
    @Value("${reader.thread}")
    int readerThread;
    private static final int CONSOLE_WIDTH = 80;
    private static PrintStream output = System.out;
    private long durationFactor = 1000000;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private String durationUnitStr = durationUnit.toString();
    private long rateFactor = 1;
    private TimeUnit rateUnit = SECONDS;
    private String rateUnitStr = rateUnit.toString();
    private Locale locale = CHINESE;
    private File resultFile;
    private File summaryFile;
    private Writer out;
    private static final Logger logger = LoggerFactory.getLogger(Reporter.class);
    private int report30d = 0;
    private static final String PUSH_MAX = "pushMax";
    private static final String PUSH_TPS = "pushTps";
    private static final String QUERY_LAST_TPS = "queryLastTps";
    private static final String QUERY_30m_TPS = "query30mTps";
    private static final String QUERY_30m_MEAN = "query30mMean";
    private static final String QUERY_30m_MIN = "query30mMin";
    private static final String QUERY_30m_MAX = "query30mMax";
    private static final String QUERY_24h_TPS = "query24hTps";
    private static final String QUERY_24h_MEAN = "query24hMean";
    private static final String QUERY_24h_MIN = "query24hMin";
    private static final String QUERY_24h_MAX = "query24hMax";
    private static final String QUERY_7d_TPS = "query7dTps";
    private static final String QUERY_7d_MEAN = "query7dMean";
    private static final String QUERY_7d_MIN = "query7dMin";
    private static final String QUERY_7d_MAX = "query7dMax";
    private static final String QUERY_30d_TPS = "query30dTps";
    private static final String QUERY_30d_MEAN = "query30dMean";
    private static final String QUERY_30d_MIN = "query30dMin";
    private static final String QUERY_30d_MAX = "query30dMax";

    private Map<String, String> pushResult = new HashMap<>();
    private Map<String, String> queryLastResult = new HashMap<>();
    private Map<String, String> queryResult = new HashMap<>();
    private Map<String, String> queryGroupByResult = new HashMap<>();


    public void report(Histogram histogram, Meter meter, double errorPer, RequestType requestType, Long duration) {
        report(histogram, meter, errorPer, requestType, duration, null, false);
    }

    synchronized public void report(Histogram histogram, Meter meter, double errorPer, RequestType requestType, Long duration, Double last, Boolean isGroupBy) {
        if (requestType == RequestType.PUSH) {
            resultFile = new File(filePath + File.separator + "pushTestResult.txt");
        } else if (requestType == RequestType.QUERY) {
            resultFile = new File(filePath + File.separator + "queryTestResult.txt");
        } else {
            resultFile = new File(filePath + File.separator + "queryLastResult.txt");
        }
        if (!resultFile.getParentFile().exists()) {
            resultFile.getParentFile().mkdirs();
        }
        try {
            out = new FileWriter(resultFile, true);
        } catch (IOException e) {
            logger.warn("", e);
        }

        stdoutAndFile(createBanner("== " + new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(System.currentTimeMillis()), '='));
        final Snapshot snapshot = histogram.getSnapshot();
        DecimalFormat df = new DecimalFormat("######0.000");
        if (requestType == RequestType.PUSH) {
            pushResult.put(PUSH_MAX, "" + meter.getCount() / totalType);
            pushResult.put(PUSH_TPS, "" + df.format(meter.getMeanRate()));
            stdoutAndFile(createBanner("-- push metric ", '-'));
            stdoutAndFile(String.format(locale, "          push 线程并发量为:%d ", writerThread));
            stdoutAndFile(String.format(locale, "          total push : %d ", meter.getCount()));
            stdoutAndFile(String.format(locale, "          可支持设备数: %d", meter.getCount() / totalType));
            stdoutAndFile(String.format(locale, "              TPS  = %2.3f push/%s", convertRate(meter.getMeanRate()), getRateUnit()));

        } else if (requestType == RequestType.QUERY) {
            Map<String, String> result = new HashMap<>();
            switch (last.toString()) {
                case "0.5":
                    result.put(QUERY_30m_TPS, "" + df.format(convertRate(meter.getMeanRate())));
                    result.put(QUERY_30m_MEAN, "" + df.format(convertDuration(snapshot.getMean())));
                    result.put(QUERY_30m_MIN, "" + df.format(convertDuration(snapshot.getMin())));
                    result.put(QUERY_30m_MAX, "" + df.format(convertDuration(snapshot.getMax())));
                    break;
                case "24.0":
                    result.put(QUERY_24h_TPS, "" + df.format(convertRate(meter.getMeanRate())));
                    result.put(QUERY_24h_MEAN, "" + df.format(convertDuration(snapshot.getMean())));
                    result.put(QUERY_24h_MIN, "" + df.format(convertDuration(snapshot.getMin())));
                    result.put(QUERY_24h_MAX, "" + df.format(convertDuration(snapshot.getMax())));
                    break;
                case "168.0":
                    result.put(QUERY_7d_TPS, "" + df.format(convertRate(meter.getMeanRate())));
                    result.put(QUERY_7d_MEAN, "" + df.format(convertDuration(snapshot.getMean())));
                    result.put(QUERY_7d_MIN, "" + df.format(convertDuration(snapshot.getMin())));
                    result.put(QUERY_7d_MAX, "" + df.format(convertDuration(snapshot.getMax())));
                    break;
                case "720.0":
                    result.put(QUERY_30d_TPS, "" + df.format(convertRate(meter.getMeanRate())));
                    result.put(QUERY_30d_MEAN, "" + df.format(convertDuration(snapshot.getMean())));
                    result.put(QUERY_30d_MIN, "" + df.format(convertDuration(snapshot.getMin())));
                    result.put(QUERY_30d_MAX, "" + df.format(convertDuration(snapshot.getMax())));
                    report30d++;
                    break;
                default:
                    result.put("query" + last + "HoursTps", "" + df.format(convertRate(meter.getMeanRate())));
                    result.put("query" + last + "HoursMean", "" + df.format(convertDuration(snapshot.getMean())));
                    result.put("query" + last + "HoursMin", "" + df.format(convertDuration(snapshot.getMin())));
                    result.put("query" + last + "HoursMax", "" + df.format(convertDuration(snapshot.getMax())));
                    break;
            }
            if (!isGroupBy) {
                queryResult.putAll(result);
                stdoutAndFile(createBanner("-- query  " + last + " hours data  metrics ", '-'));
            } else {
                queryGroupByResult.putAll(result);
                stdoutAndFile(createBanner("-- query  " + last + " hours data  group by resource tag ", '-'));
            }
            stdoutAndFile(String.format(locale, "          query 线程并发量为:%d ", readerThread));
            stdoutAndFile(String.format(locale, "          total query : %d ", meter.getCount()));
            stdoutAndFile(String.format(locale, "              TPS  = %2.3f query/%s", convertRate(meter.getMeanRate()), SECONDS.toString()));
        } else {
            queryLastResult.put(QUERY_LAST_TPS, "" + df.format(meter.getMeanRate()));
            stdoutAndFile(createBanner("-- queryLast metric ", '-'));
            stdoutAndFile(String.format(locale, "          queryLast 线程并发量为: %d ", readerLastThread));
            stdoutAndFile(String.format(locale, "          total queryLast : %d ", meter.getCount()));
            stdoutAndFile(String.format(locale, "              TPS  = %2.3f queryLast/%s", convertRate(meter.getMeanRate()), getRateUnit()));
        }

        stdoutAndFile(String.format(locale, "             error = %2.3f ", errorPer * 100) + "%");
        stdoutAndFile(String.format(locale, "               min = %2.3f %s", convertDuration(snapshot.getMin()), getDurationUnit()));
        stdoutAndFile(String.format(locale, "               max = %2.3f %s", convertDuration(snapshot.getMax()), getDurationUnit()));
        stdoutAndFile(String.format(locale, "              mean = %2.3f %s", convertDuration(snapshot.getMean()), getDurationUnit()));
        stdoutAndFile(String.format(locale, "            median = %2.3f %s", convertDuration(snapshot.getMedian()), getDurationUnit()));
        stdoutAndFile(String.format(locale, "              75%% <= %2.3f %s", convertDuration(snapshot.get75thPercentile()), getDurationUnit()));
        stdoutAndFile(String.format(locale, "              95%% <= %2.3f %s", convertDuration(snapshot.get95thPercentile()), getDurationUnit()));
        stdoutAndFile(String.format(locale, "              99%% <= %2.3f %s", convertDuration(snapshot.get99thPercentile()), getDurationUnit()));
        stdoutAndFile(System.getProperty("line.separator"));
        if (report30d >= 2) {
            report();
        }
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.warn("Writer  close occur error ", e);
        }
    }

    synchronized public void report() {
        summaryFile = new File(filePath + File.separator + "summary.txt");
        if (!summaryFile.getParentFile().exists()) {
            summaryFile.getParentFile().mkdirs();
        }

        try {
            out = new FileWriter(summaryFile, true);
        } catch (IOException e) {
            logger.warn("", e);
        }

        stdoutAndFile(createBanner("=", '='));
        stdoutAndFile(createBanner("== " + new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(System.currentTimeMillis())
                + " == 性能测试汇总报告 ", '='));
        stdoutAndFile(createBanner("=", '='));
        stdoutAndFile("");
        stdoutAndFile("标准设备写入支持数量: " + pushResult.get(PUSH_MAX));
        stdoutAndFile("指标写入TPS: " + pushResult.get(PUSH_TPS) + "  request/second");
        stdoutAndFile("");
        stdoutAndFile("指标最后1次读取（queryLast 方法）TPS: " + queryLastResult.get(QUERY_LAST_TPS) + "  request/second");
        stdoutAndFile("");
        stdoutAndFile("指定设备指定指标读取最近30 minute数据: ");
        stdoutAndFile("    TPS: " + queryResult.get(QUERY_30m_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryResult.get(QUERY_30m_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryResult.get(QUERY_30m_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryResult.get(QUERY_30m_MAX) + " milliseconds");
        stdoutAndFile("指定设备指定指标读取最近24 hour数据: ");
        stdoutAndFile("    TPS: " + queryResult.get(QUERY_24h_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryResult.get(QUERY_24h_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryResult.get(QUERY_24h_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryResult.get(QUERY_24h_MAX) + " milliseconds");
        stdoutAndFile("指定设备指定指标读取最近7 day数据: ");
        stdoutAndFile("    TPS: " + queryResult.get(QUERY_7d_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryResult.get(QUERY_7d_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryResult.get(QUERY_7d_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryResult.get(QUERY_7d_MAX) + " milliseconds");
        stdoutAndFile("指定设备指定指标读取最近30 day 数据: ");
        stdoutAndFile("    TPS: " + queryResult.get(QUERY_30d_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryResult.get(QUERY_30d_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryResult.get(QUERY_30d_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryResult.get(QUERY_30d_MAX) + " milliseconds");
        stdoutAndFile("");
        stdoutAndFile("指定设备按设备标签分组读取最近30 minute数据: ");
        stdoutAndFile("    TPS: " + queryGroupByResult.get(QUERY_30m_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryGroupByResult.get(QUERY_30m_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryGroupByResult.get(QUERY_30m_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryGroupByResult.get(QUERY_30m_MAX) + " milliseconds");
        stdoutAndFile("指定设备按设备标签分组读取最近24 hour数据: ");
        stdoutAndFile("    TPS: " + queryGroupByResult.get(QUERY_24h_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryGroupByResult.get(QUERY_24h_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryGroupByResult.get(QUERY_24h_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryGroupByResult.get(QUERY_24h_MAX) + " milliseconds");
        stdoutAndFile("指定设备按设备标签分组读取最近7 day数据: ");
        stdoutAndFile("    TPS: " + queryGroupByResult.get(QUERY_7d_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryGroupByResult.get(QUERY_7d_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryGroupByResult.get(QUERY_7d_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryGroupByResult.get(QUERY_7d_MAX) + " milliseconds");
        stdoutAndFile("指定设备按设备标签分组读取最近30 day 数据: ");
        stdoutAndFile("    TPS: " + queryGroupByResult.get(QUERY_30d_TPS) + "  request/second");
        stdoutAndFile("    平均耗时: " + queryGroupByResult.get(QUERY_30d_MEAN) + " milliseconds");
        stdoutAndFile("    最小耗时: " + queryGroupByResult.get(QUERY_30d_MIN) + " milliseconds");
        stdoutAndFile("    最大耗时: " + queryGroupByResult.get(QUERY_30d_MAX) + " milliseconds");
        stdoutAndFile("");
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.warn("Writer  close occur error ", e);
        }
        System.exit(0);
    }

    private void stdoutAndFile(String content) {
        output.println(content);
        try {
            out.write(content + System.getProperty("line.separator"));
        } catch (Throwable e) {
            logger.warn("stdoutAndFile error ", e);
        }
    }


    private String createBanner(String s, char c) {
        String result = s;
        for (int i = 0; i < (CONSOLE_WIDTH - s.length() - 1); i++) {
            result += c;
        }
        return result;
    }

    protected String getRateUnit() {
        return rateUnitStr;
    }

    protected String getDurationUnit() {
        return durationUnitStr;
    }

    protected double convertDuration(double duration) {
        return duration / durationFactor;
    }

    protected double convertRate(double rate) {
        return rate * rateFactor;
    }


    public PrintStream getOutput() {
        return output;
    }

    public void setOutput(PrintStream output) {
        this.output = output;
    }

    public long getDurationFactor() {
        return durationFactor;
    }

    public void setDurationFactor(long durationFactor) {
        this.durationFactor = durationFactor;
    }

    public long getRateFactor() {
        return rateFactor;
    }

    public void setRateFactor(long rateFactor) {
        this.rateFactor = rateFactor;
    }


    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

}
