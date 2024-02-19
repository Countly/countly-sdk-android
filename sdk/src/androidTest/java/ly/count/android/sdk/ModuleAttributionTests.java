package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleAttributionTests {
    CountlyStore countlyStore;

    String cid_1;
    String cuid_1;
    String daType_Countly;
    String daType_test;
    String daValue_Countly;
    String daValue_Countly_2;
    String daValue_Countly_3;
    String daValue_Countly_4;
    String daValue_Countly_5;
    String daValue_Test;

    Map<String, String> ia_1;
    Map<String, String> ia_2;
    String ia_1_string;
    String ia_2_string;
    String ia_k1;
    String ia_v1;
    String ia_k2;
    String ia_v2;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(InstrumentationRegistry.getInstrumentation().getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        cid_1 = "abc";
        cuid_1 = "123";
        daType_Countly = "countly";
        daType_test = "_special_test";
        daValue_Countly = "{\"cid\":\"" + cid_1 + "\", \"cuid\":\"" + cuid_1 + "\"}";
        daValue_Countly_2 = "{\"cid\":\"\", \"cuid\":\"" + cuid_1 + "\"}";
        daValue_Countly_3 = "{\"cuid\":\"" + cuid_1 + "\"}";
        daValue_Countly_4 = "{\"cid\":\"" + cid_1 + "\", \"cuid\":\"\"}";
        daValue_Countly_5 = "{\"cid\":\"" + cid_1 + "\"}";

        daValue_Test = "{'asd':'vcx', 'rte':'123'}";

        ia_k1 = "adid";
        ia_v1 = "SomeValue";
        ia_k2 = "other";
        ia_v2 = "thing";
        ia_1 = new HashMap<>();
        ia_1.put(ia_k1, ia_v1);
        ia_1_string = "{\"" + ia_k1 + "\":\"" + ia_v1 + "\"}";

        ia_2 = new HashMap<>();
        ia_2.put(ia_k1, ia_v1);
        ia_2.put(ia_k2, ia_v2);
        ia_2_string = "{\"" + ia_k2 + "\":\"" + ia_v2 + "\",\"" + ia_k1 + "\":\"" + ia_v1 + "\"}";
    }

    /**
     * With no attribution values provided during init, nothing should be recorded
     */
    @Test
    public void basicInitNoAttribution() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that init time provided direct attribution is recorded correctly
     */
    @Test
    public void basicInitOnlyDA() {

        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, daType_Countly, daValue_Countly, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(1)).sendDirectAttributionLegacy(cid_1, cuid_1);
    }

    /**
     * Validate that init time provided indirect attribution is recorded correctly
     */
    @Test
    public void basicInitOnlyIA() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, ia_1));

        verify(rqp, times(1)).sendIndirectAttribution(ia_1_string);
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that init time provided indirect attribution is recorded correctly
     * Make sure that useless values are removed
     */
    @Test
    public void basicInitOnlyIABadValues() {
        ia_1.put(null, "asd");
        ia_1.put("", "asd");
        ia_1.put("aa", null);
        ia_1.put("aa1", "");

        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, ia_1));

        verify(rqp, times(1)).sendIndirectAttribution(ia_1_string);
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that init time provided direct and indirect attribution is recorded correctly
     */
    @Test
    public void basicInitDAAndIA() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, daType_Countly, daValue_Countly, ia_1));

        verify(rqp, times(1)).sendIndirectAttribution(ia_1_string);
        verify(rqp, times(1)).sendDirectAttributionLegacy(cid_1, cuid_1);
    }

    /**
     * Validate that after init provided direct attribution is recorded correctly
     * Both values set, everything recorded
     */
    @Test
    public void postInitOnlyDA() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordDirectAttribution(daType_Countly, daValue_Countly);

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(1)).sendDirectAttributionLegacy(cid_1, cuid_1);
    }

    /**
     * Validate that after init provided direct attribution is recorded correctly
     * "cid" is empty, nothing recorded
     */
    @Test
    public void postInitOnlyDA_2() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordDirectAttribution(daType_Countly, daValue_Countly_2);

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that after init provided direct attribution is recorded correctly
     * "cid" is null, nothing recorded
     */
    @Test
    public void postInitOnlyDA_3() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordDirectAttribution(daType_Countly, daValue_Countly_3);

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that after init provided direct attribution is recorded correctly
     * "cuid" is empty string, only "cid" is recorded
     */
    @Test
    public void postInitOnlyDA_4() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordDirectAttribution(daType_Countly, daValue_Countly_4);

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(1)).sendDirectAttributionLegacy(cid_1, null);
    }

    /**
     * Validate that after init provided direct attribution is recorded correctly
     * "cuid" is null, only "cid" is recorded
     */
    @Test
    public void postInitOnlyDA_5() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordDirectAttribution(daType_Countly, daValue_Countly_5);

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(1)).sendDirectAttributionLegacy(cid_1, null);
    }

    /**
     * Validate that after init provided indirect attribution is recorded correctly
     */
    @Test
    public void postInitOnlyIA() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordIndirectAttribution(ia_1);

        verify(rqp, times(1)).sendIndirectAttribution(ia_1_string);
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that after init provided indirect attribution is recorded correctly
     * Make sure that useless values are removed
     */
    @Test
    public void postInitOnlyIABadValues() {
        ia_1.put(null, "asd");
        ia_1.put("", "asd");
        ia_1.put("aa", null);
        ia_1.put("aa1", "");

        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordIndirectAttribution(ia_1);

        verify(rqp, times(1)).sendIndirectAttribution(ia_1_string);
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that after init provided indirect attribution is recorded correctly
     * Make sure that null attribution value is ignored
     */
    @Test
    public void postInitOnlyIABadValues_2() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordIndirectAttribution(null);

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Validate that after init provided indirect attribution is recorded correctly
     * Make sure that multiple provided values are recorded correctly
     */
    @Test
    public void postInitOnlyIA_2() {
        RequestQueueProvider rqp = mock(RequestQueueProvider.class);
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, rqp, null, null, null));

        verify(rqp, times(0)).sendIndirectAttribution(any(String.class));
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));

        mCountly.attribution().recordIndirectAttribution(ia_2);

        verify(rqp, times(1)).sendIndirectAttribution(ia_2_string);
        verify(rqp, times(0)).sendDirectAttributionLegacy(any(String.class), any(String.class));
    }

    /**
     * Make sure that a post init indirect attribution recording is recorded correctly in RQ
     */
    @Test
    public void postInitAddedToRQOnlyIA() {
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null));
        mCountly.attribution().recordIndirectAttribution(ia_1);
        TestUtils.validateThatRQContainsCorrectEntry(countlyStore, "aid", UtilsNetworking.urlEncodeString(ia_1_string), 1);
    }

    /**
     * Make sure that a post init indirect attribution recording is recorded correctly in RQ
     * Multiple IA values
     */
    @Test
    public void postInitAddedToRQOnlyIA_2() {
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null));
        mCountly.attribution().recordIndirectAttribution(ia_2);
        TestUtils.validateThatRQContainsCorrectEntry(countlyStore, "aid", UtilsNetworking.urlEncodeString(ia_2_string), 1);
    }

    /**
     * Make sure that a post init direct attribution recording is recorded correctly in RQ
     * Both correct values
     */
    @Test
    public void postInitAddedToRQOnlyDA() {
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null));
        mCountly.attribution().recordDirectAttribution(daType_Countly, daValue_Countly);
        TestUtils.validateThatRQContainsCorrectEntry(countlyStore, "campaign_id", UtilsNetworking.urlEncodeString(cid_1), 1);
        TestUtils.validateThatRQContainsCorrectEntry(countlyStore, "campaign_user", UtilsNetworking.urlEncodeString(cuid_1), 1);
    }

    /**
     * Make sure that a post init direct attribution recording is recorded correctly in RQ
     * Only correct "cid"
     */
    @Test
    public void postInitAddedToRQOnlyDA_2() {
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null));
        mCountly.attribution().recordDirectAttribution(daType_Countly, daValue_Countly_4);
        TestUtils.validateThatRQContainsCorrectEntry(countlyStore, "campaign_id", UtilsNetworking.urlEncodeString(cid_1), 1);
        TestUtils.validateThatRQContainsCorrectEntry(countlyStore, "campaign_user", null, 0);
    }

    /**
     * Make sure that a post init direct attribution recording is recorded correctly in RQ
     * Recording test data
     */
    @Test
    public void postInitAddedToRQOnlyDA_3() {
        Countly mCountly = new Countly().init(TestUtils.createAttributionCountlyConfig(false, null, null, null, null, null, null));
        mCountly.attribution().recordDirectAttribution(daType_test, daValue_Test);
        TestUtils.validateThatRQContainsCorrectEntry(countlyStore, "attribution_data", UtilsNetworking.urlEncodeString(daValue_Test), 1);
    }
}
