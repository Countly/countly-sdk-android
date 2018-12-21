package ly.count.sdk.android.internal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RatingBar;

import java.util.HashMap;
import java.util.Map;

public class ModuleRating extends ly.count.sdk.internal.ModuleRating {
    /**
     * Callbacks for star rating dialog
     */
    public interface RatingCallback {
        void onRate(int rating);
        void onDismiss();
    }

    /**
     * Call to manually show star rating dialog
     * @param context
     * @param callback
     */
    public static void showStarRating(Context context, final ModuleRating.RatingCallback callback){
        //StarRatingPreferences srp = loadStarRatingPreferences(context);
        //showStarRatingCustom(context, srp.dialogTextTitle, srp.dialogTextMessage, srp.dialogTextDismiss, srp.isDialogCancellable, callback);
    }

    /**
     * Method that created the star rating dialog
     * @param context
     * @param title
     * @param message
     * @param cancelText
     * @param isCancellable
     * @param callback
     */
    public static void showStarRatingCustom(
            final Context context,
            final String title,
            final String message,
            final String cancelText,
            final boolean isCancellable,
            final ModuleRating.RatingCallback callback) {

        if(!(context instanceof Activity)) {
            L.e("Can't show star rating dialog, the provided context is not based off a activity");
            return;
        }
/*
        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        View dialogLayout = inflater.inflate(R.layout.star_rating_layout, null);
        RatingBar ratingBar = (RatingBar) dialogLayout.findViewById(R.id.ratingBar);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(isCancellable)
                .setView(dialogLayout)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if(callback != null) {
                            //call the dismiss callback ir the user clicks the back button or clicks outside the dialog
                            callback.onDismiss();
                        }
                    }
                })
                .setPositiveButton(cancelText, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(callback != null) {
                            //call the dismiss callback if the user clicks the "dismiss" button
                            callback.onDismiss();
                        }
                    }
                });

        final AlertDialog dialog = builder.show();

        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                int rating = (int) v;

                if(Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
                    Map<String, String> segm = new HashMap<>();
                    segm.put("platform", "android");
                    segm.put("app_version", DeviceInfo.getAppVersion(context));
                    segm.put("rating", "" + rating);

                    Countly.sharedInstance().recordEvent(STAR_RATING_EVENT_KEY, segm, 1);
                }

                dialog.dismiss();
                if(callback != null) {
                    callback.onRate(rating);
                }
            }
        });*/
    }
}
