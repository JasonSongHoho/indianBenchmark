package uyun.indian.tester.service.api;

import java.util.List;

/**
 * Created by JasonSong on 2017/12/21.
 */
public interface QueryService {
    void query(Long duration, Long last, Boolean isGroupBy);

    void queryGroup(List<Long> last);
}
