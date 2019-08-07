package ly.count.sdk.internal;

public enum CoreFeature {
    Sessions(1 << 1),
    Events(1 << 2),
    Views(1 << 3),
    CrashReporting(1 << 4),
    Location(1 << 5),
    UserProfiles(1 << 6),
    StarRating(1 << 7),

    /*
    THESE ARE ONLY HERE AS DOCUMENTATION
    THEY SHOW WHICH ID'S ARE USED IN ANDROID
    Push(1 << 10),
    Attribution(1 << 11),

    PerformanceMonitoring(1 << 14);
    */
    RemoteConfig(1 << 13),
    TestDummy(1 << 19),//used during testing
    DeviceId(1 << 20),
    Requests(1 << 21),
    Logs(1 << 22);

    private final int index;

    CoreFeature(int index){ this.index = index; }

    public int getIndex(){ return index; }
}
