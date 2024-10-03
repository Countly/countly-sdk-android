package ly.count.android.sdk;

class TestLifecycleObserver implements Countly.LifecycleObserver {
    boolean isForeground = false;

    @Override
    public boolean LifeCycleAtleastStarted() {
        return isForeground;
    }

    protected void bringToForeground() {
        this.isForeground = true;
    }

    protected void goToBackground() {
        this.isForeground = false;
    }
}
