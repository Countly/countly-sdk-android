package ly.count.android.sdk;

import java.util.Map;

public class ExperimentInformation {
    public String experimentID;
    public String experimentName;
    public String experimentDescription;
    public String currentVariant;
    public Map<String, Map<String, Object>> variants;

    public ExperimentInformation(String experimentID, String experimentName, String experimentDescription, String currentVariant, Map<String, Map<String, Object>> variants) {
        this.experimentID = experimentID;
        this.experimentName = experimentName;
        this.experimentDescription = experimentDescription;
        this.currentVariant = currentVariant;
        this.variants = variants;
    }
}
