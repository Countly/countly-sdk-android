package ly.count.sdk.internal;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class encapsulating most of device-specific logic: metrics, info, etc.
 */

public class DeviceCore {
    public static DeviceCore dev = new DeviceCore();

    protected DeviceCore() {
        dev = this;
    }

    protected static final Log.Module L = Log.module("Device");

    /**
     * One second in nanoseconds
     */
    protected static final Double NS_IN_SECOND = 1000000000.0d;
    protected static final Double NS_IN_MS = 1000000.0d;
    protected static final Double MS_IN_SECOND = 1000d;
    protected static final Long BYTES_IN_MB = 1024L * 1024;

    /**
     * General interface for time generators.
     */
    public interface TimeGenerator {
        long timestamp();
    }

    /**
     * Always increasing timer.
     */
    static class UniformTimeGenerator implements TimeGenerator{
        private Long last;

        @Override
        public synchronized long timestamp() {
            long ms = System.currentTimeMillis();
            if (last == null) {
                last = ms;
            } else if (last >= ms) {
                last = last + 1;
                return last;
            } else {
                last = ms;
            }
            return ms;
        }
    }

    /**
     * Unique timer, keeps last 10 returned values in memory.
     */
    static class UniqueTimeGenerator implements TimeGenerator {
        List<Long> lastTsMs = new ArrayList<>(10);
        long addition = 0;

        long currentTimeMillis() {
            return System.currentTimeMillis() + addition;
        }

        public synchronized long timestamp() {
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

    protected TimeGenerator uniqueTimer = new UniqueTimeGenerator();
    protected TimeGenerator uniformTimer = new UniformTimeGenerator();

    /**
     * Get operation system name
     *
     * @return the display name of the current operating system.
     */
    public String getOS() {
        return System.getProperty("os.name");
    }

    /**
     * Get Android version
     *
     * @return current operating system version as a displayable string.
     */
    public String getOSVersion() {
        return System.getProperty("os.version", "n/a");
//                + " / " + System.getProperty("os.arch", "n/a");
    }

    /**
     * Get device timezone offset in seconds
     *
     * @return timezone offset in seconds
     */
    public int getTimezoneOffset() {
        return TimeZone.getDefault().getOffset(new Date().getTime()) / 60000;
    }

    /**
     * Get current locale
     *
     * @return current locale (ex. "en_US").
     */
    public String getLocale() {
        final Locale locale = Locale.getDefault();
        return locale.getLanguage() + "_" + locale.getCountry();
    }

    /**
     * Build metrics {@link Params} object as required by Countly server
     *
     * @param ctx Ctx in which to request metrics
     */
    public Params buildMetrics(final Ctx ctx) {
        Params params = new Params();
        params.obj("metrics")
                .put("_os", getOS())
                .put("_os_version", getOSVersion())
                .put("_locale", getLocale())
                .put("_store", ctx.getConfig().getApplicationName())
                .put("_app_version", ctx.getConfig().getApplicationVersion())
            .add();

        return params;
    }

    /**
     * Wraps {@link System#currentTimeMillis()} to always return different value, even within
     * same millisecond and even when time changes. Works in a limited window of 10 timestamps for now.
     *
     * @return unique time in ms
     */
    public synchronized long uniqueTimestamp() {
        return uniqueTimer.timestamp();
    }

    /**
     * Wraps {@link System#currentTimeMillis()} to return always rising values.
     * Resolves issue with device time updates via NTP or manually where time must go up.
     *
     * @return uniform time in ms
     */
    public synchronized long uniformTimestamp() {
        return uniformTimer.timestamp();
    }

    /**
     * Get current day of week
     *
     * @return day of week value, Sunday = 0, Saturday = 6
     */
    public int currentDayOfWeek() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.SUNDAY:
                return 0;
            case Calendar.MONDAY:
                return 1;
            case Calendar.TUESDAY:
                return 2;
            case Calendar.WEDNESDAY:
                return 3;
            case Calendar.THURSDAY:
                return 4;
            case Calendar.FRIDAY:
                return 5;
            case Calendar.SATURDAY:
                return 6;
        }
        return 0;
    }

    /**
     * Get current hour of day
     *
     * @return current hour of day
     */
    public int currentHour() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    /**
     * Convert time in nanoseconds to milliseconds
     *
     * @param ns time in nanoseconds
     * @return ns in milliseconds
     */
    public long nsToMs(long ns) {
        return Math.round(ns / NS_IN_MS);
    }

    /**
     * Convert time in nanoseconds to seconds
     *
     * @param ns time in nanoseconds
     * @return ns in seconds
     */
    public long nsToSec(long ns) {
        return Math.round(ns / NS_IN_SECOND);
    }

    /**
     * Convert time in seconds to nanoseconds
     *
     * @param sec time in seconds
     * @return sec in nanoseconds
     */
    public long secToNs(long sec) {
        return Math.round(sec * NS_IN_SECOND);
    }

    /**
     * Convert time in seconds to milliseconds
     *
     * @param sec time in seconds
     * @return sec in nanoseconds
     */
    public long secToMs(long sec) {
        return Math.round(sec * MS_IN_SECOND);
    }

    /**
     * Get total RAM in Mb
     *
     * @return total RAM in Mb or null if cannot determine
     */
    public Long getRAMTotal() {
        RandomAccessFile reader = null;
        try {
            reader = new RandomAccessFile("/proc/meminfo", "r");
            String load = reader.readLine();

            // Get the Number value from the string
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(load);
            String value = "";
            while (m.find()) {
                value = m.group(1);
            }
            return Long.parseLong(value) / 1024;
        } catch (NumberFormatException e){
            L.e("Cannot parse meminfo", e);
            return null;
        } catch (IOException e) {
            L.e("Cannot read meminfo", e);
            return null;
        }
        finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Get current device RAM amount.
     *
     * @return currently available RAM in Mb or {@code null} if couldn't determine
     */
    public Long getRAMAvailable() {
        Long total = Runtime.getRuntime().totalMemory();
        Long availMem = Runtime.getRuntime().freeMemory();
        return (total - availMem) / BYTES_IN_MB;
    }

    /**
     * Get current device disk space.
     *
     * @return currently available disk space in Mb
     */
    public Long getDiskAvailable() {
        long total = 0, free = 0;
        for (File f : File.listRoots()) {
            total += f.getTotalSpace();
            free += f.getUsableSpace();
        }
        return (total - free) / BYTES_IN_MB;
    }

    /**
     * Get total device disk space.
     *
     * @return total device disk space in Mb
     */
    public Long getDiskTotal() {
        long total = 0;
        for (File f : File.listRoots()) {
            total += f.getTotalSpace();
        }
        return total / BYTES_IN_MB;
    }

    public boolean isDebuggerConnected() {
        try {
            return ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-Xdebug") || ManagementFactory.getRuntimeMXBean().getInputArguments().contains("jdwp=");
        } catch (Throwable e) {
            Log.i("Cannot determine whether debugger is connected, skipping", e);
            return false;
        }
    }

}
