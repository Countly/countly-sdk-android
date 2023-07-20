package ly.count.android.sdk;

interface HealthTracker {
    void logWarning();

    void logError();

    void logFailedNetworkRequest(int statusCode, String errorResponse);

    void clearAndSave();

    void saveState();
}
