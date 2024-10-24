package ly.count.android.sdk;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class TransparentActivityConfig implements Serializable {
    Integer x;
    Integer y;
    Integer width;
    Integer height;
    String url;
    List<WebViewUrlListener> listeners;

    TransparentActivityConfig(Integer x, Integer y, Integer width, Integer height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.listeners = new ArrayList<>();
    }
}