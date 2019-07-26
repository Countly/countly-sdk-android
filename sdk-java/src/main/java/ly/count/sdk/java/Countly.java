package ly.count.sdk.java;

import java.io.File;

import ly.count.sdk.Cly;
import ly.count.sdk.Crash;
import ly.count.sdk.CrashProcessor;
import ly.count.sdk.Event;
import ly.count.sdk.Session;
import ly.count.sdk.Usage;
import ly.count.sdk.User;
import ly.count.sdk.UserEditor;
import ly.count.sdk.java.internal.CtxImpl;
import ly.count.sdk.java.internal.SDK;

/**
 * Main Countly SDK API class.
 * <ul>
 *     <li>Initialize Countly SDK using {@code #init(Application, Config)}.</li>
 *     <li>Stop Countly SDK with {@link #stop(boolean)} if needed.</li>
 *     <li>Call {@link #onActivityCreated(Activity, Bundle)}, {@link #onActivityStarted(Activity)} {@link #onActivityStopped(Activity)} if targeting API levels < 14.</li>
 *     <li>Use {@link #session(Context)} to get a {@link Session} instance.</li>
 *     <li>Use {@link #login(Context, String)} & {@link #logout(Context)} when user logs in & logs out.</li>
 * </ul>
 */

public class Countly extends CountlyLifecycle {

    protected static Countly cly;
    protected SDK sdk;

    protected Countly(SDK sdk, CtxImpl ctx) {
        super();
        cly = this;
        super.sdkInterface = this.sdk = sdk;
        this.ctx = ctx;
    }

    private static CtxImpl ctx(File directory) {
        return new CtxImpl(cly.sdk, cly.sdk.config(), directory);
    }

    private static CtxImpl ctx(File directory, String view) {
        return new CtxImpl(cly.sdk, cly.sdk.config(), directory, view);
    }

    /**
     * Returns active {@link Session} if any or creates new {@link Session} instance.
     *
     * NOTE: {@link Session} instances can expire, for example when {@link Config.DID} changes.
     * {@link Session} also holds application context.
     * So either do not store {@link Session} instances in any static variables and use this method or {@link #getSession()} every time you need it,
     * or check {@link Session#isActive()} before using it.
     *
     * @return active {@link Session} instance
     */
    public static Session session(){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        }
        return Cly.session(cly.ctx);
    }

    /**
     * Returns active {@link Session} if any or {@code null} otherwise.
     *
     * NOTE: {@link Session} instances can expire, for example when {@link Config.DID} changes.
     * {@link Session} also holds application context.
     * So either do not store {@link Session} instances in any static variables and use this method or {@link #session()} every time you need it,
     * or check {@link Session#isActive()} before using it.
     *
     * @return active {@link Session} instance if there is one, {@code null} otherwise
     */
    public static Session getSession(){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        }
        return Cly.getSession();
    }

    /**
     * Alternative to {@link #getSession()} & {@link #session()} method for accessing Countly SDK API.
     *
     * @return {@link Usage} instance
     */
    public static Usage api() {
        return cly;
    }

    @Override
    public Usage login(String id) {
        sdk.login(ctx, id);
        return this;
    }

    @Override
    public Usage logout() {
        sdk.logout(ctx);
        return this;
    }

    @Override
    public Usage resetDeviceId(String id) {
        sdk.resetDeviceId(ctx, id);
        return this;
    }
}
