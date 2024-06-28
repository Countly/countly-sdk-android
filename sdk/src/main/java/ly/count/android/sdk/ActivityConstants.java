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
        protected static final int TOP_BAR = 3;
        protected static final int BOTTOM_BAR = 4;
        protected static final int CENTER_PADDED = 5;
        protected static final int CENTER_WHOLE = 6;
        protected static final int BOTTOM_RIGHT_QUARTER = 7;
        protected static final int BOTTOM_LEFT_QUARTER = 8;
        protected static final int TOP_RIGHT_QUARTER = 9;
        protected static final int TOP_LEFT_QUARTER = 10;
    }

    public static class Animate {
        protected static final int TOP_TO_CENTER = 1;
        protected static final int BOTTOM_BAR_TO_PADDED_BOTTOM_HALF = 2;
        protected static final int CENTER_PADDED_TO_BOTTOM_HALF = 3;
        protected static final int BOTTOM_BAR_TO_WHOLE_SCREEN = 4;
    }
}
