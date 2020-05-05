package ly.count.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.RatingBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import static android.content.Context.UI_MODE_SERVICE;

public class CountlyStarRating {

    /**
     * Callbacks for star rating dialog
     */
    //@Deprecated use StarRatingCallback
    public interface RatingCallback {
        void onRate(int rating);

        void onDismiss();
    }

    /**
     * Used for callback to developer from calling the Rating widget
     */
    //@Deprecated
    public interface FeedbackRatingCallback {
        /**
         * Called after trying to show a rating dialog popup
         *
         * @param error if is null, it means that no errors were encountered
         */
        void callback(String error);
    }
}
