package ly.count.android.sdk;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class UtilsTime {

    public static class Instant {
        public final long timestampMs;
        public final int hour;
        public final int dow; //0-Sunday, 1-Monday, 2-Tuesday, 3-Wednesday, 4-Thursday, 5-Friday, 6-Saturday

        protected Instant(long timestampInMillis, int hour, int dow) {
            this.timestampMs = timestampInMillis;
            this.hour = hour;
            this.dow = dow;
        }

        public static Instant get(long timestampInMillis) {
            if (timestampInMillis < 0L) {
                throw new IllegalArgumentException("timestampInMillis must be greater than or equal to zero");
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestampInMillis);
            final int hour = calendar.get(Calendar.HOUR_OF_DAY);
            // Calendar days are 1-based, Countly days are 0-based
            final int dow = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            return new Instant(timestampInMillis, hour, dow);
        }
    }

    /**
     * Get's a instant for the current moment
     *
     * @return
     */
    public static synchronized Instant getCurrentInstant() {
        long timestamp = currentTimestampMs();
        return Instant.get(timestamp);
    }

    /**
     * Get current timestamp in milliseconds
     *
     * @return
     */
    public static synchronized long currentTimestampMs() {
        return timeGenerator.uniqueTimestamp();
    }

    /**
     * Utility method to return a current timestamp in seconds.
     */
    public static int currentTimestampSeconds() {
        return ((int) (System.currentTimeMillis() / 1000L));
    }

    private static class TimeUniquesEnsurer {
        final List<Long> lastTsMs = new ArrayList<>(10);
        final long addition = 0;

        long currentTimeMillis() {
            return System.currentTimeMillis() + addition;
        }

        synchronized long uniqueTimestamp() {
            long ms = currentTimeMillis();

            // change time back case
            if (lastTsMs.size() > 2) {
                long min = Collections.min(lastTsMs);
                if (ms < min) {
                    lastTsMs.clear();
                    lastTsMs.add(ms);
                    return ms;
                }
            }
            // usual case
            while (lastTsMs.contains(ms)) {
                ms += 1;
            }
            while (lastTsMs.size() >= 10) {
                lastTsMs.remove(0);
            }
            lastTsMs.add(ms);
            return ms;
        }
    }

    private static final TimeUniquesEnsurer timeGenerator = new TimeUniquesEnsurer();
}
