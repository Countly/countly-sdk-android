package ly.count.android.demo.kotlin

import android.app.Application
import android.util.Log
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig

/**
 * Main Application class
 */
class App : Application() {
  private val COUNTLY_SERVER_URL = "https://your.server.ly"
  private val COUNTLY_APP_KEY = "YOUR_APP_KEY"
  override fun onCreate() {
    super.onCreate()

    if (COUNTLY_SERVER_URL == "https://your.server.ly" || COUNTLY_APP_KEY == "YOUR_APP_KEY") {
      Log.e("CountlyDemo", "Please provide correct COUNTLY_SERVER_URL and COUNTLY_APP_KEY")
      return
    }

    val countlyConfig = CountlyConfig(
      this,
      COUNTLY_APP_KEY,
      COUNTLY_SERVER_URL
    )
      .setDeviceId(
        "myDeviceId"
      )
      .setLoggingEnabled(true)

    countlyConfig.crashes.enableCrashReporting().enableRecordAllThreadsWithCrash()

    Countly.sharedInstance().init(countlyConfig)
  }
}
