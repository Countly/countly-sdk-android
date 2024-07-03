package ly.count.android.sdk;

class TransparentActivityConfig {
    final int x;
    final int y;
    final int width;
    final int height;
    String url;

    TransparentActivityConfig(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void setUrl(String url) {
        this.url = url;
    }
}
