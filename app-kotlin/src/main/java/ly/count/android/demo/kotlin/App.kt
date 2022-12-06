package ly.count.android.demo.kotlin

import android.app.Application
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig

//import ly.count.android.sdk.DeviceIdType

class App : Application() {
  val COUNTLY_SERVER_URL = "https://yea.yea"
  val COUNTLY_APP_KEY = "YOUR_APP_KEY"
  override fun onCreate() {
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