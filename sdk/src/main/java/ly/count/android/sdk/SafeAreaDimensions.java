package ly.count.android.sdk;

/**
 * Class to hold safe area dimensions and offsets
 */
class SafeAreaDimensions {
    int portraitWidth;
    int portraitHeight;
    int landscapeWidth;
    int landscapeHeight;
    int portraitTopOffset;
    int landscapeTopOffset;
    int portraitLeftOffset;
    int landscapeLeftOffset;

    SafeAreaDimensions(int portraitWidth, int portraitHeight, int landscapeWidth, int landscapeHeight,
        int portraitTopOffset, int landscapeTopOffset, int portraitLeftOffset, int landscapeLeftOffset) {
        this.portraitWidth = portraitWidth;
        this.portraitHeight = portraitHeight;
        this.landscapeWidth = landscapeWidth;
        this.landscapeHeight = landscapeHeight;
        this.portraitTopOffset = portraitTopOffset;
        this.landscapeTopOffset = landscapeTopOffset;
        this.portraitLeftOffset = portraitLeftOffset;
        this.landscapeLeftOffset = landscapeLeftOffset;
    }
}
