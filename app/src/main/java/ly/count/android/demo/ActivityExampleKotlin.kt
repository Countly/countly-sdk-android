package ly.count.android.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ly.count.android.sdk.Countly
import ly.count.android.sdk.PersistentName

/**
 * Sample Activity in Kotlin
 */
@PersistentName("ActivityExampleKotlin")
class ActivityExampleKotlin : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onStart() {
    super.onStart()
    Countly.sharedInstance().apm().startTrace("fff");
    Countly.sharedInstance().consent().checkAllConsent();
    Countly.sharedInstance().crashes().addCrashBreadcrumb("ddd");
    Countly.sharedInstance().events().recordEvent("ddd");
    val count : Int = Countly.sharedInstance().ratings().getAutomaticStarRatingSessionLimit();
    Countly.sharedInstance().remoteConfig().allValues;
    Countly.sharedInstance().sessions().beginSession()
    Countly.sharedInstance().views().isAutomaticViewTrackingEnabled;
  }
}