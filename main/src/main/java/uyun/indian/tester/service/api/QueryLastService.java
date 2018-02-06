package uyun.indian.tester.service.api;

/**
 * Created by JasonSong on 2018/1/9.
 */
public interface QueryLastService {
    void queryLast();

    void queryLast(String cachedTagKey, String cachedTagValue, Long duration);
}
