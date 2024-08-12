package ly.count.android.demo;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import ly.count.android.sdk.Countly;

public class ActivityExampleContents extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_contents);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e("PIXEL", "onConfigurationChanged");
        Log.e("PIXEL", "Current orientation: " + newConfig.orientation + " Landscape: " + Configuration.ORIENTATION_LANDSCAPE + " Portrait: " + Configuration.ORIENTATION_PORTRAIT);
        Log.e("PIXEL", "width " + getResources().getDisplayMetrics().widthPixels + " height " + getResources().getDisplayMetrics().heightPixels);
        Log.e("PIXEL", "widthDPI " + newConfig.screenWidthDp + " heightDPI " + newConfig.screenHeightDp);
    }

    public void onClickFetchContents(View v) {

        Point displaySize = new Point();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(displaySize);
        Log.e("CountlyDisplay", "displaySize: " + displaySize.x + "x" + displaySize.y);

        Resources resources = getResources();

        //print navigation hidden values
        Log.e("CountlyConfInfo", "navigationHidden: " + Configuration.NAVIGATIONHIDDEN_YES + " no: " + Configuration.NAVIGATIONHIDDEN_NO + " und: " + Configuration.NAVIGATIONHIDDEN_UNDEFINED);
        Log.e("CountlyConfInfo", "keyboardHidden: " + Configuration.KEYBOARDHIDDEN_YES + " no: " + Configuration.KEYBOARDHIDDEN_NO + " und: " + Configuration.KEYBOARDHIDDEN_UNDEFINED);

        Log.e("CountlyDisplay", "density: " + resources.getDisplayMetrics().density);
        Log.e("CountlyDisplay", "orientation: " + resources.getConfiguration().orientation);
        Log.e("CountlyDisplay", "smallestScreenWidthDp: " + resources.getConfiguration().smallestScreenWidthDp);
        Log.e("CountlyDisplay", "screenWidthDp: " + resources.getConfiguration().screenWidthDp);
        Log.e("CountlyDisplay", "screenHeightDp: " + resources.getConfiguration().screenHeightDp);
        Log.e("CountlyDisplay", "screenLayout: " + resources.getConfiguration().screenLayout);
        Log.e("CountlyDisplay", "navigation: " + resources.getConfiguration().navigation);
        Log.e("CountlyDisplay", "touchscreen: " + resources.getConfiguration().touchscreen);
        Log.e("CountlyDisplay", "keyboard: " + resources.getConfiguration().keyboard);
        Log.e("CountlyDisplay", "keyboardHidden: " + resources.getConfiguration().keyboardHidden);
        Log.e("CountlyDisplay", "navigationHidden: " + resources.getConfiguration().navigationHidden);

        try {
            boolean showStatusBar = resources.getBoolean(resources.getIdentifier("config_showStatusBar", "bool", "android"));
            Log.e("CountlyDisplay", "showStatusBar: " + showStatusBar);
        } catch (Resources.NotFoundException e) {

        }

        try {
            int resourceId = resources.getIdentifier("config_showNavigationBar", "bool", "android");
            Log.e("CountlyDisplay", "showNavigationBar: " + resources.getBoolean(resourceId));
        } catch (Resources.NotFoundException e) {
        }

        boolean hasSoftKeys = true;
        boolean hasMenuKey = ViewConfiguration.get(this).hasPermanentMenuKey();
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
        hasSoftKeys = !hasMenuKey && !hasBackKey;
        Log.e("CountlyDisplay", "hasSoftKeys: " + hasSoftKeys);

        try {
            int resourceId = resources.getIdentifier("config_enableTranslucentDecor", "bool", "android");
            Log.e("CountlyDisplay", "config_enableTranslucentDecor: " + resources.getBoolean(resourceId));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "statusBarHeight: " + resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "navigationBarHeight: " + resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_height", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "navigationBarWidth: " + resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_width", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "statusBarHeightLandscape: " + resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height_landscape", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "navigationBarHeightLandscape: " + resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_height_landscape", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "navigationBarWidthLandscape: " + resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_width_landscape", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "statusBarHeightPortrait: " + resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height_portrait", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "navigationBarHeightPortrait: " + resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_height_portrait", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "navigationBarWidthPortrait: " + resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_width_portrait", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        try {
            Log.e("CountlyDisplay", "statusBarWidth: " + resources.getDimensionPixelSize(resources.getIdentifier("status_bar_width", "dimen", "android")));
        } catch (Resources.NotFoundException e) {
        }

        //Countly.sharedInstance().content().openForContent();

        Display d = getWindowManager().getDefaultDisplay();

        DisplayMetrics realDisplayMetrics = new DisplayMetrics();
        d.getRealMetrics(realDisplayMetrics);

        int realHeight = realDisplayMetrics.heightPixels;
        int realWidth = realDisplayMetrics.widthPixels;
        Log.e("CountlyDisplay", "realWidth: " + realWidth + " realHeight: " + realHeight);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        d.getMetrics(displayMetrics);

        int displayHeight = displayMetrics.heightPixels;
        int displayWidth = displayMetrics.widthPixels;
        Log.e("CountlyDisplay", "displayWidth: " + displayWidth + " displayHeight: " + displayHeight);
        hasSoftKeys = realWidth - displayWidth > 0 || realHeight - displayHeight > 0;

        Log.e("CountlyDisplay", "hasSoftKeys: " + hasSoftKeys);

        Countly.sharedInstance().contents().openForContent();
    }

    public void onClickExitContents(View v) {
        Countly.sharedInstance().contents().exitFromContent();
    }
}
