package ly.count.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class ModuleAttribution extends ModuleBase {

    Attribution attributionInterface;

    ModuleAttribution(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleAttribution] Initialising");

        attributionInterface = new ModuleAttribution.Attribution();
    }

    void recordDirectAttributionInternal(@Nullable String campaignType, @Nullable String campaignData) {
        L.d("[ModuleAttribution] recordDirectAttributionInternal, campaign id:[" + campaignType + "], user id:[" + campaignData + "]");

        if (campaignType == null || campaignType.isEmpty()) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, provided campaign type value is not valid. Execution will be aborted.");
            return;
        }

        if (campaignData == null || campaignData.isEmpty()) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, provided campaign data value is not valid. Execution will be aborted.");
            return;
        }

        if (!campaignType.equals("countly") && !campaignType.equals("_special_test")) {
            //stop execution if the type is not "countly"
            //this is a temporary exception
            L.w("[ModuleAttribution] recordDirectAttributionInternal, recording direct attribution with a type other than 'countly' is currently not supported. Execution will be aborted.");
            return;
        }

        if (campaignType.equals("_special_test")) {
            reportSpecialTestAttribution(campaignData);
        }

        if (campaignType.equals("countly")) {
            reportLegacyInstallAttribution(campaignData);
        }
    }

    void reportLegacyInstallAttribution(@NonNull String campaignData) {
        JSONObject jObj;

        try {
            jObj = new JSONObject(campaignData);
        } catch (JSONException e) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, recording direct attribution data is not in the correct format. Execution will be aborted.");
            return;
        }

        if (!jObj.has("cid")) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, direct attribution can't be recorded because the data does not contain the 'cid' value. Execution will be aborted.");
            return;
        }

        String campaignId = null;
        try {
            campaignId = jObj.getString("cid");

            if (campaignId.isEmpty()) {
                L.e("[ModuleAttribution] recordDirectAttributionInternal, 'cid' value can't be empty string. Execution will be aborted.");
                return;
            }
        } catch (JSONException e) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, encountered issue while accessing 'cid'. Execution will be aborted.");
            return;
        }

        String campaignUserId = null;

        try {
            if (jObj.has("cuid")) {
                campaignUserId = jObj.getString("cuid");

                if (campaignUserId.isEmpty()) {
                    L.w("[ModuleAttribution] recordDirectAttributionInternal, 'cuid' value can't be empty string. value will be ignored.");
                    campaignUserId = null;
                }
            }
        } catch (JSONException e) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, encountered issue while accessing 'cuid'. Execution will be aborted.");
            return;
        }

        requestQueueProvider.sendDirectAttributionLegacy(campaignId, campaignUserId);
    }

    void reportSpecialTestAttribution(@NonNull String attributionData) {
        requestQueueProvider.sendDirectAttributionTest(attributionData);
    }

    void recordIndirectAttributionInternal(@Nullable Map<String, String> attributionId) {
        L.d("[ModuleAttribution] recordIndirectAttributionInternal, attribution id:[" + attributionId + "]");

        if (attributionId == null || attributionId.isEmpty()) {
            L.e("[ModuleAttribution] recordIndirectAttributionInternal, provided id values are not valid. Execution will be aborted.");
            return;
        }

        JSONObject jObj = new JSONObject();
        for (Map.Entry<String, String> entry : attributionId.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isEmpty()) {
                L.e("[ModuleAttribution] recordIndirectAttributionInternal, provided key is not valid [" + key + "].");
                continue;
            }

            if (value == null || value.isEmpty()) {
                L.e("[ModuleAttribution] recordIndirectAttributionInternal, for the key[" + key + "] the provided value is not valid [" + value + "].");
                continue;
            }

            try {
                jObj.putOpt(key, value);
            } catch (JSONException e) {
                L.e("[ModuleAttribution] recordIndirectAttributionInternal, an issue happened while trying to add a value: " + e.toString());
            }
        }

        if (jObj.length() == 0) {
            L.e("[ModuleAttribution] recordIndirectAttributionInternal, no valid attribution values were provided");
            return;
        }

        String attributionObj = jObj.toString();

        requestQueueProvider.sendIndirectAttribution(attributionObj);
    }

    @Override
    void initFinished(@NonNull CountlyConfig config) {
        //check if any indirect attribution value is set
        if (config.iaAttributionValues != null) {
            if (config.iaAttributionValues.isEmpty()) {
                L.e("[ModuleAttribution] provided attribution ID for indirect attribution is empty string.");
            } else {
                recordIndirectAttributionInternal(config.iaAttributionValues);
            }
        }

        //checking if any direct attribution value is set
        if (config.daCampaignData != null || config.daCampaignType != null) {
            if (config.daCampaignType == null || config.daCampaignType.isEmpty()) {
                L.e("[ModuleAttribution] Can't record direct attribution can't be recorded with an invalid campaign id.");
            } else {
                if (config.daCampaignData != null && config.daCampaignData.isEmpty()) {
                    L.e("[ModuleAttribution] For direct attribution the provided Campaign user ID can't be empty string.");
                }
                recordDirectAttributionInternal(config.daCampaignType, config.daCampaignData);
            }
        }
    }

    @Override
    public void halt() {
        attributionInterface = null;
    }

    public class Attribution {

        /**
         * Report direct user attribution
         */
        public void recordDirectAttribution(String campaignType, String campaignData) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordCampaign'");

                recordDirectAttributionInternal(campaignType, campaignData);
            }
        }

        /**
         * Report indirect user attribution
         *
         * @param attributionValues
         */
        public void recordIndirectAttribution(Map<String, String> attributionValues) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordIndirectAttribution'");

                recordIndirectAttributionInternal(attributionValues);
            }
        }
    }
}
