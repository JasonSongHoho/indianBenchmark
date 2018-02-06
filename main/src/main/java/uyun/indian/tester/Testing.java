package uyun.indian.tester;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uyun.indian.tester.report.Reporter;
import uyun.indian.tester.service.api.PushService;
import uyun.indian.tester.service.api.QueryLastService;
import uyun.indian.tester.service.api.QueryService;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by JasonSong on 2018/1/10.
 */
@Component
public class Testing {
    private static final Long ONE_MINUTE = 60 * 1000L;
    @Autowired
    private PushService pushService;
    @Autowired
    private QueryService queryService;
    @Autowired
    private QueryLastService queryLastService;
    @Autowired
    Reporter reporter;

    void push() {
        int maxRes = pushService.push();
        pushService.pushData(maxRes);
    }

    void queryLast() {
        queryLastService.queryLast();
        mySleep(ONE_MINUTE);
    }

    void query() {
        List<Long> last = new ArrayList<>();
        last.add(30 * ONE_MINUTE);
        last.add(24 * 60 * ONE_MINUTE);
        last.add(7 * 24 * 60 * ONE_MINUTE);
        last.add(30 * 24 * 60 * ONE_MINUTE);
        queryService.queryGroup(last);
    }

    void report() {
        reporter.report();
    }

    static void mySleep(long sleep) {
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
