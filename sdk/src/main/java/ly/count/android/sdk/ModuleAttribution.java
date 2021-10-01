package ly.count.android.sdk;

public class ModuleAttribution extends ModuleBase {

    Attribution attributionInterface;

    ModuleAttribution(Countly cly, CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleAttribution] Initialising");

        attributionInterface = new ModuleAttribution.Attribution();
    }

    void recordCampaignInternal(String campaignId, String campaignUserId) {
        L.d("[ModuleAttribution] recordCampaignInternal, campaign id:[" + campaignId + "], user id:[" + campaignUserId + "]");

        requestQueueProvider.sendReferrerDataManual(campaignId, campaignUserId);
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
        public void recordCampaign(String campaignId) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordCampaign'");

                recordCampaignInternal(campaignId, null);
            }
        }

        /**
         * Report user attribution manually
         *
         * @param campaignId
         */
        public void recordCampaign(String campaignId, String campaignUserId) {
            synchronized (_cly) {
                L.i("[Attribution] calling 'recordCampaign'");

                recordCampaignInternal(campaignId, campaignUserId);
            }
        }
    }
}
