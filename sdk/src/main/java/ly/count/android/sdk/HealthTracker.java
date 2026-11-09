package ly.count.android.sdk;

import androidx.annotation.NonNull;

interface HealthTracker {
    void logWarning();

    void logError();

    void logFailedNetworkRequest(int statusCode, String errorResponse);

    void logBackoffRequest();

    void logConsecutiveBackoffRequest();

    /**
     * Counts one call of a method for the health check "fu" map, under its feature area ("sessions", "events", "views",
     * "crashes") and method name. A call the SDK made by itself carries the ":a" suffix, for example "begin:a". Nothing is
     * counted while consent is required and the area's feature has no consent, or while an automatic crash is reported.
     *
     * @param area feature area of the method
     * @param method method name, with its modifiers
     */
    void recordUsage(@NonNull String area, @NonNull String method);

    /**
     * Counts one occurrence of a log code for the health check "lc" map, a level letter and three digits such as "w740".
     *
     * @param code the log code
     */
    void recordLogCode(@NonNull String code);

    /**
     * Counts several occurrences of a log code at once, for a condition that several items met together, such as requests
     * dropped from the queue in one go.
     *
     * @param code the log code
     * @param count how many times the condition occurred
     */
    void recordLogCode(@NonNull String code, int count);

    /**
     * Counts one occurrence of a log code that a public method of a feature area records. Like recordUsage, nothing is
     * counted while consent is required and the area's feature has no consent.
     *
     * @param code the log code
     * @param area feature area of the method that recorded it
     */
    void recordLogCode(@NonNull String code, @NonNull String area);

    /**
     * Makes every counting call ignore its call until crash reporting starts again, so reporting an automatically detected
     * crash never calls into the health check counters.
     */
    void stopRecordingForCrash();

    void clearAndSave();

    void saveState();
}
