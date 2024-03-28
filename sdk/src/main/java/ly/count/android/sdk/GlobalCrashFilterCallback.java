package ly.count.android.sdk;

public interface GlobalCrashFilterCallback {
    /**
     * Callback for filtering the crash
     *
     * @param crash happened
     * @return true if the crash should not be sent to the server
     */
    boolean filterCrash(CrashData crash);
}
