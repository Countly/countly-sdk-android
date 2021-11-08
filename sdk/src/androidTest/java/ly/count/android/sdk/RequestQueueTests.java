package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class RequestQueueTests {
    Countly mCountly;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);

        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @Test
    public void testRequestQueueRemoveWithoutAppKey() {
        String[] sampleRequests = new String[] {
            "app_key=12345qer&timestamp=1604252139458&hour=19&dow=0&tz=120&sdk_version=19.10.0-rc1&sdk_name=java-native-android&begin_session=1&metrics=%7B%22_device%22%3A%22Pixel%22%2C%22_os%22%3A%22Android%22%2C%22_os_version%22%3A%2210%22%2C%22_carrier%22%3A%22LMT%22%2C%22_resolution%22%3A%221080x1794%22%2C%22_density%22%3A%22XXHDPI%22%2C%22_locale%22%3A%22en_US%22%2C%22_app_version%22%3A%221.0%22%2C%22_device_type%22%3A%22mobile%22%7D&location=-23.8043604%2C-46.6718331&city=B%C3%B6ston&country_code=us&ip=10.2.33.12",
            "app_key=qqq45qer&timestamp=1604252139686&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&events=%5B%7B%22key%22%3A%22%5BCLY%5D_view%22%2C%22count%22%3A1%2C%22timestamp%22%3A1604252139560%2C%22hour%22%3A19%2C%22dow%22%3A0%2C%22segmentation%22%3A%7B%22Five%22%3A%22Six%22%2C%22segment%22%3A%22Android%22%2C%22name%22%3A%22MainActivity%22%2C%22start%22%3A%221%22%2C%22visit%22%3A%221%22%2C%22One%22%3A2%2C%22Three%22%3A4.44%7D%2C%22sum%22%3A0%7D%5D",
            "app_key=12345qer&timestamp=1604252139761&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&count=1&apm=%7B%22type%22%3A%22device%22%2C%22name%22%3A%22app_start%22%2C+%22apm_metrics%22%3A%7B%22duration%22%3A+28833%7D%2C+%22stz%22%3A+1604252110922%2C+%22etz%22%3A+1604252139755%7D",
            "app_key=12345qer&timestamp=1604252161932&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&consent=%7B%22push%22%3Atrue%2C%22sessions%22%3Atrue%2C%22location%22%3Atrue%2C%22attribution%22%3Atrue%2C%22crashes%22%3Atrue%2C%22events%22%3Atrue%2C%22star-rating%22%3Atrue%2C%22users%22%3Atrue%2C%22views%22%3Atrue%2C%22apm%22%3Atrue%2C%22location%22%3Atrue%2C%22surveys%22%3Atrue%7D",
            //copied and tweaked first requests
            "timestamp=1604252139761&hour=19&app_key=12345qer&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&count=1&apm=%7B%22type%22%3A%22device%22%2C%22name%22%3A%22app_start%22%2C+%22apm_metrics%22%3A%7B%22duration%22%3A+28833%7D%2C+%22stz%22%3A+1604252110922%2C+%22etz%22%3A+1604252139755%7D",
            "timestamp=1604252161932&hour=19&app_key=qqq45qer&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&consent=%7B%22push%22%3Atrue%2C%22sessions%22%3Atrue%2C%22location%22%3Atrue%2C%22attribution%22%3Atrue%2C%22crashes%22%3Atrue%2C%22events%22%3Atrue%2C%22star-rating%22%3Atrue%2C%22users%22%3Atrue%2C%22views%22%3Atrue%2C%22apm%22%3Atrue%2C%22location%22%3Atrue%2C%22surveys%22%3Atrue%7D",
        };

        Assert.assertEquals(6, sampleRequests.length);

        List<String> resRequests = mCountly.moduleRequestQueue.requestQueueRemoveWithoutAppKey(sampleRequests, "12345qer");

        Assert.assertEquals(resRequests.size(), sampleRequests.length - 2);

        List<String> wantedRes = new ArrayList<>();
        wantedRes.add(sampleRequests[0]);
        wantedRes.add(sampleRequests[2]);
        wantedRes.add(sampleRequests[3]);
        wantedRes.add(sampleRequests[4]);

        for (int a = 0; a < resRequests.size(); a++) {
            Assert.assertEquals(wantedRes.get(a), resRequests.get(a));
        }
    }

    @Test
    public void testRequestQueueRemoveWithoutAppKey_empty() {
        List<String> resRequests = mCountly.moduleRequestQueue.requestQueueRemoveWithoutAppKey(null, "12345qer");
        List<String> resRequests2 = mCountly.moduleRequestQueue.requestQueueRemoveWithoutAppKey(new String[] {}, "12345qer");
        List<String> resRequests3 = mCountly.moduleRequestQueue.requestQueueRemoveWithoutAppKey(new String[] {}, null);
        List<String> resRequests4 = mCountly.moduleRequestQueue.requestQueueRemoveWithoutAppKey(new String[] {}, "");
        Assert.assertNotNull(resRequests);
        Assert.assertNotNull(resRequests2);
        Assert.assertNotNull(resRequests3);
        Assert.assertNotNull(resRequests4);
    }

    @Test
    public void testRequestQueueReplaceWithAppKey_empty() {
        List<String> resRequests = mCountly.moduleRequestQueue.requestQueueReplaceWithAppKey(null, "12345qer");
        List<String> resRequests2 = mCountly.moduleRequestQueue.requestQueueReplaceWithAppKey(new String[] {}, "12345qer");
        List<String> resRequests3 = mCountly.moduleRequestQueue.requestQueueReplaceWithAppKey(new String[] {}, null);
        List<String> resRequests4 = mCountly.moduleRequestQueue.requestQueueReplaceWithAppKey(new String[] {}, "");
        Assert.assertNotNull(resRequests);
        Assert.assertNotNull(resRequests2);
        Assert.assertNotNull(resRequests3);
        Assert.assertNotNull(resRequests4);
    }

    @Test
    public void testRequestQueueReplaceWithAppKey() {
        String[] sampleRequests = new String[] {
            "app_key=12345qer&timestamp=1604252139458&hour=19&dow=0&tz=120&sdk_version=19.10.0-rc1&sdk_name=java-native-android&begin_session=1&metrics=%7B%22_device%22%3A%22Pixel%22%2C%22_os%22%3A%22Android%22%2C%22_os_version%22%3A%2210%22%2C%22_carrier%22%3A%22LMT%22%2C%22_resolution%22%3A%221080x1794%22%2C%22_density%22%3A%22XXHDPI%22%2C%22_locale%22%3A%22en_US%22%2C%22_app_version%22%3A%221.0%22%2C%22_device_type%22%3A%22mobile%22%7D&location=-23.8043604%2C-46.6718331&city=B%C3%B6ston&country_code=us&ip=10.2.33.12",
            "app_key=qqq45qer&timestamp=1604252139686&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&events=%5B%7B%22key%22%3A%22%5BCLY%5D_view%22%2C%22count%22%3A1%2C%22timestamp%22%3A1604252139560%2C%22hour%22%3A19%2C%22dow%22%3A0%2C%22segmentation%22%3A%7B%22Five%22%3A%22Six%22%2C%22segment%22%3A%22Android%22%2C%22name%22%3A%22MainActivity%22%2C%22start%22%3A%221%22%2C%22visit%22%3A%221%22%2C%22One%22%3A2%2C%22Three%22%3A4.44%7D%2C%22sum%22%3A0%7D%5D",
            "app_key=12345qer&timestamp=1604252139761&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&count=1&apm=%7B%22type%22%3A%22device%22%2C%22name%22%3A%22app_start%22%2C+%22apm_metrics%22%3A%7B%22duration%22%3A+28833%7D%2C+%22stz%22%3A+1604252110922%2C+%22etz%22%3A+1604252139755%7D",
            "app_key=12345qer&timestamp=1604252161932&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&consent=%7B%22push%22%3Atrue%2C%22sessions%22%3Atrue%2C%22location%22%3Atrue%2C%22attribution%22%3Atrue%2C%22crashes%22%3Atrue%2C%22events%22%3Atrue%2C%22star-rating%22%3Atrue%2C%22users%22%3Atrue%2C%22views%22%3Atrue%2C%22apm%22%3Atrue%2C%22location%22%3Atrue%2C%22surveys%22%3Atrue%7D",
            //copied and tweaked first requests
            "timestamp=1604252139761&hour=19&app_key=12345qer&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&count=1&apm=%7B%22type%22%3A%22device%22%2C%22name%22%3A%22app_start%22%2C+%22apm_metrics%22%3A%7B%22duration%22%3A+28833%7D%2C+%22stz%22%3A+1604252110922%2C+%22etz%22%3A+1604252139755%7D",
            "timestamp=1604252161932&hour=19&app_key=qqq45qer&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&consent=%7B%22push%22%3Atrue%2C%22sessions%22%3Atrue%2C%22location%22%3Atrue%2C%22attribution%22%3Atrue%2C%22crashes%22%3Atrue%2C%22events%22%3Atrue%2C%22star-rating%22%3Atrue%2C%22users%22%3Atrue%2C%22views%22%3Atrue%2C%22apm%22%3Atrue%2C%22location%22%3Atrue%2C%22surveys%22%3Atrue%7D",
        };

        Assert.assertEquals(6, sampleRequests.length);

        List<String> resRequests = mCountly.moduleRequestQueue.requestQueueReplaceWithAppKey(sampleRequests, "12345qer");

        Assert.assertEquals(sampleRequests.length, resRequests.size());

        List<String> wantedRes = new ArrayList<>();
        wantedRes.add(sampleRequests[0]);
        wantedRes.add(sampleRequests[1].replace("app_key=qqq45qer", "app_key=12345qer"));
        wantedRes.add(sampleRequests[2]);
        wantedRes.add(sampleRequests[3]);
        wantedRes.add(sampleRequests[4]);
        wantedRes.add(sampleRequests[5].replace("app_key=qqq45qer", "app_key=12345qer"));

        for (int a = 0; a < resRequests.size(); a++) {
            Assert.assertEquals(wantedRes.get(a), resRequests.get(a));
        }
    }

    @Test
    public void testRequestQueueRemoveWithoutAppKey_nullEntries() {
        String[] sampleRequests = new String[] {
            null,
            "",
            "app_key=12345qer&timestamp=1604252139458&hour=19&dow=0&tz=120&sdk_version=19.10.0-rc1&sdk_name=java-native-android&begin_session=1&metrics=%7B%22_device%22%3A%22Pixel%22%2C%22_os%22%3A%22Android%22%2C%22_os_version%22%3A%2210%22%2C%22_carrier%22%3A%22LMT%22%2C%22_resolution%22%3A%221080x1794%22%2C%22_density%22%3A%22XXHDPI%22%2C%22_locale%22%3A%22en_US%22%2C%22_app_version%22%3A%221.0%22%2C%22_device_type%22%3A%22mobile%22%7D&location=-23.8043604%2C-46.6718331&city=B%C3%B6ston&country_code=us&ip=10.2.33.12",
            "app_key=qqq45qer&timestamp=1604252139686&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&events=%5B%7B%22key%22%3A%22%5BCLY%5D_view%22%2C%22count%22%3A1%2C%22timestamp%22%3A1604252139560%2C%22hour%22%3A19%2C%22dow%22%3A0%2C%22segmentation%22%3A%7B%22Five%22%3A%22Six%22%2C%22segment%22%3A%22Android%22%2C%22name%22%3A%22MainActivity%22%2C%22start%22%3A%221%22%2C%22visit%22%3A%221%22%2C%22One%22%3A2%2C%22Three%22%3A4.44%7D%2C%22sum%22%3A0%7D%5D",
        };

        Assert.assertEquals(4, sampleRequests.length);

        List<String> resRequests = mCountly.moduleRequestQueue.requestQueueRemoveWithoutAppKey(sampleRequests, "12345qer");

        Assert.assertEquals(1, resRequests.size());

        List<String> wantedRes = new ArrayList<>();
        wantedRes.add(sampleRequests[2]);

        for (int a = 0; a < resRequests.size(); a++) {
            Assert.assertEquals(wantedRes.get(a), resRequests.get(a));
        }
    }

    @Test
    public void testRequestQueueReplaceWithAppKey_nullEntries() {
        String[] sampleRequests = new String[] {
            null,
            "",
            "app_key=12345qer&timestamp=1604252139458&hour=19&dow=0&tz=120&sdk_version=19.10.0-rc1&sdk_name=java-native-android&begin_session=1&metrics=%7B%22_device%22%3A%22Pixel%22%2C%22_os%22%3A%22Android%22%2C%22_os_version%22%3A%2210%22%2C%22_carrier%22%3A%22LMT%22%2C%22_resolution%22%3A%221080x1794%22%2C%22_density%22%3A%22XXHDPI%22%2C%22_locale%22%3A%22en_US%22%2C%22_app_version%22%3A%221.0%22%2C%22_device_type%22%3A%22mobile%22%7D&location=-23.8043604%2C-46.6718331&city=B%C3%B6ston&country_code=us&ip=10.2.33.12",
            "app_key=qqq45qer&timestamp=1604252139686&hour=19&dow=0&tz=120&sdk_version=20.10.0-rc1&sdk_name=java-native-android&events=%5B%7B%22key%22%3A%22%5BCLY%5D_view%22%2C%22count%22%3A1%2C%22timestamp%22%3A1604252139560%2C%22hour%22%3A19%2C%22dow%22%3A0%2C%22segmentation%22%3A%7B%22Five%22%3A%22Six%22%2C%22segment%22%3A%22Android%22%2C%22name%22%3A%22MainActivity%22%2C%22start%22%3A%221%22%2C%22visit%22%3A%221%22%2C%22One%22%3A2%2C%22Three%22%3A4.44%7D%2C%22sum%22%3A0%7D%5D",
        };

        Assert.assertEquals(4, sampleRequests.length);

        List<String> resRequests = mCountly.moduleRequestQueue.requestQueueReplaceWithAppKey(sampleRequests, "12345qer");

        Assert.assertEquals(3, resRequests.size());

        List<String> wantedRes = new ArrayList<>();
        wantedRes.add("");
        wantedRes.add(sampleRequests[2]);
        wantedRes.add(sampleRequests[3].replace("app_key=qqq45qer", "app_key=12345qer"));

        for (int a = 0; a < resRequests.size(); a++) {
            Assert.assertEquals(wantedRes.get(a), resRequests.get(a));
        }
    }
}
