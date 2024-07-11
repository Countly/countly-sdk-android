package ly.count.android.sdk;

import android.content.Intent;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import org.json.JSONObject;

public class ModuleContent extends ModuleBase {

    private String contentChecksum = null;
    private final ImmediateRequestGenerator iRGenerator;
    private final int requestCountToAddParameter;
    private int requestCount = 0;

    ModuleContent(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleRemoteConfig] Initialising");
        iRGenerator = config.immediateRequestGenerator;

        L.d("[ModuleRemoteConfig] Setting if remote config Automatic triggers enabled, " + config.enableRemoteConfigAutomaticDownloadTriggers + ", caching enabled: " + config.enableRemoteConfigValueCaching + ", auto enroll enabled: " + config.enableAutoEnrollFlag);
        requestCountToAddParameter = config.requestCountToAddParameter;
    }

    void fetchContents(String checksum) {
        L.d("[ModuleContent] fetchContent, checksum: [" + checksum + "], old checksum: [" + contentChecksum + "]");
        if (contentChecksum == null || !contentChecksum.equals(checksum)) {
            L.d("[ModuleContent] fetchContent, new content data available, fetching it");
            contentChecksum = checksum;

            DisplayMetrics displayMetrics = deviceInfo.mp.getDisplayMetrics(_cly.context_);
            // GIVE RESOLUTION / DENSITY
            String requestData = requestQueueProvider.prepareFetchContents(displayMetrics);

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/i/content/sdkDim", cp, false, networkingIsEnabled, checkResponse -> {
                L.d("[ModuleContent] fetchContents, processing fetched contents, received response is :[" + checkResponse + "]");
                if (checkResponse == null) {
                    return;
                }

                try {
                    if (validateResponse(checkResponse)) {
                        L.d("[ModuleContent] fetchContents, got new content data, showing it");
                        TransparentActivityConfig tac = parseContent(checkResponse);
                        showActivity(tac);
                    }
                } catch (Exception ex) {
                    L.e("[ModuleContent] fetchContents, Encountered internal issue while trying to fetch contents, [" + ex + "]");
                }
            }, L);
        }
    }

    boolean validateResponse(JSONObject response) {
        return response.has("content") && response.has("x") && response.has("y") && response.has("width") && response.has("height");
    }

    void showActivity(TransparentActivityConfig config) {
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;

        tweakSize(screenWidth, screenHeight, config);

        Intent intent = new Intent(_cly.context_, TransparentActivity.class);
        intent.putExtra(TransparentActivity.CONFIGURATION, config);

        _cly.context_.startActivity(intent);
    }

    private static void tweakSize(int screenWidth, int screenHeight, TransparentActivityConfig config) {
        //fallback to top left corner
        if (config.x == null) {
            config.x = 0;
        } else {
            config.x = (int) Math.ceil(config.x * Resources.getSystem().getDisplayMetrics().density);
        }
        if (config.y == null) {
            config.y = 0;
        } else {
            config.y = (int) Math.ceil(config.y * Resources.getSystem().getDisplayMetrics().density);
        }

        int remainingWidth = screenWidth - config.x;
        int remainingHeight = screenHeight - config.y;

        //fallback to remaining screen
        if (config.width == null) {
            config.width = remainingWidth;
        } else {
            config.width = (int) Math.ceil(config.width * Resources.getSystem().getDisplayMetrics().density);
        }
        if (config.height == null) {
            config.height = remainingHeight;
        } else {
            config.height = (int) Math.ceil(config.height * Resources.getSystem().getDisplayMetrics().density);
        }
    }

    TransparentActivityConfig parseContent(JSONObject response) {
        String content = response.optString("content");
        Integer width = response.optInt("width"); // x density
        Integer height = response.optInt("height"); // x density
        Integer x = response.optInt("x"); // x density
        Integer y = response.optInt("y"); // x density

        L.d("[ModuleContent] parseContent, content: [" + content + "], x: [" + x + "], y: [" + y + "], width: [" + width + "], height: [" + height + "]");

        TransparentActivityConfig tac = new TransparentActivityConfig(x, y, width, height);
        tac.setUrl(content);

        return tac;
    }

    /**
     * if (jsonObject.has("content")) {
     * String content = jsonObject.getString("content");
     * L.d("[ConnectionProcessor] Content received: " + content);
     * if (content != null && !content.isEmpty()) {
     * // Content is not empty, so we will do an immediate request to download the content
     * // and show it via TransparentActivity
     * // Lets wait for server ok
     * // 1 - We will going to send a request to fetch contents
     * // 2- We will look whether content checksum is changed
     * //  2.a - If it is changed show the new one
     * // 3 - Just show the content that is at the top of the queue
     * // 4 - Queue mechanism will be implemented later because they are not exact yet
     * //
     * // https://stackoverflow.com/questions/58337691/wkwebview-user-agent-swift
     * // https://stackoverflow.com/a/29218966
     * L.d("[ConnectionProcessor] Content received, will show it via TransparentActivity");
     * }
     * }
     *
     * @param request
     */

    @Override
    void onRequest(@NonNull String request) {
        //TODO please add device_id and app_key check
        assert request != null;

        requestCount++;
        if (requestCount >= requestCountToAddParameter) {
            L.d("[ModuleContent] onRequest, adding fetch_content=1 parameter to the request rc: [" + requestCount + "], rcap: [" + requestCountToAddParameter + "]");
            request += "&fetch_content=1";
            requestCount = 0;
        }
    }

    @Override
    void onResponse(@NonNull JSONObject response) {
        assert response != null;

        if (response.has("content")) {
            L.d("[ModuleContent] onResponse, got content data from server");
            String checksum = response.optString("checksum");
            if (!checksum.isEmpty()) {
                L.d("[ModuleContent] Got new remote config data, saving it");
                fetchContents(checksum);
            }
        }
    }

    @Override
    void halt() {
        contentChecksum = null;
    }
}
