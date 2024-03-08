package ly.count.android.sdk;

import java.util.Map;

public class CrashData {

    private String stackTrace;
    private Map<String,Object> crashSegmentation;
    private String breadcrumbs;
    protected boolean crashChanged = false;

    public CrashData(String stackTrace, Map<String,Object> crashSegmentation, String breadcrumbs) {
        this.stackTrace = stackTrace;
        this.crashSegmentation = crashSegmentation;
        this.breadcrumbs = breadcrumbs;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public Map<String,Object> getCrashSegmentation() {
        return crashSegmentation;
    }

    public String getBreadcrumbs() {
        return breadcrumbs;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        crashChanged = true;
    }

    public void setCrashSegmentation(Map<String,Object> crashSegmentation) {
        this.crashSegmentation = crashSegmentation;
        crashChanged = true;

    }


    public void setBreadcrumbs(String breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
        crashChanged = true;

    }
}
