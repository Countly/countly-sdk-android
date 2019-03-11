/*
Copyright (c) 2012, 2013, 2014 Countly

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package ly.count.android.sdk;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.mockito.ArgumentCaptor;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EventQueueTests {
    EventQueue mEventQueue;
    CountlyStore mMockCountlyStore;

    @Before
    public void setUp() throws Exception {

        mMockCountlyStore = mock(CountlyStore.class);
        mEventQueue = new EventQueue(mMockCountlyStore);
    }

    @Test
    public void testConstructor() {
        assertSame(mMockCountlyStore, mEventQueue.getCountlyStore());
    }

    @Test
    public void testRecordEvent() {
        final String eventKey = "eventKey";
        final int count = 42;
        final double sum = 3.0d;
        final double dur = 10.0d;
        final Map<String, String> segmentation = new HashMap<String, String>(1);
        final Map<String, Integer> segmentationInt = null;
        final Map<String, Double> segmentationDouble = null;
        final long timestamp = Countly.currentTimestampMs();
        final int hour = Countly.currentHour();
        final int dow = Countly.currentDayOfWeek();
        final ArgumentCaptor<Long> arg = ArgumentCaptor.forClass(Long.class);

        mEventQueue.recordEvent(eventKey, segmentation, null, null, count, sum, dur);
        verify(mMockCountlyStore).addEvent(eq(eventKey), eq(segmentation), eq(segmentationInt), eq(segmentationDouble), arg.capture(), eq(hour), eq(dow), eq(count), eq(sum), eq(dur));
        assertTrue(((timestamp - 1) <= arg.getValue()) && ((timestamp + 1) >= arg.getValue()));
    }

    @Test
    public void testSize_zeroLenArray() {
        when(mMockCountlyStore.events()).thenReturn(new String[0]);
        assertEquals(0, mEventQueue.size());
    }

    @Test
    public void testSize() {
        when(mMockCountlyStore.events()).thenReturn(new String[2]);
        assertEquals(2, mEventQueue.size());
    }

    @Test
    public void testEvents_emptyList() throws UnsupportedEncodingException {
        final List<Event> eventsList = new ArrayList<Event>();
        when(mMockCountlyStore.eventsList()).thenReturn(eventsList);

        final String expected = URLEncoder.encode("[]", "UTF-8");
        assertEquals(expected, mEventQueue.events());
        verify(mMockCountlyStore).eventsList();
        verify(mMockCountlyStore).removeEvents(eventsList);
    }

    @Test
    public void testEvents_nonEmptyList() throws UnsupportedEncodingException {
        final List<Event> eventsList = new ArrayList<Event>();
        final Event event1 = new Event();
        event1.key = "event1Key";
        eventsList.add(event1);
        final Event event2 = new Event();
        event2.key = "event2Key";
        eventsList.add(event2);
        when(mMockCountlyStore.eventsList()).thenReturn(eventsList);

        final String jsonToEncode = "[" + event1.toJSON().toString() + "," + event2.toJSON().toString() + "]";
        final String expected = URLEncoder.encode(jsonToEncode, "UTF-8");
        assertEquals(expected, mEventQueue.events());
        verify(mMockCountlyStore).eventsList();
        verify(mMockCountlyStore).removeEvents(eventsList);
    }
}
