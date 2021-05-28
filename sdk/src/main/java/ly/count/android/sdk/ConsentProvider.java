package ly.count.android.sdk;

interface ConsentProvider {
    boolean getConsent(String featureName);

    boolean anyConsentGiven();
}
