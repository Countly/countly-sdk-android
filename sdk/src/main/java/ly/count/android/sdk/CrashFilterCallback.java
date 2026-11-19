package ly.count.android.sdk;

/**
 * callback for filtering crashes
 *
 * @deprecated No SDK call takes this callback any more. Use {@link GlobalCrashFilterCallback} through
 * {@link ConfigCrashes#setGlobalCrashFilterCallback(GlobalCrashFilterCallback)}.
 */
@Deprecated
public interface CrashFilterCallback {
    /**
     * Callback for filtering the crash
     *
     * @param crash
     * @return return true if the crash should not be sent to the server
     */
    boolean filterCrash(String crash);
}
