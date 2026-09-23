package ly.count.android.sdk.messaging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.TestUtils;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Posts real notifications through {@link CountlyPush#displayNotification} and inspects what the
 * system shows, covering the large icon ("c.li") and collapse key ("c.ck") payload keys.
 */
@RunWith(AndroidJUnit4.class)
public class PushNotificationDisplayTests {
    private static final long WAIT_MS = 10_000;
    private static final String TEST_ICON_RESOURCE = "countly_test_large_icon";

    private Context context;
    private NotificationManager manager;
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        Assume.assumeTrue("getActiveNotifications needs API 23", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);

        context = TestUtils.getContext();
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(), "android.permission.POST_NOTIFICATIONS");
        }
        manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CountlyPush.CHANNEL_ID, "Countly test", NotificationManager.IMPORTANCE_DEFAULT));
        }
        manager.cancelAll();

        Countly.sharedInstance().halt();
        Countly.sharedInstance().init(TestUtils.createBaseConfig());

        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        if (manager != null) {
            manager.cancelAll();
        }
        Countly.sharedInstance().halt();
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Without a collapse key every message keeps its own notification, as before.
     */
    @Test
    public void noCollapseKey_messagesStack() {
        display(message("id-1", "first", null));
        display(message("id-2", "second", null));

        Assert.assertEquals(2, waitForNotifications(2).size());
    }

    /**
     * A message with the same collapse key replaces the notification of the previous one.
     */
    @Test
    public void sameCollapseKey_replacesPreviousNotification() {
        display(message("id-1", "first", "order-42"));
        waitForNotifications(1);
        display(message("id-2", "second", "order-42"));

        SystemClock.sleep(500);
        List<StatusBarNotification> shown = activeNotifications();
        Assert.assertEquals(1, shown.size());
        Assert.assertEquals("second", shown.get(0).getNotification().extras.getString(Notification.EXTRA_TITLE));
    }

    /**
     * Messages with different collapse keys do not replace each other.
     */
    @Test
    public void differentCollapseKeys_messagesStack() {
        display(message("id-1", "first", "order-42"));
        display(message("id-2", "second", "order-43"));

        Assert.assertEquals(2, waitForNotifications(2).size());
    }

    /**
     * A collapsed notification does not replace a regular one that is already shown.
     */
    @Test
    public void collapseKey_doesNotReplaceMessageWithoutKey() {
        display(message("id-1", "first", null));
        display(message("id-2", "second", "order-42"));

        Assert.assertEquals(2, waitForNotifications(2).size());
    }

    /**
     * A large icon given as a resource name of the app is shown on the notification.
     */
    @Test
    public void largeIconResourceName_isShown() {
        Map<String, String> data = message("id-1", "first", null);
        data.put("c.li", TEST_ICON_RESOURCE);
        display(data);

        Notification shown = waitForNotifications(1).get(0).getNotification();
        Assert.assertNotNull(shown.getLargeIcon());
    }

    /**
     * A large icon given as a URL is downloaded and shown on the notification.
     */
    @Test
    public void largeIconUrl_isDownloadedAndShown() throws InterruptedException {
        server.enqueue(pngResponse(96, 96));
        Map<String, String> data = message("id-1", "first", null);
        data.put("c.li", server.url("/icon.png").toString());
        display(data);

        Notification shown = waitForNotifications(1).get(0).getNotification();
        Assert.assertNotNull(shown.getLargeIcon());
        Assert.assertEquals("/icon.png", server.takeRequest(1, TimeUnit.SECONDS).getPath());
    }

    /**
     * A large icon that cannot be resolved does not stop the notification from being shown.
     */
    @Test
    public void unknownLargeIcon_notificationShownWithoutIcon() {
        Map<String, String> data = message("id-1", "first", null);
        data.put("c.li", "no_such_resource_anywhere");
        display(data);

        Notification shown = waitForNotifications(1).get(0).getNotification();
        Assert.assertNull(shown.getLargeIcon());
    }

    /**
     * A large icon URL that fails to download does not stop the notification from being shown.
     */
    @Test
    public void failingLargeIconUrl_notificationShownWithoutIcon() {
        int previousAttempts = CountlyPush.MEDIA_DOWNLOAD_ATTEMPTS;
        CountlyPush.MEDIA_DOWNLOAD_ATTEMPTS = 1;
        try {
            server.enqueue(new MockResponse().setResponseCode(404));
            Map<String, String> data = message("id-1", "first", null);
            data.put("c.li", server.url("/missing.png").toString());
            display(data);

            Notification shown = waitForNotifications(1).get(0).getNotification();
            Assert.assertNull(shown.getLargeIcon());
        } finally {
            CountlyPush.MEDIA_DOWNLOAD_ATTEMPTS = previousAttempts;
        }
    }

    /**
     * With both a large icon and media, the large icon stays on the collapsed notification and is
     * hidden in the expanded picture so the image is not shown twice.
     */
    @Test
    public void largeIconAndMedia_largeIconHiddenWhenExpanded() {
        server.enqueue(pngResponse(400, 200));
        Map<String, String> data = message("id-1", "first", null);
        data.put("c.li", TEST_ICON_RESOURCE);
        data.put("c.m", server.url("/media.png").toString());
        display(data);

        Notification shown = waitForNotifications(1).get(0).getNotification();
        Assert.assertNotNull(shown.getLargeIcon());
        Assert.assertTrue(shown.extras.containsKey(Notification.EXTRA_LARGE_ICON_BIG));
        Assert.assertNull(shown.extras.get(Notification.EXTRA_LARGE_ICON_BIG));
    }

    /**
     * A large icon bigger than the notification needs is scaled down, keeping its aspect ratio.
     */
    @Test
    public void scaleLargeIcon_oversizedBitmapScaledDown() {
        Bitmap scaled = CountlyPush.scaleLargeIcon(Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888));

        Assert.assertEquals(CountlyPush.LARGE_ICON_MAX_SIZE, scaled.getWidth());
        Assert.assertEquals(CountlyPush.LARGE_ICON_MAX_SIZE / 2, scaled.getHeight());
    }

    /**
     * A large icon that already fits is left untouched.
     */
    @Test
    public void scaleLargeIcon_smallBitmapUnchanged() {
        Bitmap small = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);

        Assert.assertSame(small, CountlyPush.scaleLargeIcon(small));
    }

    /**
     * Shows the message as a notification. The test APK has no launcher activity, so an explicit
     * tap intent is passed instead of relying on the default one.
     */
    private void display(Map<String, String> data) {
        Intent tapIntent = new Intent().setPackage(context.getPackageName());
        Assert.assertEquals(Boolean.TRUE, CountlyPush.displayNotification(context, CountlyPush.decodeMessage(data), android.R.drawable.ic_dialog_info, tapIntent));
    }

    private static Map<String, String> message(String id, String title, String collapseKey) {
        Map<String, String> data = new HashMap<>();
        data.put("c.i", id);
        data.put("title", title);
        data.put("message", "body of " + title);
        if (collapseKey != null) {
            data.put("c.ck", collapseKey);
        }
        return data;
    }

    private static MockResponse pngResponse(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody(new Buffer().write(bytes.toByteArray()));
    }

    /**
     * Notifications currently shown for this app, without the group summary the system adds on its
     * own once an app shows several notifications.
     */
    private List<StatusBarNotification> activeNotifications() {
        List<StatusBarNotification> result = new ArrayList<>();
        for (StatusBarNotification sbn : manager.getActiveNotifications()) {
            boolean groupSummary = (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            if (context.getPackageName().equals(sbn.getPackageName()) && !groupSummary) {
                result.add(sbn);
            }
        }
        return result;
    }

    private List<StatusBarNotification> waitForNotifications(int count) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        List<StatusBarNotification> shown = activeNotifications();
        while (shown.size() < count && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100);
            shown = activeNotifications();
        }
        Assert.assertTrue("expected " + count + " notifications, got " + shown.size(), shown.size() >= count);
        return shown;
    }
}
