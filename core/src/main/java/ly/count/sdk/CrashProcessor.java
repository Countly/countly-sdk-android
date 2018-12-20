package ly.count.sdk;

import java.util.Map;

/**
 * This interface allows sending custom data with a crash if it occurs.
 * When {@link ConfigCore#crashProcessorClass} is set with your own implementation of this interface,
 * Countly SDK instantiates an instance of this class and calls {@link #process(Crash)} whenever
 * application crashes or you send custom crash report using {@link Session#addCrashReport(Throwable, boolean, String, Map, String...)}}.
 *
 * @see Crash#setLogs(String[]) to add custom logs sent to the server along with a crash
 * @see Crash#setSegments(Map) to add custom crash segments sent to the server along with a crash
 */

public interface CrashProcessor {
    /**
     * When {@link ConfigCore#crashProcessorClass} is set with your own implementation of this interface,
     * Countly calls this method to allow adding custom data to the crash.
     *
     * @see Crash#setLogs(String[]) to add custom logs sent to the server along with a crash
     * @see Crash#setSegments(Map) to add custom crash segments sent to the server along with a crash
     *
     * @param crash {@link Crash} instance to process
     * @return {@code null} if crash shouldn't be sent, {@code crash} parameter value otherwise
     */
    Crash process(Crash crash);
}
