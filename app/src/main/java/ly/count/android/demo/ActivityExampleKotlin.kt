package ly.count.android.demo

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import ly.count.android.sdk.Countly

class ActivityExampleKotlin : AppCompatActivity() {
  override fun onStart() {
    super.onStart()
    Countly.sharedInstance().onStart(this);

    Countly.sharedInstance().apm().startTrace("fff");
    Countly.sharedInstance().consent().checkAllConsent();
    Countly.sharedInstance().crashes().addCrashBreadcrumb("ddd");
    Countly.sharedInstance().events().recordEvent("ddd");
    val count: Int = Countly.sharedInstance().ratings().getAutomaticStarRatingSessionLimit();
    Countly.sharedInstance().remoteConfig().allValues;
    Countly.sharedInstance().sessions().beginSession()
    Countly.sharedInstance().views().isAutomaticViewTrackingEnabled;

  }

  override fun onStop() {
    Countly.sharedInstance().onStop();
    super.onStop()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    Countly.sharedInstance().onConfigurationChanged(newConfig);
  }
}