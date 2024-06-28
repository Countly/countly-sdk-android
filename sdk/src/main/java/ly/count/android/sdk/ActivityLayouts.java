package ly.count.android.sdk;

class ActivityLayouts {

    static class BaseLayout {
        int mainY = 0;
        int mainX = 0;
        int layoutHeight = -1;
        int layoutWidth = -1;
        int webViewHeight = -1;
        int webViewWidth = -1;
        int iconTopMargin = 0;
        int iconEndMargin = 0;

        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            mainY = 0;
            mainX = 0;
            layoutWidth = screenWidth;
            layoutHeight = screenHeight;
            webViewWidth = screenWidth;
            webViewHeight = screenHeight;
            iconTopMargin = closeIconHeight / 2;
            iconEndMargin = closeIconWidth / 2;
        }
    }

    static class TopHalfLayout extends BaseLayout {
        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            layoutHeight = screenHeight / 2;
            webViewHeight = layoutHeight;
        }
    }

    static class BottomHalfLayout extends BaseLayout {
        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainY = screenHeight / 2;
            layoutHeight = (screenHeight / 2 + closeIconHeight / 2);
            webViewHeight = screenHeight / 2;
        }
    }

    static class TopBarLayout extends BaseLayout {
        double barHeightPercentage;
        double barWidthPercentage;

        TopBarLayout(double barHeightPercentage, double barWidthPercentage) {
            this.barHeightPercentage = barHeightPercentage;
            this.barWidthPercentage = barWidthPercentage;
        }

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainY = -screenHeight / 2;
            mainX = closeIconWidth / 4;
            layoutHeight = (int) (screenHeight * barHeightPercentage) + closeIconHeight;
            layoutWidth = (int) (screenWidth * barWidthPercentage) + closeIconWidth / 2;
            webViewHeight = (int) (screenHeight * barHeightPercentage);
            webViewWidth = (int) (screenWidth * barWidthPercentage);
        }
    }

    static class BottomBarLayout extends TopBarLayout {

        BottomBarLayout(double barHeightPercentage, double barWidthPercentage) {
            super(barHeightPercentage, barWidthPercentage);
        }

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainY = (int) (screenHeight / 2 - (screenHeight * barHeightPercentage) + closeIconHeight);
        }
    }

    static class CenterPaddedLayout extends BaseLayout {

        double barHeightPercentage;
        double barWidthPercentage;

        CenterPaddedLayout(double barHeightPercentage, double barWidthPercentage) {
            this.barHeightPercentage = barHeightPercentage;
            this.barWidthPercentage = barWidthPercentage;
        }

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainX = closeIconWidth / 4;
            mainY = 0;
            layoutHeight = (int) ((screenHeight * barHeightPercentage) + closeIconHeight);
            layoutWidth = (int) ((screenWidth * barWidthPercentage) + closeIconWidth / 2);
            webViewHeight = (int) (screenHeight * barHeightPercentage);
            webViewWidth = (int) (screenWidth * barWidthPercentage);
        }
    }

    static class CenterWholeLayout extends BaseLayout {

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            layoutHeight = screenHeight / 2 + closeIconHeight / 2;
            webViewHeight = screenHeight / 2;
        }
    }

    abstract static class BaseQuarterLayout extends BaseLayout {
        double quarterHeightPercentage;
        double quarterWidthPercentage;

        BaseQuarterLayout(double quarterHeightPercentage, double quarterWidthPercentage) {
            this.quarterHeightPercentage = quarterHeightPercentage;
            this.quarterWidthPercentage = quarterWidthPercentage;
        }

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainY = (int) ((screenHeight / 2 - (screenHeight * quarterHeightPercentage) + closeIconHeight));
            mainX = (int) (screenWidth * quarterWidthPercentage * quarterWidthPercentage);
            layoutHeight = (int) ((screenHeight * quarterHeightPercentage) + closeIconHeight);
            layoutWidth = (int) ((screenWidth * quarterWidthPercentage) + closeIconWidth / 2);
            webViewHeight = (int) (screenHeight * quarterHeightPercentage);
            webViewWidth = (int) (screenWidth * quarterWidthPercentage);
        }
    }

    static class BRQLayout extends BaseQuarterLayout {
        BRQLayout(double quarterHeightPercentage, double quarterWidthPercentage) {
            super(quarterHeightPercentage, quarterWidthPercentage);
        }
    }

    static class BLQLayout extends BaseQuarterLayout {
        BLQLayout(double quarterHeightPercentage, double quarterWidthPercentage) {
            super(quarterHeightPercentage, quarterWidthPercentage);
        }

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainX = -mainX;
        }
    }

    static class TRQLayout extends BaseQuarterLayout {
        TRQLayout(double quarterHeightPercentage, double quarterWidthPercentage) {
            super(quarterHeightPercentage, quarterWidthPercentage);
        }

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainY = -mainY;
        }
    }

    static class TLQLayout extends BaseQuarterLayout {
        TLQLayout(double quarterHeightPercentage, double quarterWidthPercentage) {
            super(quarterHeightPercentage, quarterWidthPercentage);
        }

        @Override
        void applyValues(int screenHeight, int screenWidth, int closeIconHeight, int closeIconWidth) {
            super.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
            mainY = -mainY;
            mainX = -mainX;
        }
    }
}