package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
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

        Map<String, Object> crashMetrics = new HashMap<>();
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

        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new HashMap<>(), true);

        Assert.assertEquals(crashData.getBreadcrumbsAsString(), "key\nvalue\n");
    }

    @Test
    public void setStackTrace() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setStackTrace("ST2");
        crashData.calculateChangedFields();
        Assert.assertEquals(crashData.getStackTrace(), "ST2");
        validateChanged(crashData, true, false, false, false, false);
    }

    @Test
    public void setCrashSegmentation() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        crashData.setCrashSegmentation(crashSegmentation);
        crashData.calculateChangedFields();
        Assert.assertEquals(crashData.getCrashSegmentation(), crashSegmentation);
        validateChanged(crashData, false, true, false, false, false);
    }

    @Test
    public void setBreadcrumbs() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");
        crashData.setBreadcrumbs(breadcrumbs);
        crashData.calculateChangedFields();
        Assert.assertEquals(crashData.getBreadcrumbs(), breadcrumbs);
        validateChanged(crashData, false, false, true, false, false);
    }

    @Test
    public void setCrashMetrics() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        Map<String, Object> crashMetrics = new HashMap<>();
        crashMetrics.put("key", "value");
        crashData.setCrashMetrics(crashMetrics);
        crashData.calculateChangedFields();
        Assert.assertEquals(crashData.getCrashMetrics(), crashMetrics);
        validateChanged(crashData, false, false, false, true, false);
    }

    @Test
    public void setFatal() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setFatal(false);
        crashData.calculateChangedFields();
        Assert.assertFalse(crashData.getFatal());
        validateChanged(crashData, false, false, false, false, true);
    }

    @Test
    public void setMultipleProperties() throws JSONException {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        crashData.setCrashSegmentation(crashSegmentation);
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");
        crashData.setBreadcrumbs(breadcrumbs);
        Map<String, Object> crashMetrics = new HashMap<>();
        crashMetrics.put("key", "value");
        crashData.setCrashMetrics(crashMetrics);
        crashData.setFatal(false);
        crashData.calculateChangedFields();
        validateChanged(crashData, false, true, true, true, true);
    }

    @Test
    public void setAllProperties() throws JSONException {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        crashData.setCrashSegmentation(crashSegmentation);
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        breadcrumbs.add("value");
        crashData.setBreadcrumbs(breadcrumbs);
        HashMap<String, Object> crashMetrics = new HashMap<>();
        crashMetrics.put("key", "value");
        crashData.setCrashMetrics(crashMetrics);
        crashData.setFatal(false);
        crashData.setStackTrace("ST2");
        crashData.calculateChangedFields();
        validateChanged(crashData, true, true, true, true, true);
    }

    @Test
    public void setBreadcrumbs_null() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setBreadcrumbs(null);

        Assert.assertEquals(crashData.getBreadcrumbs(), new ArrayList<>());
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setCrashSegmentation_null() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setCrashSegmentation(null);

        Assert.assertEquals(crashData.getCrashSegmentation(), new HashMap<>());
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setCrashMetrics_null() throws JSONException {
        Map<String, Object> crashMetrics = new HashMap<>();
        crashMetrics.put("key", "value");
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), crashMetrics, true);
        crashData.setCrashMetrics(null);

        Assert.assertEquals(crashData.getCrashMetrics(), crashMetrics);
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setStackTrace_null() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setStackTrace(null);

        Assert.assertEquals(crashData.getStackTrace(), "ST");
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setStackTrace_empty() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setStackTrace("");
        crashData.calculateChangedFields();

        Assert.assertEquals(crashData.getStackTrace(), "");
        validateChanged(crashData, true, false, false, false, false);
    }

    @Test
    public void setBreadcrumbs_empty() {
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new HashMap<>(), true);
        crashData.setBreadcrumbs(new ArrayList<>());
        crashData.calculateChangedFields();

        Assert.assertEquals(crashData.getBreadcrumbs(), new ArrayList<>());
        validateChanged(crashData, false, false, true, false, false);
    }

    @Test
    public void setStackTrace_same() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setStackTrace("ST");

        Assert.assertEquals(crashData.getStackTrace(), "ST");
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setCrashSegmentation_same() {
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        CrashData crashData = new CrashData("ST", crashSegmentation, new ArrayList<>(), new HashMap<>(), true);
        crashData.setCrashSegmentation(crashSegmentation);

        Assert.assertEquals(crashData.getCrashSegmentation(), crashSegmentation);
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setBreadcrumbs_same() {
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new HashMap<>(), true);
        crashData.setBreadcrumbs(breadcrumbs);

        Assert.assertEquals(crashData.getBreadcrumbs(), breadcrumbs);
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setCrashMetrics_same() throws JSONException {
        Map<String, Object> crashMetrics = new HashMap<>();
        crashMetrics.put("key", "value");
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), crashMetrics, true);
        crashData.setCrashMetrics(crashMetrics);

        Assert.assertEquals(crashData.getCrashMetrics(), crashMetrics);
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setFatal_same() {
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), new HashMap<>(), true);
        crashData.setFatal(true);

        Assert.assertTrue(crashData.getFatal());
        validateChanged(crashData, false, false, false, false, false);
    }

    @Test
    public void setBreadcrumbs_withoutSetter() {
        List<String> breadcrumbs = new ArrayList<>();
        breadcrumbs.add("key");
        CrashData crashData = new CrashData("ST", new HashMap<>(), breadcrumbs, new HashMap<>(), true);
        crashData.getBreadcrumbs().add("value");
        crashData.calculateChangedFields();

        validateChanged(crashData, false, false, true, false, false);
        Assert.assertEquals(crashData.getBreadcrumbsAsString(), "key\nvalue\n");
    }

    @Test
    public void setCrashSegmentation_withoutSetter() {
        Map<String, Object> crashSegmentation = new HashMap<>();
        crashSegmentation.put("key", "value");
        CrashData crashData = new CrashData("ST", crashSegmentation, new ArrayList<>(), new HashMap<>(), true);
        crashData.getCrashSegmentation().put("key2", "value2");
        crashData.calculateChangedFields();

        validateChanged(crashData, false, true, false, false, false);
        Assert.assertEquals(crashData.getCrashSegmentation().get("key2"), "value2");
    }

    @Test
    public void setCrashMetrics_withoutSetter() throws JSONException {
        Map<String, Object> crashMetrics = new ConcurrentHashMap<>();
        crashMetrics.put("key", "value");
        CrashData crashData = new CrashData("ST", new HashMap<>(), new ArrayList<>(), crashMetrics, true);

        crashData.getCrashMetrics().put("key2", "value2");
        crashData.calculateChangedFields();
        validateChanged(crashData, false, false, false, true, false);
        Assert.assertEquals(crashData.getCrashMetrics().get("key2"), "value2");
    }

    private void validateChanged(CrashData crashData, boolean stackTraceChanged, boolean crashSegmentationChanged, boolean breadcrumbsChanged, boolean crashMetricsChanged, boolean fatalChanged) {
        Assert.assertEquals(getChangedFieldsAsInt(new boolean[] { stackTraceChanged, crashSegmentationChanged, breadcrumbsChanged, crashMetricsChanged, fatalChanged }), crashData.getChangedFieldsAsInt());
    }

    private int getChangedFieldsAsInt(boolean[] changedFields) {
        int result = 0;
        for (int i = changedFields.length - 1; i >= 0; i--) {
            if (changedFields[i]) {
                result |= (1 << (changedFields.length - 1 - i));
            }
        }
        return result;
    }
}
