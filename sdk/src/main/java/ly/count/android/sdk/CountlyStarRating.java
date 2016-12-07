package ly.count.android.sdk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

/**
 * Created by Arturs on 29.11.2016..
 */
public class CountlyStarRating {

    public interface RatingCallback {
        void OnRate(int rating);
        void OnDismiss();
    }

    public static void ShowStarRating(Context context, final CountlyStarRating.RatingCallback callback){
        ShowStarRatingCustom(context, "App rating", "Please rate this app", "Cancel", callback);
    }

    public static void ShowStarRatingCustom(
            final Context context,
            final String title,
            final String message,
            final String cancelText,
            final CountlyStarRating.RatingCallback callback) {

        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        View dialoglayout = inflater.inflate(R.layout.star_rating_layout, null);
        RatingBar ratingBar = (RatingBar) dialoglayout.findViewById(R.id.ratingBar);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setView(dialoglayout)
                .setPositiveButton(cancelText, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(callback != null) {
                            callback.OnDismiss();
                        }
                    }
                });

        final AlertDialog dialog = builder.show();

        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                Log.d("SDADSDSD", "" + v);
                int rating = (int)v;
                Countly.sharedInstance().getConnectionQueue().sendStarRating(rating, context);
                dialog.cancel();
                if(callback != null) {
                    callback.OnRate(rating);
                }
            }
        });
    }
}
