package ly.count.android.sdk;

import java.util.Collection;
import java.util.List;
import java.util.Map;

interface StorageProvider {
    public String[] getRequests();

    public String[] getEvents();

    public List<Event> getEventList();

    public void addRequest(final String requestStr);

    public void removeRequest(final String requestStr);

    public void replaceRequests(final String[] newConns);

    public void replaceRequestList(final List<String> newConns);

    public void addEvent(final String key, final Map<String, String> segmentation, final Map<String, Integer> segmentationInt, final Map<String, Double> segmentationDouble, final Map<String, Boolean> segmentationBoolean,
        final long timestamp, final int hour, final int dow, final int count, final double sum, final double dur);

    public void removeEvents(final Collection<Event> eventsToRemove);

    public String getDeviceID();

    public String getDeviceIDType();

    public void setDeviceID(String id);

    public void setDeviceIDType(String type);
}
