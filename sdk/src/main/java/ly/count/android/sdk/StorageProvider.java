package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

interface StorageProvider {
    String[] getRequests();

    String[] getEvents();

    List<Event> getEventList();

    @NonNull String getRequestQueueRaw();

    void addRequest(final String requestStr, final boolean writeInSync);

    void removeRequest(final String requestStr);

    void replaceRequests(final String[] newConns);

    void replaceRequestList(final List<String> newConns);

    void removeEvents(final List<Event> eventsToRemove);

    int getEventQueueSize();

    int getMaxRequestQueueSize();

    String getEventsForRequestAndEmptyEventQueue();

    @Nullable String getDeviceID();

    @Nullable String getDeviceIDType();

    void setDeviceID(String id);

    void setDeviceIDType(String type);

    void setStarRatingPreferences(String preferences);//not integrated

    String getStarRatingPreferences();//not integrated

    void setCachedAdvertisingId(String advertisingId);//not integrated

    String getCachedAdvertisingId();//not integrated

    void setRemoteConfigValues(String values);//not integrated

    String getRemoteConfigValues();//not integrated

    void esWriteCacheToStorage(@Nullable ExplicitStorageCallback callback);//required for explicit storage

    void setServerConfig(String config);

    String getServerConfig();

    //fields for data migration
    int getDataSchemaVersion();

    void setDataSchemaVersion(int version);

    boolean anythingSetInStorage();

    /**
     * Stores when each content trigger last started a round, so that its gap holds after a restart
     *
     * @param gaps a JSON object of gap key to milliseconds
     */
    void setContentTriggerGaps(@NonNull String gaps);

    /**
     * @return the times content triggers last started a round, a JSON object of gap key to milliseconds, "" when none
     */
    @NonNull String getContentTriggerGaps();

    @NonNull String getHealthCheckCounterState();

    void setHealthCheckCounterState(String counterState);

    /**
     * Adds one automatically detected crash to the count the next start reports in the health check, without the health
     * check counters, because it runs while the crash is reported. Call it before the crash report is queued: the
     * synchronous save of the report writes the count to storage too.
     */
    void incrementAutomaticCrashCount();

    /**
     * Returns the automatically detected crashes counted since the last call, and sets the stored count back to 0.
     *
     * @return the stored count of automatically detected crashes
     */
    int takeAutomaticCrashCount();
}
