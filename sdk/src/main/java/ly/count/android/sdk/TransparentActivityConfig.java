package ly.count.android.sdk;

class TransparentActivityConfig {
    Integer x;
    Integer y;
    Integer width;
    Integer height;
    Double widthPercent;
    Double heightPercent;
    Double xPercent;
    Double yPercent;
    String url;

    TransparentActivityConfig(Integer x, Integer y, Integer width, Integer height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void setUrl(String url) {
        this.url = url;
    }

    void setPercents(Double xPercent, Double yPercent, Double widthPercent, Double heightPercent) {
        this.xPercent = xPercent;
        this.yPercent = yPercent;
        this.widthPercent = widthPercent;
        this.heightPercent = heightPercent;
    }
}
