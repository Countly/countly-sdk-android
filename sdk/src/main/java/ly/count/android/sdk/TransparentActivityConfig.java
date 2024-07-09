package ly.count.android.sdk;

public class TransparentActivityConfig {
    Integer x;
    Integer y;
    Integer width;
    Integer height;
    String url;

    public TransparentActivityConfig(Integer x, Integer y, Integer width, Integer height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
