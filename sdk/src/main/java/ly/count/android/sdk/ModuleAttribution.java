package ly.count.android.sdk;

import androidx.annotation.Nullable;

public class ModuleAttribution extends ModuleBase {

    Attribution attributionInterface;

    ModuleAttribution(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleAttribution] Initialising");

        attributionInterface = new ModuleAttribution.Attribution();
    }

    void recordDirectAttributionInternal(@Nullable String campaignId, @Nullable String campaignUserId) {
        L.d("[ModuleAttribution] recordDirectAttributionInternal, campaign id:[" + campaignId + "], user id:[" + campaignUserId + "]");

        if (campaignId == null || campaignId.isEmpty()) {
            L.e("[ModuleAttribution] recordDirectAttributionInternal, provided campaign id value is not valid. Execution will be aborted.");
            return;
        }

        requestQueueProvider.sendDirectAttribution(campaignId, campaignUserId);
    }

    void recordIndirectAttributionInternal(@Nullable String attributionId) {
        L.d("[ModuleAttribution] recordIndirectAttributionInternal, attribution id:[" + attributionId + "]");

        if (attributionId == null || attributionId.isEmpty()) {
            L.e("[ModuleAttribution] recordIndirectAttributionInternal, provided id value is not valid. Execution will be aborted.");
            return;
        }

        requestQueueProvider.sendIndirectAttribution(attributionId);
    }

    @Override
    public void halt() {
        attributionInterface = null;
    }

    public class Attribution {

        /**
         * Report user attribution manually
         *
         * @param campaignId
         */
        public void recordDirectAttribution(String campaignId) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordCampaign'");

                recordDirectAttributionInternal(campaignId, null);
            }
        }

        /**
         * Report direct user attribution
         *
         * @param campaignId
         */
        public void recordDirectAttribution(String campaignId, String campaignUserId) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordCampaign'");

                recordDirectAttributionInternal(campaignId, campaignUserId);
            }
        }

        /**
         * Report indirect user attribution
         *
         * @param attributionId
         */
        public void recordIndirectAttribution(String attributionId) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordIndirectAttribution'");

                recordIndirectAttributionInternal(attributionId);
            }
        }
    }
}
