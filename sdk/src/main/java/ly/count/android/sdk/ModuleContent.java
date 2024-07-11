package ly.count.android.sdk;

import android.content.Intent;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class ModuleContent extends ModuleBase {

    private String contentChecksum = null;
    private final ImmediateRequestGenerator iRGenerator;
    private final int requestCountToAddParameter;
    private int requestCount = 0;
    Content contentInterface;

    ModuleContent(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleRemoteConfig] Initialising");
        iRGenerator = config.immediateRequestGenerator;

        L.d("[ModuleRemoteConfig] Setting if remote config Automatic triggers enabled, " + config.enableRemoteConfigAutomaticDownloadTriggers + ", caching enabled: " + config.enableRemoteConfigValueCaching + ", auto enroll enabled: " + config.enableAutoEnrollFlag);
        requestCountToAddParameter = config.requestCountToAddParameter;
        contentInterface = new Content();
    }

    void fetchContentsInternal(String checksum) {
        L.d("[ModuleContent] fetchContentsInternal, checksum: [" + checksum + "], old checksum: [" + contentChecksum + "]");
        if (contentChecksum == null || !contentChecksum.equals(checksum)) {
            L.d("[ModuleContent] fetchContentsInternal, new content data available, fetching it");
            contentChecksum = checksum;

            DisplayMetrics displayMetrics = deviceInfo.mp.getDisplayMetrics(_cly.context_);
            // GIVE RESOLUTION / DENSITY
            String requestData = requestQueueProvider.prepareFetchContents(displayMetrics);

            ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
            final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

            iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/i/content/sdkDim", cp, false, networkingIsEnabled, checkResponse -> {
                L.d("[ModuleContent] fetchContentsInternal, processing fetched contents, received response is :[" + checkResponse + "]");
                if (checkResponse == null) {
                    return;
                }

                try {
                    if (validateResponse(checkResponse)) {
                        L.d("[ModuleContent] fetchContentsInternal, got new content data, showing it");
                        Map<Integer, TransparentActivityConfig> placementCoordinates = parseContent(checkResponse, displayMetrics);

                        Intent intent = new Intent(_cly.context_, TransparentActivity.class);
                        intent.putExtra(TransparentActivity.CONFIGURATION_LANDSCAPE, placementCoordinates.get(Configuration.ORIENTATION_LANDSCAPE));
                        intent.putExtra(TransparentActivity.CONFIGURATION_PORTRAIT, placementCoordinates.get(Configuration.ORIENTATION_PORTRAIT));
                        intent.putExtra(TransparentActivity.ORIENTATION, _cly.context_.getResources().getConfiguration().orientation);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        _cly.context_.startActivity(intent);
                    }
                } catch (Exception ex) {
                    L.e("[ModuleContent] fetchContentsInternal, Encountered internal issue while trying to fetch contents, [" + ex + "]");
                }
            }, L);
        }
    }

    boolean validateResponse(@NonNull JSONObject response) {
        return response.has("placementCoordinates") && response.has("pathToHtml");
    }

    @NonNull
    Map<Integer, TransparentActivityConfig> parseContent(@NonNull JSONObject response, @NonNull DisplayMetrics displayMetrics) {
        Map<Integer, TransparentActivityConfig> placementCoordinates = new ConcurrentHashMap<>();
        String content = response.optString("pathToHtml");
        JSONObject coordinates = response.optJSONObject("placementCoordinates");

        assert coordinates != null;
        placementCoordinates.put(Configuration.ORIENTATION_PORTRAIT, extractOrientationPlacements(coordinates, displayMetrics.density, "portrait", content));
        placementCoordinates.put(Configuration.ORIENTATION_LANDSCAPE, extractOrientationPlacements(coordinates, displayMetrics.density, "landscape", content));

        return placementCoordinates;
    }

    private TransparentActivityConfig extractOrientationPlacements(@NonNull JSONObject placements, float density, @NonNull String orientation, @NonNull String content) {
        if (placements.has(orientation)) {
            Log.d("Countly", "[ModuleContent] extractOrientationPlacements, orientation: [" + orientation + "]");
            JSONObject orientationPlacements = placements.optJSONObject(orientation);
            assert orientationPlacements != null;
            int x = orientationPlacements.optInt("x");
            int y = orientationPlacements.optInt("y");
            int w = orientationPlacements.optInt("width");
            int h = orientationPlacements.optInt("height");
            L.d("[ModuleContent] extractOrientationPlacements, orientation: [" + orientation + "], x: [" + x + "], y: [" + y + "], w: [" + w + "], h: [" + h + "]");

            TransparentActivityConfig config = new TransparentActivityConfig((int) Math.ceil(x * density), (int) Math.ceil(y * density), (int) Math.ceil(w * density), (int) Math.ceil(h * density));
            config.setUrl(content);
            return config;
        }

        return null;
    }

    @Override
    void onRequest(@NonNull String request) {
        assert request != null;

        if (!_cly.moduleRequestQueue.doesBelongToCurrentAppKeyOrDeviceId(request)) {
            L.d("[ModuleContent] onRequest, request does not contain current app_id or device_id, skipping");
            return;
        }

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
                fetchContentsInternal(checksum);
            }
        }
    }

    @Override
    void halt() {
        contentChecksum = null;
        contentInterface = null;
    }

    public class Content {
        public void fetchContents() {
            fetchContentsInternal(UUID.randomUUID().toString());
        }
    }
}
