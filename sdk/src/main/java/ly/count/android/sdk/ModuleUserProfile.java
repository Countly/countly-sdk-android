package ly.count.android.sdk;

public class ModuleUserProfile extends ModuleBase{

    UserProfile userProfileInterface = null;

    ModuleUserProfile(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleUserProfile] Initialising");

        userProfileInterface = new UserProfile();
    }

    @Override
    void halt() {

    }

    public class UserProfile {

    }
}
