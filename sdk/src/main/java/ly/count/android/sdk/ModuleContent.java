package ly.count.android.sdk;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class ModuleContent extends ModuleBase {

    private String currentContentChecksum = null;
    private final ImmediateRequestGenerator iRGenerator;
    Content contentInterface;

    ModuleContent(@NonNull Countly cly, @NonNull CountlyConfig config) {
        super(cly, config);
        L.v("[ModuleRemoteConfig] Initialising");
        iRGenerator = config.immediateRequestGenerator;

        L.d("[ModuleRemoteConfig] Setting if remote config Automatic triggers enabled, " + config.enableRemoteConfigAutomaticDownloadTriggers + ", caching enabled: " + config.enableRemoteConfigValueCaching + ", auto enroll enabled: " + config.enableAutoEnrollFlag);
        contentInterface = new Content();
    }

    void fetchContentsInternal() {
        L.d("[ModuleContent] fetchContentsInternal, new content data available, fetching it");

        DisplayMetrics displayMetrics = deviceInfo.mp.getDisplayMetrics(_cly.context_);
        String requestData = prepareContentFetchRequest(displayMetrics);

        ConnectionProcessor cp = requestQueueProvider.createConnectionProcessor();
        final boolean networkingIsEnabled = cp.configProvider_.getNetworkingEnabled();

        iRGenerator.CreateImmediateRequestMaker().doWork(requestData, "/i/content/queue", cp, false, networkingIsEnabled, checkResponse -> {
            L.d("[ModuleContent] fetchContentsInternal, processing fetched contents, received response is :[" + checkResponse + "]");
            if (checkResponse == null) {
                return;
            }

            try {
                if (validateResponse(checkResponse)) {
                    L.d("[ModuleContent] fetchContentsInternal, got new content data, showing it");
                    Map<Integer, TransparentActivityConfig> placementCoordinates = parseContent(checkResponse, displayMetrics);
                    if (placementCoordinates.isEmpty()) {
                        L.d("[ModuleContent] fetchContentsInternal, placement coordinates are empty, skipping");
                        return;
                    }

                    Intent intent = new Intent(_cly.context_, TransparentActivity.class);
                    intent.putExtra(TransparentActivity.CONFIGURATION_LANDSCAPE, placementCoordinates.get(Configuration.ORIENTATION_LANDSCAPE));
                    intent.putExtra(TransparentActivity.CONFIGURATION_PORTRAIT, placementCoordinates.get(Configuration.ORIENTATION_PORTRAIT));
                    intent.putExtra(TransparentActivity.ORIENTATION, _cly.context_.getResources().getConfiguration().orientation);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    _cly.context_.startActivity(intent);
                }
            } catch (Exception ex) {
                L.e("[ModuleContent] fetchContentsInternal, Encountered internal issue while trying to fetch contents, [" + ex + "]");
            }
        }, L);
    }

    @NonNull
    private String prepareContentFetchRequest(@NonNull DisplayMetrics displayMetrics) {

        Resources resources = _cly.context_.getResources();
        int currentOrientation = resources.getConfiguration().orientation;
        boolean portrait = currentOrientation == Configuration.ORIENTATION_PORTRAIT;
        int navbarHeightScaled = 0;

        if (!ViewConfiguration.get(_cly.context_).hasPermanentMenuKey()) {
            int navbarHeightId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
            if (navbarHeightId > 0) {
                navbarHeightScaled = (int) Math.ceil(resources.getDimensionPixelSize(navbarHeightId) / displayMetrics.density);
            }
        }

        int scaledWidth = (int) Math.ceil(displayMetrics.widthPixels / displayMetrics.density);
        int scaledHeight = (int) Math.ceil(displayMetrics.heightPixels / displayMetrics.density);

        int portraitWidth = portrait ? scaledWidth : scaledHeight;
        int portraitHeight = (portrait ? scaledHeight : scaledWidth) + navbarHeightScaled;
        int landscapeWidth = (portrait ? scaledHeight : scaledWidth);
        int landscapeHeight = portrait ? scaledWidth : scaledHeight;

        return requestQueueProvider.prepareFetchContents(portraitWidth, portraitHeight, landscapeWidth, landscapeHeight);
    }

    boolean validateResponse(@NonNull JSONObject response) {
        return response.has("placementCoordinates") && response.has("pathToHtml");
    }

    @NonNull
    Map<Integer, TransparentActivityConfig> parseContent(@NonNull JSONObject response, @NonNull DisplayMetrics displayMetrics) {
        Map<Integer, TransparentActivityConfig> placementCoordinates = new ConcurrentHashMap<>();
        String content = response.optString("pathToHtml");

        String contentChecksum = UtilsNetworking.sha256Hash(content); // TODO store this to prevent showing the same content again
        L.d("[ModuleContent] parseContent, checksum: [" + contentChecksum + "], current checksum: [" + currentContentChecksum + "]");

        if (currentContentChecksum != null && currentContentChecksum.equals(contentChecksum)) {
            L.d("[ModuleContent] parseContent, content did not change, skipping");
            return placementCoordinates;
        }
        currentContentChecksum = contentChecksum;

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
    void halt() {
        currentContentChecksum = null;
        contentInterface = null;
    }

    public class Content {
        public void fetchContents() {
            fetchContentsInternal();
        }
    }
}
