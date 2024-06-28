package ly.count.android.sdk;

public class ActivityConstants {

    // bar dimensions
    static double barWidthPercentage = 0.9;
    static double barHeightPercentage = 0.25;

    // quarter dimensions
    static double quarterWidthPercentage = 0.45;
    static double quarterHeightPercentage = 0.25;

    public static class Orientation {
        public static final int TOP_HALF = 1;
        public static final int BOTTOM_HALF = 2;
        public static final int TOP_BAR = 3;
        public static final int BOTTOM_BAR = 4;
        public static final int CENTER_PADDED = 5;
        public static final int CENTER_WHOLE = 6;
        public static final int BOTTOM_RIGHT_QUARTER = 7;
        public static final int BOTTOM_LEFT_QUARTER = 8;
        public static final int TOP_RIGHT_QUARTER = 9;
        public static final int TOP_LEFT_QUARTER = 10;
    }

    public static class Animate {
        public static final int TOP_TO_CENTER = 1;
        public static final int BOTTOM_BAR_TO_PADDED_BOTTOM_HALF = 2;
        public static final int CENTER_PADDED_TO_BOTTOM_HALF = 3;
        public static final int BOTTOM_BAR_TO_WHOLE_SCREEN = 4;
    }
}
