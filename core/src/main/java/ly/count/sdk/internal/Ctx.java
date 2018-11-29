package ly.count.sdk.internal;

import ly.count.sdk.Config;

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
    SDK getSDK();
    boolean isExpired();
}
