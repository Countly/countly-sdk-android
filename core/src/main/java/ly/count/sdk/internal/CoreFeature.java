package ly.count.sdk.internal;

import ly.count.sdk.Config;

public enum CoreFeature {
    Events(1 << 1),
    Sessions(1 << 2),
    Views(1 << 3),
    CrashReporting(1 << 4),
    Location(1 << 5),
    UserProfiles(1 << 5),

    DeviceId(1 << 20),
    Requests(1 << 21),
    Logs(1 << 22);

    private final int index;

    CoreFeature(int index){ this.index = index; }

    public int getIndex(){ return index; }
}
