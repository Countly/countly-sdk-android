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

    @NonNull String getHealthCheckCounterState();

    void setHealthCheckCounterState(String counterState);
}
