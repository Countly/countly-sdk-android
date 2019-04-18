package ly.count.android.sdknative;

import android.text.TextUtils;
import android.util.Log;

public class CountlyNative {
    private static String TAG = "Countly";

    static boolean loadBreakpadSuccess = false;

    static {
        try {
            System.loadLibrary("breakpad");
            loadBreakpadSuccess = true;
            Log.d(TAG, "breakpad loaded.");
        } catch (Exception e) {
            loadBreakpadSuccess = false;
            Log.e(TAG, "fail to load breakpad");
        }
    }

    /**
     * init breakpad
     * @param dumpFileDir the directory of dump file
     * @return true: init success  false: init fail
     */
    public static boolean initBreakpad(String dumpFileDir){
        if (TextUtils.isEmpty(dumpFileDir)) {
            Log.e(TAG, "dumpFileDir can not be empty");
            return false;
        }
        if (loadBreakpadSuccess) {
            return init(dumpFileDir) > 0 ;
        }
        return false;
    }

    public static void crash() {
        testCrash();
    }
    private static native int init(String dumpFileDir);
    private static native int testCrash();
}
