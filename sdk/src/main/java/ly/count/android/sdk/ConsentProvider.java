package ly.count.android.sdk;

import androidx.annotation.NonNull;

interface ConsentProvider {
    boolean getConsent(@NonNull String featureName);

    /**
     * Reads a feature's consent without the log line {@link #getConsent(String)} writes, for callers that run inside
     * a log call.
     *
     * @param featureName the feature to look up
     * @return true when consent is not required or is given for the feature
     */
    boolean getConsentSilently(@NonNull String featureName);

    boolean anyConsentGiven();
}
