package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * recordAdRevenue: one [CLY]_ad_revenue event per paid impression,
 * {"key":"[CLY]_ad_revenue","count":1,"sum":amount,"segmentation":{network, placement, format, currency, precision}}
 */
@RunWith(AndroidJUnit4.class)
public class ModuleAttributionAdRevenueTests {
    CountlyStore countlyStore;

    @Before
    public void setUp() {
        countlyStore = new CountlyStore(TestUtils.getContext(), mock(ModuleLog.class));
        countlyStore.clear();
    }

    Countly init(String[] consent) {
        CountlyConfig cc = TestUtils.createAttributionCountlyConfig(consent != null, consent, null, null, null, null, null);
        return new Countly().init(cc);
    }

    List<JSONObject> adRevenueEvents() throws Exception {
        List<JSONObject> out = new ArrayList<>();
        for (String event : countlyStore.getEvents()) {
            JSONObject json = new JSONObject(event);
            if ("[CLY]_ad_revenue".equals(json.getString("key"))) {
                out.add(json);
            }
        }
        return out;
    }

    /**
     * Every detail is a segment, the amount is the sum, count is 1, there is no duration
     */
    @Test
    public void recordAdRevenue_allDetails() throws Exception {
        Countly countly = init(null);
        countly.attribution().recordAdRevenue(0.0125, new AdRevenueDetails().setNetwork("admob").setPlacement("home_banner").setFormat("banner").setCurrency("USD").setPrecision("publisher_defined"));

        List<JSONObject> events = adRevenueEvents();
        Assert.assertEquals(1, events.size());
        JSONObject event = events.get(0);
        Assert.assertEquals(1, event.getInt("count"));
        Assert.assertEquals(0.0125, event.getDouble("sum"), 0);
        Assert.assertFalse(event.has("dur"));
        JSONObject segmentation = event.getJSONObject("segmentation");
        Assert.assertEquals("admob", segmentation.getString("network"));
        Assert.assertEquals("home_banner", segmentation.getString("placement"));
        Assert.assertEquals("banner", segmentation.getString("format"));
        Assert.assertEquals("USD", segmentation.getString("currency"));
        Assert.assertEquals("publisher_defined", segmentation.getString("precision"));
        Assert.assertEquals(5, segmentation.length());
    }

    /**
     * Details not given (null or empty) are left out; one event per call
     */
    @Test
    public void recordAdRevenue_optionalDetailsOmitted() throws Exception {
        Countly countly = init(null);
        countly.attribution().recordAdRevenue(0.5, new AdRevenueDetails().setNetwork("applovin").setFormat(""));
        countly.attribution().recordAdRevenue(0.25);

        List<JSONObject> events = adRevenueEvents();
        Assert.assertEquals(2, events.size());
        JSONObject first = events.get(0).getJSONObject("segmentation");
        Assert.assertEquals("applovin", first.getString("network"));
        Assert.assertEquals(1, first.length());
        Assert.assertEquals(0.25, events.get(1).getDouble("sum"), 0);
        JSONObject second = events.get(1).optJSONObject("segmentation");
        Assert.assertTrue(second == null || second.length() == 0);
    }

    /**
     * Zero is a valid amount; negative, NaN and infinite amounts are not recorded
     */
    @Test
    public void recordAdRevenue_invalidAmounts() throws Exception {
        Countly countly = init(null);
        countly.attribution().recordAdRevenue(-1, new AdRevenueDetails().setNetwork("admob"));
        countly.attribution().recordAdRevenue(Double.NaN, new AdRevenueDetails().setNetwork("admob"));
        countly.attribution().recordAdRevenue(Double.POSITIVE_INFINITY, new AdRevenueDetails().setNetwork("admob"));
        Assert.assertEquals(0, adRevenueEvents().size());

        countly.attribution().recordAdRevenue(0, new AdRevenueDetails().setNetwork("admob"));
        Assert.assertEquals(1, adRevenueEvents().size());
        Assert.assertEquals(0, adRevenueEvents().get(0).getDouble("sum"), 0);
    }

    /**
     * It is recorded under the "events" consent
     */
    @Test
    public void recordAdRevenue_eventsConsent() throws Exception {
        Countly countly = init(new String[] { Countly.CountlyFeatureNames.attribution });
        countly.attribution().recordAdRevenue(1.5, new AdRevenueDetails().setNetwork("admob"));
        Assert.assertEquals(0, adRevenueEvents().size());

        countly.consent().giveConsent(new String[] { Countly.CountlyFeatureNames.events });
        countly.attribution().recordAdRevenue(1.5, new AdRevenueDetails().setNetwork("admob"));
        Assert.assertEquals(1, adRevenueEvents().size());
    }
}
