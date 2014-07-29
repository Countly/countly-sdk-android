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
package ly.count.android.api;

import android.test.AndroidTestCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import static org.mockito.Mockito.*;

public class ConnectionProcessorTests extends AndroidTestCase {
    ConnectionProcessor connectionProcessor;
    CountlyStore mockStore;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DeviceInfo.setDeviceID("1234");
        mockStore = mock(CountlyStore.class);
        connectionProcessor = new ConnectionProcessor("http://server", mockStore);
    }

    public void testConstructorAndGetters() {
        final String serverURL = "https://secureserver";
        final CountlyStore mockStore = mock(CountlyStore.class);
        final ConnectionProcessor connectionProcessor1 = new ConnectionProcessor(serverURL, mockStore);
        assertEquals(serverURL, connectionProcessor1.getServerURL());
        assertSame(mockStore, connectionProcessor1.getCountlyStore());
    }

    public void testUrlConnectionForEventData() throws IOException {
        final String eventData = "blahblahblah";
        final URLConnection urlConnection = connectionProcessor.urlConnectionForEventData(eventData);
        assertEquals(30000, urlConnection.getConnectTimeout());
        assertEquals(30000, urlConnection.getReadTimeout());
        assertFalse(urlConnection.getUseCaches());
        assertTrue(urlConnection.getDoInput());
        assertFalse(urlConnection.getDoOutput());
        assertEquals(new URL(connectionProcessor.getServerURL() + "/i?" + eventData), urlConnection.getURL());
    }

    public void testRun_storeReturnsNullConnections() throws IOException {
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(null);
        connectionProcessor.run();
        verify(mockStore).connections();
        verify(connectionProcessor, times(0)).urlConnectionForEventData(anyString());
    }

    public void testRun_storeReturnsEmptyConnections() throws IOException {
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[0]);
        connectionProcessor.run();
        verify(mockStore).connections();
        verify(connectionProcessor, times(0)).urlConnectionForEventData(anyString());
    }

    private static class TestInputStream extends InputStream {
        int readCount = 0;
        boolean fullyRead() { return readCount >= 2; }
        boolean closed = false;

        @Override
        public int read() throws IOException {
            return readCount++ < 1 ? 1 : -1;
        }

        @Override
        public void close() throws IOException {
            super.close();
            closed = true;
        }
    }

    private static class CountlyResponseStream extends ByteArrayInputStream {
        boolean closed = false;

        CountlyResponseStream(final String result) throws UnsupportedEncodingException {
            super(("{\"result\":\"" + result + "\"}").getBytes("UTF-8"));
        }

        boolean fullyRead() { return pos == buf.length; }

        @Override
        public void close() throws IOException {
            super.close();
            closed = true;
        }
    }

    public void testRun_storeHasSingleConnection() throws IOException {
        final String eventData = "blahblahblah";
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[]{eventData}, new String[0]);
        final HttpURLConnection mockURLConnection = mock(HttpURLConnection.class);
        final CountlyResponseStream testInputStream = new CountlyResponseStream("Success");
        when(mockURLConnection.getInputStream()).thenReturn(testInputStream);
        when(mockURLConnection.getResponseCode()).thenReturn(200);
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        connectionProcessor.run();
        verify(mockStore, times(2)).connections();
        verify(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        verify(mockURLConnection).connect();
        verify(mockURLConnection).getInputStream();
        verify(mockURLConnection).getResponseCode();
        assertTrue(testInputStream.fullyRead());
        verify(mockStore).removeConnection(eventData);
        assertTrue(testInputStream.closed);
        verify(mockURLConnection).disconnect();
    }

    public void testRun_storeHasSingleConnection_butHTTPResponseCodeWasNot2xx() throws IOException {
        final String eventData = "blahblahblah";
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[]{eventData}, new String[0]);
        final HttpURLConnection mockURLConnection = mock(HttpURLConnection.class);
        final CountlyResponseStream testInputStream = new CountlyResponseStream("Success");
        when(mockURLConnection.getInputStream()).thenReturn(testInputStream);
        when(mockURLConnection.getResponseCode()).thenReturn(300);
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        connectionProcessor.run();
        verify(mockStore).connections();
        verify(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        verify(mockURLConnection).connect();
        verify(mockURLConnection).getInputStream();
        verify(mockURLConnection).getResponseCode();
        assertTrue(testInputStream.fullyRead());
        verify(mockStore, times(0)).removeConnection(eventData);
        assertTrue(testInputStream.closed);
        verify(mockURLConnection).disconnect();
    }

    public void testRun_storeHasSingleConnection_butResponseWasNotJSON() throws IOException {
        final String eventData = "blahblahblah";
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[]{eventData}, new String[0]);
        final HttpURLConnection mockURLConnection = mock(HttpURLConnection.class);
        final TestInputStream testInputStream = new TestInputStream();
        when(mockURLConnection.getInputStream()).thenReturn(testInputStream);
        when(mockURLConnection.getResponseCode()).thenReturn(200);
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        connectionProcessor.run();
        verify(mockStore).connections();
        verify(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        verify(mockURLConnection).connect();
        verify(mockURLConnection).getInputStream();
        verify(mockURLConnection).getResponseCode();
        assertTrue(testInputStream.fullyRead());
        verify(mockStore, times(0)).removeConnection(eventData);
        assertTrue(testInputStream.closed);
        verify(mockURLConnection).disconnect();
    }

    public void testRun_storeHasSingleConnection_butResponseJSONWasNotSuccess() throws IOException {
        final String eventData = "blahblahblah";
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[]{eventData}, new String[0]);
        final HttpURLConnection mockURLConnection = mock(HttpURLConnection.class);
        final CountlyResponseStream testInputStream = new CountlyResponseStream("Failed");
        when(mockURLConnection.getInputStream()).thenReturn(testInputStream);
        when(mockURLConnection.getResponseCode()).thenReturn(200);
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        connectionProcessor.run();
        verify(mockStore).connections();
        verify(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        verify(mockURLConnection).connect();
        verify(mockURLConnection).getInputStream();
        assertTrue(testInputStream.fullyRead());
        verify(mockURLConnection).getResponseCode();
        verify(mockStore, times(0)).removeConnection(eventData);
        assertTrue(testInputStream.closed);
        verify(mockURLConnection).disconnect();
    }

    public void testRun_storeHasSingleConnection_successCheckIsCaseInsensitive() throws IOException {
        final String eventData = "blahblahblah";
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[]{eventData}, new String[0]);
        final HttpURLConnection mockURLConnection = mock(HttpURLConnection.class);
        final CountlyResponseStream testInputStream = new CountlyResponseStream("SuCcEsS");
        when(mockURLConnection.getInputStream()).thenReturn(testInputStream);
        when(mockURLConnection.getResponseCode()).thenReturn(200);
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        connectionProcessor.run();
        verify(mockStore, times(2)).connections();
        verify(connectionProcessor).urlConnectionForEventData(eventData + "&device_id=" + DeviceInfo.getDeviceID());
        verify(mockURLConnection).connect();
        verify(mockURLConnection).getInputStream();
        verify(mockURLConnection).getResponseCode();
        assertTrue(testInputStream.fullyRead());
        verify(mockStore).removeConnection(eventData);
        assertTrue(testInputStream.closed);
        verify(mockURLConnection).disconnect();
    }

    public void testRun_storeHasTwoConnections() throws IOException {
        final String eventData1 = "blahblahblah";
        final String eventData2 = "123523523432";
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[]{eventData1, eventData2}, new String[]{eventData2}, new String[0]);
        final HttpURLConnection mockURLConnection = mock(HttpURLConnection.class);
        final CountlyResponseStream testInputStream1 = new CountlyResponseStream("Success");
        final CountlyResponseStream testInputStream2 = new CountlyResponseStream("Success");
        when(mockURLConnection.getInputStream()).thenReturn(testInputStream1, testInputStream2);
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData1 + "&device_id=" + DeviceInfo.getDeviceID());
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData2 + "&device_id=" + DeviceInfo.getDeviceID());
        when(mockURLConnection.getResponseCode()).thenReturn(200, 200);
        connectionProcessor.run();
        verify(mockStore, times(3)).connections();
        verify(connectionProcessor).urlConnectionForEventData(eventData1 + "&device_id=" + DeviceInfo.getDeviceID());
        verify(connectionProcessor).urlConnectionForEventData(eventData2 + "&device_id=" + DeviceInfo.getDeviceID());
        verify(mockURLConnection, times(2)).connect();
        verify(mockURLConnection, times(2)).getInputStream();
        verify(mockURLConnection, times(2)).getResponseCode();
        assertTrue(testInputStream1.fullyRead());
        assertTrue(testInputStream2.fullyRead());
        verify(mockStore).removeConnection(eventData1);
        verify(mockStore).removeConnection(eventData2);
        assertTrue(testInputStream1.closed);
        assertTrue(testInputStream2.closed);
        verify(mockURLConnection, times(2)).disconnect();
    }

    private static class TestInputStream2 extends InputStream {
        boolean closed = false;

        @Override
        public int read() throws IOException {
            throw new IOException();
        }

        @Override
        public void close() throws IOException {
            super.close();
            closed = true;
        }
    }

    public void testRun_storeHasTwoConnections_butFirstOneThrowsWhenInputStreamIsRead() throws IOException {
        final String eventData1 = "blahblahblah";
        final String eventData2 = "123523523432";
        connectionProcessor = spy(connectionProcessor);
        when(mockStore.connections()).thenReturn(new String[]{eventData1, eventData2}, new String[]{eventData2}, new String[0]);
        final HttpURLConnection mockURLConnection = mock(HttpURLConnection.class);
        final TestInputStream2 testInputStream = new TestInputStream2();
        when(mockURLConnection.getInputStream()).thenReturn(testInputStream);
        doReturn(mockURLConnection).when(connectionProcessor).urlConnectionForEventData(eventData1 + "&device_id=" + DeviceInfo.getDeviceID());
        connectionProcessor.run();
        verify(mockStore).connections();
        verify(connectionProcessor).urlConnectionForEventData(eventData1 + "&device_id=" + DeviceInfo.getDeviceID());
        verify(connectionProcessor, times(0)).urlConnectionForEventData(eventData2 + "&device_id=" + DeviceInfo.getDeviceID());
        verify(mockURLConnection).connect();
        verify(mockURLConnection).getInputStream();
        verify(mockStore, times(0)).removeConnection(anyString());
        assertTrue(testInputStream.closed);
        verify(mockURLConnection).disconnect();
    }
}
