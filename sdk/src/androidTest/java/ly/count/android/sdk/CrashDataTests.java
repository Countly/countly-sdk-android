package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CrashDataTests {

    @Test
    public void testConstructor() throws JSONException {
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");

        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");

        JSONObject crashMetrics = new JSONObject();
        crashMetrics.put("key", "value");

        CrashData crashData = new CrashData("ST", crashSegmentation, breadcrumbs, crashMetrics, true);

        Assert.assertEquals(crashData.getStackTrace(), "ST");
        Assert.assertEquals(crashData.getCrashSegmentation(), crashSegmentation);
        Assert.assertEquals(crashData.getBreadcrumbs(), breadcrumbs);
        Assert.assertEquals(crashData.getCrashMetrics(), crashMetrics);
        Assert.assertTrue(crashData.getFatal());
    }

    @Test
    public void getBreadcrumbsAsString() {
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");

        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new JSONObject(), true);

        Assert.assertEquals(crashData.getBreadcrumbsAsString(), "key\nvalue\n");
    }

    @Test
    public void setStackTrace() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setStackTrace("ST2");
        Assert.assertEquals(crashData.getStackTrace(), "ST2");
        Assert.assertEquals(crashData.crashBits, 0b00001);
    }

    @Test
    public void setCrashSegmentation() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        crashData.setCrashSegmentation(crashSegmentation);
        Assert.assertEquals(crashData.getCrashSegmentation(), crashSegmentation);
        Assert.assertEquals(crashData.crashBits, 0b00010);
    }

    @Test
    public void setBreadcrumbs() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");
        crashData.setBreadcrumbs(breadcrumbs);
        Assert.assertEquals(crashData.getBreadcrumbs(), breadcrumbs);
        Assert.assertEquals(crashData.crashBits, 0b00100);
    }

    @Test
    public void setCrashMetrics() throws JSONException {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        JSONObject crashMetrics = new JSONObject();
        crashMetrics.put("key", "value");
        crashData.setCrashMetrics(crashMetrics);
        Assert.assertEquals(crashData.getCrashMetrics(), crashMetrics);
        Assert.assertEquals(crashData.crashBits, 0b10000);
    }

    @Test
    public void setFatal() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setFatal(false);
        Assert.assertFalse(crashData.getFatal());
        Assert.assertEquals(crashData.crashBits, 0b01000);
    }

    @Test
    public void setMultipleProperties() throws JSONException {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        crashData.setCrashSegmentation(crashSegmentation);
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");
        crashData.setBreadcrumbs(breadcrumbs);
        JSONObject crashMetrics = new JSONObject();
        crashMetrics.put("key", "value");
        crashData.setCrashMetrics(crashMetrics);
        crashData.setFatal(false);
        Assert.assertEquals(crashData.crashBits, 0b11110);
    }

    @Test
    public void setAllProperties() throws JSONException {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        crashData.setCrashSegmentation(crashSegmentation);
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");
        crashData.setBreadcrumbs(breadcrumbs);
        JSONObject crashMetrics = new JSONObject();
        crashMetrics.put("key", "value");
        crashData.setCrashMetrics(crashMetrics);
        crashData.setFatal(false);
        crashData.setStackTrace("ST2");
        Assert.assertEquals(crashData.crashBits, 0b11111);
    }

    @Test
    public void setBreadcrumbs_null() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setBreadcrumbs(null);

        Assert.assertEquals(crashData.getBreadcrumbs(), new ArrayList<>());
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setCrashSegmentation_null() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setCrashSegmentation(null);

        Assert.assertEquals(crashData.getCrashSegmentation(), new HashMap<>());
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setCrashMetrics_null() throws JSONException {
        JSONObject crashMetrics = new JSONObject();
        crashMetrics.put("key", "value");
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), crashMetrics, true);
        crashData.setCrashMetrics(null);

        Assert.assertEquals(crashData.getCrashMetrics(), crashMetrics);
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setStackTrace_null() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setStackTrace(null);

        Assert.assertEquals(crashData.getStackTrace(), "ST");
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setStackTrace_empty() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setStackTrace("");

        Assert.assertEquals(crashData.getStackTrace(), "");
        Assert.assertEquals(crashData.crashBits, 0b00001);
    }

    @Test
    public void setBreadcrumbs_empty() {
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new JSONObject(), true);
        crashData.setBreadcrumbs(new ArrayList<>());

        Assert.assertEquals(crashData.getBreadcrumbs(), new ArrayList<>());
        Assert.assertEquals(crashData.crashBits, 0b00100);
    }

    @Test
    public void setStackTrace_same() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setStackTrace("ST");

        Assert.assertEquals(crashData.getStackTrace(), "ST");
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setCrashSegmentation_same() {
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        CrashData crashData = new CrashData("ST", crashSegmentation, new ArrayList<>(), new JSONObject(), true);
        crashData.setCrashSegmentation(crashSegmentation);

        Assert.assertEquals(crashData.getCrashSegmentation(), crashSegmentation);
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setBreadcrumbs_same() {
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new JSONObject(), true);
        crashData.setBreadcrumbs(breadcrumbs);

        Assert.assertEquals(crashData.getBreadcrumbs(), breadcrumbs);
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setCrashMetrics_same() throws JSONException {
        JSONObject crashMetrics = new JSONObject();
        crashMetrics.put("key", "value");
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), crashMetrics, true);
        crashData.setCrashMetrics(crashMetrics);

        Assert.assertEquals(crashData.getCrashMetrics(), crashMetrics);
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setFatal_same() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new JSONObject(), true);
        crashData.setFatal(true);

        Assert.assertTrue(crashData.getFatal());
        Assert.assertEquals(crashData.crashBits, 0b00000);
    }

    @Test
    public void setBreadcrumbs_withoutSetter() {
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new JSONObject(), true);
        crashData.getBreadcrumbs().add("value");

        Assert.assertEquals(crashData.crashBits, 0b00000);
        Assert.assertEquals(crashData.getBreadcrumbsAsString(), "key\nvalue\n");
    }

    @Test
    public void setCrashSegmentation_withoutSetter() {
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        CrashData crashData = new CrashData("ST", crashSegmentation, new ArrayList<>(), new JSONObject(), true);
        crashData.getCrashSegmentation().put("key2", "value2");

        Assert.assertEquals(crashData.crashBits, 0b00000);
        Assert.assertEquals(crashData.getCrashSegmentation().get("key2"), "value2");
    }

    @Test
    public void setCrashMetrics_withoutSetter() throws JSONException {
        JSONObject crashMetrics = new JSONObject();
        crashMetrics.put("key", "value");
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), crashMetrics, true);
        crashData.getCrashMetrics().put("key2", "value2");

        Assert.assertEquals(crashData.crashBits, 0b00000);
        Assert.assertEquals(crashData.getCrashMetrics().get("key2"), "value2");
    }
}
