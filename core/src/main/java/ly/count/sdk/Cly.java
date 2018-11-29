package ly.count.sdk;

import ly.count.sdk.internal.Ctx;
import ly.count.sdk.internal.SDK;
import ly.count.sdk.internal.UserImpl;

public abstract class Cly {

    protected SDK sdk;
    protected UserImpl user;

    protected static void init(Ctx ctx) {
        if (instance != null) {
            //...
        }
        instance = new Cly() {
        };

        instance.sdk = ctx.getSDK();
        instance.user = ctx.getSDK().user(ctx);
        //user = ...

        instance.user.edit().setCountry("Jamaica").commit();
    }

    public static User user() {
        // if instance == null
        return instance.user;
    }
}
