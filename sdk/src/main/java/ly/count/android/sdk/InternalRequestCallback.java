package ly.count.android.sdk;

interface InternalRequestCallback {
    default void onRequestCompleted(String response, boolean success) {
    }

    default void onRQFinished() {
    }
}
