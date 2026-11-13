package ly.count.android.sdk;

import androidx.annotation.Nullable;

/**
 * The optional details of one paid ad impression for {@code attribution().recordAdRevenue(amount, details)}.
 * Each one that is set (not null or empty) is recorded as a segment of the {@code [CLY]_ad_revenue} event.
 */
public class AdRevenueDetails {
    @Nullable String network;
    @Nullable String placement;
    @Nullable String format;
    @Nullable String currency;
    @Nullable String precision;

    /**
     * @param network the ad network that served the impression, for example "admob" or "applovin"
     * @return the same object for chaining
     */
    public AdRevenueDetails setNetwork(@Nullable String network) {
        this.network = network;
        return this;
    }

    /**
     * @param placement the app's placement or ad unit, for example "home_banner"
     * @return the same object for chaining
     */
    public AdRevenueDetails setPlacement(@Nullable String placement) {
        this.placement = placement;
        return this;
    }

    /**
     * @param format the ad format, for example "banner", "interstitial" or "rewarded"
     * @return the same object for chaining
     */
    public AdRevenueDetails setFormat(@Nullable String format) {
        this.format = format;
        return this;
    }

    /**
     * @param currency the currency the amount is in, kept for reference only; the SDK converts nothing
     * @return the same object for chaining
     */
    public AdRevenueDetails setCurrency(@Nullable String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * @param precision how exact the amount is as the network says, for example "publisher_defined", "exact" or "estimated"
     * @return the same object for chaining
     */
    public AdRevenueDetails setPrecision(@Nullable String precision) {
        this.precision = precision;
        return this;
    }
}
