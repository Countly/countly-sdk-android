package ly.count.android.sdk;

import androidx.annotation.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;

interface StorageProvider {
    String[] getRequests();

    String[] getEvents();

    List<Event> getEventList();

    @NonNull String getRequestQueueRaw();

    void addRequest(final String requestStr);

    void removeRequest(final String requestStr);

    void replaceRequests(final String[] newConns);

    void replaceRequestList(final List<String> newConns);

    void removeEvents(final Collection<Event> eventsToRemove);

    int getEventQueueSize();

    String getEventsForRequestAndEmptyEventQueue();

    String getDeviceID();

    String getDeviceIDType();

    void setDeviceID(String id);

    void setDeviceIDType(String type);

    void setStarRatingPreferences(String preferences);//not integrated

    String getStarRatingPreferences();//not integrated

    void setCachedAdvertisingId(String advertisingId);//not integrated

    String getCachedAdvertisingId();//not integrated

    void setRemoteConfigValues(String values);//not integrated

    String getRemoteConfigValues();//not integrated

    //fields for data migration
    int getDataSchemaVersion();

    void setDataSchemaVersion(int version);

    boolean anythingSetInStorage();
}
