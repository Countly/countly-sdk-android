package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ModuleRatingsTests {
    Countly mCountly;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext());
        countlyStore.clear();

        mCountly = new Countly();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "http://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void recordManualRating() {
        final EventQueue mockEventQueue = mock(EventQueue.class);
        mCountly.setEventQueue(mockEventQueue);

        String[] vals = new String[]{"aa", "bb", "cc"};
        int rating = 3;
        mCountly.ratings().recordManualRating(vals[0], rating, vals[1], vals[2], true);

        final Map<String, String> segmS = new HashMap<>(6);
        final Map<String, Integer> segmI = new HashMap<>();
        final Map<String, Double> segmD = new HashMap<>();
        final Map<String, Boolean> segmB = new HashMap<>(1);

        segmS.put("platform", "android");
        segmS.put("app_version", "1.0");
        segmS.put("rating", "" + rating);
        segmS.put("widget_id", vals[0]);
        segmS.put("email", vals[1]);
        segmS.put("comment", vals[2]);

        segmB.put("contactMe", true);


        verify(mockEventQueue).recordEvent(ModuleRatings.STAR_RATING_EVENT_KEY, segmS, segmI, segmD, segmB,1, 0, 0, null);
    }
}
