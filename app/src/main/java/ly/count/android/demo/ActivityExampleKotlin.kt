package ly.count.android.demo

import androidx.appcompat.app.AppCompatActivity
import ly.count.android.sdk.Countly

/**
 * Example of using Countly SDK in Kotlin
 */
class ActivityExampleKotlin : AppCompatActivity() {
  override fun onStart() {
    super.onStart()
    Countly.sharedInstance().apm().startTrace("fff");
    Countly.sharedInstance().consent().checkAllConsent();
    Countly.sharedInstance().crashes().addCrashBreadcrumb("ddd");
    Countly.sharedInstance().events().recordEvent("ddd");
    val count: Int = Countly.sharedInstance().ratings().getAutomaticStarRatingSessionLimit();
    Countly.sharedInstance().remoteConfig().allValues;
    Countly.sharedInstance().sessions().beginSession()
    Countly.sharedInstance().views().isAutomaticViewTrackingEnabled;
    val id = Countly.sharedInstance().deviceId().id;

  }
}