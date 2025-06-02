package ly.count.android.sdk;

interface HealthTracker {
    void logWarning();

    void logError();

    void logFailedNetworkRequest(int statusCode, String errorResponse);

    void logSessionStartedWhileRunning();

    void logSessionEndedWhileNotRunning();

    void logSessionUpdatedWhileNotRunning();

    void logBackoffRequest();

    void logConsecutiveBackoffRequest(int consecutiveBackoffRequest);

    void clearAndSave();

    void saveState();
}
