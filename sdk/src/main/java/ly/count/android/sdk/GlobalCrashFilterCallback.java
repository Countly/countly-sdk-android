package ly.count.android.sdk;

public interface GlobalCrashFilterCallback {
    /**
     * Callback for filtering the crash
     *
     * @param crash happened
     * @return filtered crash, or null if the crash should not be sent to the server
     */
    String filterCrash(String crash);
}
