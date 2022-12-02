package com.example.kotlin

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.kotlin.databinding.ActivityMainBinding

import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig

class MainActivity : AppCompatActivity() {
    val COUNTLY_SERVER_URL = "YOUR_SERVER_URL"
    val COUNTLY_APP_KEY = "YOUR_APP_KEY"

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        Countly.sharedInstance().init(countlyConfig)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        setupActionBarWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        Countly.sharedInstance().onStart(this)
    }

    override fun onStop() {
        Countly.sharedInstance().onStop()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Countly.sharedInstance().onConfigurationChanged(newConfig)
    }
}
