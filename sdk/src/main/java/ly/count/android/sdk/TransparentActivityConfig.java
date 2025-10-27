package ly.count.android.sdk;

import java.io.Serializable;

class TransparentActivityConfig implements Serializable {
    Integer x;
    Integer y;
    Integer width;
    Integer height;
    String url;
    boolean useSafeArea = false;
    int topOffset = 0;
    int leftOffset = 0;

    TransparentActivityConfig(Integer x, Integer y, Integer width, Integer height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}