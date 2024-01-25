package ly.count.android.demo.kotlin

import android.app.Application
import android.util.Log
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig

//import ly.count.android.sdk.DeviceIdType

class App : Application() {
  val COUNTLY_SERVER_URL = "https://your.server.ly"
  val COUNTLY_APP_KEY = "YOUR_APP_KEY"
  override fun onCreate() {

    if (COUNTLY_SERVER_URL == "https://your.server.ly" || COUNTLY_APP_KEY == "YOUR_APP_KEY") {
      Log.e("CountlyDemo", "Please provide correct COUNTLY_SERVER_URL and COUNTLY_APP_KEY")
      return
    }

    super.onCreate()

    val countlyConfig = CountlyConfig(
      this,
      COUNTLY_APP_KEY,
      COUNTLY_SERVER_URL
    )
      .setDeviceId(
        "myDeviceId"
      )
      .enableCrashReporting()
      .setRecordAllThreadsWithCrash()
      .setLoggingEnabled(true)
      .setViewTracking(false)

    Countly.sharedInstance().init(countlyConfig)
  }
}