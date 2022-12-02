package ly.count.android.demo.kotlin

import android.app.Application
import android.util.Log
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig
import ly.count.android.sdk.RemoteConfigCallback

//import ly.count.android.sdk.DeviceIdType

class App :Application() {
  val COUNTLY_SERVER_URL = "YOUR_SERVER"
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
    .setCustomCrashSegment(mapOf(Pair("deviceIdKey", "deviceId")))
    .setRemoteConfigAutomaticDownload(true, RemoteConfigCallback { error ->
      if (error == null) {
        Log.d("Countly", "No error for automatic RC")
      } else {
        Log.d("Countly", "Automatic RC encountered issue")
      }
    })


    Countly.sharedInstance().init(countlyConfig)
  }
}