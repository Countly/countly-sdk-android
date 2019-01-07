package ly.count.sdk.internal;

/**
 * Interface for {@link Module} calls uncoupling from Android SDK.
 * Contract:
 * <ul>
 *     <li>Ctx cannot be saved in a module, it expires as soon as method returns</li>
 * </ul>
 */

public interface Ctx {
    Object getContext();
    InternalConfig getConfig();
    SDKInterface getSDK();
    boolean isExpired();
}
