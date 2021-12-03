package ly.count.android.sdk;

import androidx.annotation.NonNull;

interface ConsentProvider {
    boolean getConsent(@NonNull String featureName);

    boolean anyConsentGiven();
}
