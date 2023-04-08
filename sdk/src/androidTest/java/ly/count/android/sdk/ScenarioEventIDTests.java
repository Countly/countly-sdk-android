package ly.count.android.sdk;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Map;
import ly.count.android.sdk.messaging.ModulePush;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class ScenarioEventIDTests {

    int idxV = 0;
    int idxE = 0;
    String[] idV = TestUtils.viewIDVals;//view ID's
    String[] idE = TestUtils.eventIDVals;//event ID's
    final String[] eKeys = TestUtils.eKeys;//event keys
    final String[] vNames = TestUtils.vNames;//view names

    SafeIDGenerator safeViewIDGenerator;
    SafeIDGenerator safeEventIDGenerator;

    @Before
    public void setUp() {
        final CountlyStore countlyStore = new CountlyStore(getContext(), mock(ModuleLog.class));
        countlyStore.clear();

        safeViewIDGenerator = new SafeIDGenerator() {
            @NonNull @Override public String GenerateValue() {
                return idV[idxV++];
            }
        };

        safeEventIDGenerator = new SafeIDGenerator() {
            @NonNull @Override public String GenerateValue() {
                return idE[idxE++];
            }
        };
    }

    @After
    public void tearDown() {
    }

    @Test
    public void eventIDScenario_mixedViews_1() {
        Activity act = mock(Activity.class);

        CountlyConfig cc = TestUtils.createScenarioEventIDConfig(safeViewIDGenerator, safeEventIDGenerator);
        Countly mCountly = new Countly().init(cc);

        EventQueueProvider eqp = TestUtils.setCreateEventQueueProviderMock(mCountly);

        //no events initially
        verifyRecordEventToEventQueueNotCalled(eqp);

        //record custom event
        mCountly.events().recordEvent(eKeys[0]);
        verifyRecordEventToEventQueueIDs(eqp, eKeys[0], idE[0], "", null, "", 0, 1);
        clearInvocations(eqp);

        //record internal non view event
        mCountly.events().recordEvent(ModuleFeedback.NPS_EVENT_KEY);
        verifyRecordEventToEventQueueIDs(eqp, ModuleFeedback.NPS_EVENT_KEY, idE[1], "", null, null, 0, 1);
        clearInvocations(eqp);

        //record custom event
        mCountly.events().recordEvent(eKeys[1]);
        verifyRecordEventToEventQueueIDs(eqp, eKeys[1], idE[2], "", null, idE[0], 0, 1);
        clearInvocations(eqp);

        //- Record view A manually
        mCountly.views().recordView(vNames[0]);
        verifyRecordEventToEventQueueIDs(eqp, ModuleViews.VIEW_EVENT_KEY, idV[0], null, "", null, 0, 1);
        clearInvocations(eqp);

        //record custom event
        mCountly.events().recordEvent(eKeys[2]);
        verifyRecordEventToEventQueueIDs(eqp, eKeys[2], idE[3], idV[0], null, idE[2], 0, 1);
        clearInvocations(eqp);

        //record internal non view event
        mCountly.events().recordEvent(ModuleFeedback.SURVEY_EVENT_KEY);
        verifyRecordEventToEventQueueIDs(eqp, ModuleFeedback.SURVEY_EVENT_KEY, idE[4], idV[0], null, null, 0, 1);
        clearInvocations(eqp);

        //- Start view B automatically
        mCountly.onStart(act);
        verifyRecordEventToEventQueueIDs(eqp, ModuleViews.VIEW_EVENT_KEY, idV[0], null, "", null, 0, 2);
        verifyRecordEventToEventQueueIDs(eqp, ModuleViews.VIEW_EVENT_KEY, idV[1], null, idV[0], null, 1, 2);
        clearInvocations(eqp);

        //record custom event
        mCountly.events().recordEvent(eKeys[3]);
        verifyRecordEventToEventQueueIDs(eqp, eKeys[3], idE[5], idV[1], null, idE[3], 0, 1);
        clearInvocations(eqp);

        //record internal non view event
        mCountly.events().recordEvent(ModuleViews.ORIENTATION_EVENT_KEY);
        verifyRecordEventToEventQueueIDs(eqp, ModuleViews.ORIENTATION_EVENT_KEY, idE[6], idV[1], null, null, 0, 1);
        clearInvocations(eqp);

        //- Close view B automatically
        mCountly.onStop();
        verifyRecordEventToEventQueueIDs(eqp, ModuleViews.VIEW_EVENT_KEY, idV[1], null, idV[0], null, 0, 1);
        clearInvocations(eqp);

        //record custom event
        mCountly.events().recordEvent(eKeys[4]);
        verifyRecordEventToEventQueueIDs(eqp, eKeys[4], idE[7], idV[1], null, idE[5], 0, 1);
        clearInvocations(eqp);

        //record internal non view event
        mCountly.events().recordEvent(ModulePush.PUSH_EVENT_ACTION);
        verifyRecordEventToEventQueueIDs(eqp, ModulePush.PUSH_EVENT_ACTION, idE[8], idV[1], null, null, 0, 1);
    }
    }

    public static void verifyRecordEventToEventQueueNotCalled(EventQueueProvider eqp) {
        ArgumentCaptor<String> arg01 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> arg02 = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Integer> arg1 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> arg2 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> arg3 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Long> arg4 = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> arg5 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> arg6 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> arg7 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg8 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg9 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg10 = ArgumentCaptor.forClass(String.class);

        verify(eqp, never()).recordEventToEventQueue(arg01.capture(), arg02.capture(), arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture(), arg7.capture(), arg8.capture(), arg9.capture(), arg10.capture());
    }

    public static void verifyRecordEventToEventQueueIDs(EventQueueProvider eqp, String eventKey, String eventID, String currentViewID, String previousViewID, String previousEventID, int entryIdx, int entryCount) {
        ArgumentCaptor<String> argEventKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> arg02 = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Integer> arg1 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> arg2 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> arg3 = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Long> arg4 = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> arg5 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> arg6 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> argEid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> argPvid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> argCvid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> argPeid = ArgumentCaptor.forClass(String.class);

        verify(eqp, times(entryCount)).recordEventToEventQueue(argEventKey.capture(), arg02.capture(), arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture(), argEid.capture(), argPvid.capture(), argCvid.capture(), argPeid.capture());

        Assert.assertEquals(eventKey, argEventKey.getAllValues().get(entryIdx));
        Assert.assertEquals(eventID, argEid.getAllValues().get(entryIdx));
        Assert.assertEquals(previousViewID, argPvid.getAllValues().get(entryIdx));
        Assert.assertEquals(currentViewID, argCvid.getAllValues().get(entryIdx));
        Assert.assertEquals(previousEventID, argPeid.getAllValues().get(entryIdx));
    }
}
