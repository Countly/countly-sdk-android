package ly.count.android.sdk;

interface RequestInfoProvider {
    boolean isHttpPostForced();

    boolean isDeviceAppCrawler();

    boolean ifShouldIgnoreCrawlers();

    int getRequestDropAgeHours();

    String getRequestSalt();
}
