package ly.count.android.sdk.messaging;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ly.count.android.sdk.Countly;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PushTests {

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void decodeMessage() {
        //{c.b=[{"t":"Button 1","l":"https:\/\/www.google111.com"},{"t":"Button 2","l":"https:\/\/www.google222.com"}], c.i=5e56ae8c80171b2dc1154f3d, c.l=https://www.google333.com, sound=default, title=112, message=rewrwer}

        Map<String, String> values = new HashMap<>();
        values.put("c.b", "[{\"t\":\"Button 1\",\"l\":\"https:\\/\\/www.google111.com\"},{\"t\":\"Button 2\",\"l\":\"https:\\/\\/www.google222.com\"}]");

        values.put("c.i", "5e56ae8c80171b2dc1154f3d");
        values.put("c.l", "https://www.google333.com");
        values.put("sound", "default");
        values.put("title", "112");
        values.put("message", "rewrwer");

        ModulePush.MessageImpl message = new ModulePush.MessageImpl(values);

        Assert.assertEquals("5e56ae8c80171b2dc1154f3d", message.id);
        Assert.assertEquals("https://www.google333.com", message.link().toString());
        Assert.assertEquals("default", message.sound());
        Assert.assertEquals("112", message.title());
        Assert.assertEquals("rewrwer", message.message());

        List<CountlyPush.Button> buttons = message.buttons();
        Assert.assertEquals("Button 1", buttons.get(0).title());
        Assert.assertEquals("https://www.google111.com", buttons.get(0).link().toString());

        Assert.assertEquals("Button 2", buttons.get(1).title());
        Assert.assertEquals("https://www.google222.com", buttons.get(1).link().toString());
    }
}
