package ly.count.android.sdknative;

import android.content.Context;
import android.util.Log;
import java.io.File;

public class CountlyNative {
    private static String TAG = "Countly";
    private static String countlyNativeCrashFolderPath;

    static boolean loadBreakpadSuccess = false;

    static {
        try {
            System.loadLibrary("countly_native");
            loadBreakpadSuccess = true;
            Log.d(TAG, "countly_native library loaded.");
        } catch (Exception e) {
            loadBreakpadSuccess = false;
            Log.e(TAG, "fail to load countly_native library");
        }
    }

    /**
     * init breakpad
     * @return true: init success  false: init fail
     */
    public static boolean initNative(Context cxt){
        // String basePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String basePath = cxt.getCacheDir().getAbsolutePath();
        String countlyFolderName = "Countly";
        String countlyNativeCrashFolderName = "CrashDumps";
        countlyNativeCrashFolderPath = basePath + File.separator + countlyFolderName + File.separator + countlyNativeCrashFolderName;

        File folder = new File(countlyNativeCrashFolderPath);
        if (!folder.exists()) {
            boolean res = folder.mkdirs();
        }
        if (loadBreakpadSuccess) {
            return init(countlyNativeCrashFolderPath) > 0 ;
        }
        return false;
    }

    public static void crash() {
        testCrash();
    }

    public static native String getBreakpadVersion();
    public static native String getBreakpadChecksum();

    private static native int init(String dumpFileDir);
    private static native int testCrash();
}
