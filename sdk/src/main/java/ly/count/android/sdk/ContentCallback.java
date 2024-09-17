package ly.count.android.sdk;

import java.util.Map;

public interface ContentCallback {
    void onContentCallback(ContentStatus contentStatus, Map<String, Object> contentData);
}
