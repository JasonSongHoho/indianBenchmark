package uyun.indian.tester;

/**
 * Created by JasonSong on 2017/12/27.
 */
public enum RequestType {
    PUSH {
        @Override
        public String toString() {
            return "kairosdb.datastore.write_size";
        }
    },
    QUERY {
        @Override
        public String toString() {
            return "kairosdb.http.query_time";
        }
    },
    QUERYLAST {
        @Override
        public String toString() {
            return "kairosdb.datastore.write_size";
        }
    };
}
