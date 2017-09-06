package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.User;

import static android.support.test.InstrumentationRegistry.getContext;

@RunWith(AndroidJUnit4.class)
public class UserImplTests {

    @Test
    public void testStorageAllSet() throws Exception {
        UserImpl user = new UserImpl();
        user.name = "name";
        user.username = "username";
        user.email = "email";
        user.org = "org";
        user.phone = "phone";
        user.picture = new byte[]{3, 2, 1};
        user.picturePath = "http://picture.com";
        user.gender = User.Gender.FEMALE;
        user.birthyear = 1900;

        Core.initForBroadcastReceiver(getContext());
        Assert.assertTrue(Storage.push(user));

        User stored = Storage.read(new UserImpl());
        Assert.assertNotNull(stored);

        Assert.assertEquals(user.name, stored.name());
        Assert.assertEquals(user.username, stored.username());
        Assert.assertEquals(user.email, stored.email());
        Assert.assertEquals(user.org, stored.org());
        Assert.assertEquals(user.phone, stored.phone());
        Assert.assertTrue(Arrays.equals(user.picture, stored.picture()));
        Assert.assertEquals(user.picturePath, stored.picturePath());
        Assert.assertEquals(user.gender, stored.gender());
        Assert.assertEquals(user.birthyear, stored.birthyear());

    }

    @Test
    public void testStorageNothingSet() throws Exception {
        UserImpl user = new UserImpl();

        Core.initForBroadcastReceiver(getContext());
        new Log().init(new InternalConfig(TestingUtilityInternal.setupConfig().enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG)));
        Assert.assertTrue(Storage.push(user));

        User stored = Storage.read(new UserImpl());
        Assert.assertNotNull(stored);

        Assert.assertNull(stored.name());
        Assert.assertNull(stored.username());
        Assert.assertNull(stored.email());
        Assert.assertNull(stored.org());
        Assert.assertNull(stored.phone());
        Assert.assertNull(stored.picture());
        Assert.assertNull(stored.picturePath());
        Assert.assertNull(stored.gender());
        Assert.assertNull(stored.birthyear());

    }

    @Test
    public void testStorageSomeSet() throws Exception {
        UserImpl user = new UserImpl();
        user.username = "username";
        user.gender = User.Gender.MALE;
        user.phone = "phone";

        Core.initForBroadcastReceiver(getContext());
        new Log().init(new InternalConfig(TestingUtilityInternal.setupConfig().enableTestMode().setLoggingLevel(Config.LoggingLevel.DEBUG)));
        Assert.assertTrue(Storage.push(user));

        User stored = Storage.read(new UserImpl());
        Assert.assertNotNull(stored);

        Assert.assertNull(stored.name());
        Assert.assertEquals(user.username, stored.username());
        Assert.assertNull(stored.email());
        Assert.assertNull(stored.org());
        Assert.assertEquals(user.phone, stored.phone());
        Assert.assertNull(stored.picture());
        Assert.assertNull(stored.picturePath());
        Assert.assertEquals(user.gender, stored.gender());
        Assert.assertNull(stored.birthyear());

    }
}
