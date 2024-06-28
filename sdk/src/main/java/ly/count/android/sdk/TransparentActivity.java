package ly.count.android.sdk;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import org.jetbrains.annotations.NotNull;

public class TransparentActivity extends Activity {

    static final String ORIENTATION_KEY = "orientation";
    static final String ANIMATION_KEY = "animation";
    static final String URI_KEY = "uri";

    int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
    int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;

    int closeIconWidth = 50;
    int closeIconHeight = 50;

    private Runnable myRunnable;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint({ "SetJavaScriptEnabled", "WrongConstant" })
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        // Get extras
        int orientation = getIntent().getIntExtra(ORIENTATION_KEY, 0);
        int animation = getIntent().getIntExtra(ANIMATION_KEY, 0);
        String uri = getIntent().getStringExtra(URI_KEY);

        ActivityLayouts.BaseLayout layout = getLayout(orientation);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.gravity = Gravity.TOP;
        params.x = layout.mainX;
        params.y = layout.mainY;
        params.height = layout.layoutHeight;
        params.width = layout.layoutWidth;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        getWindow().setAttributes(params);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        RelativeLayout relativeLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(layout.layoutWidth, layout.layoutHeight);
        relativeLayout.setLayoutParams(layoutParams);

        WebView webView = new WebView(this);
        RelativeLayout.LayoutParams webLayoutParams = new RelativeLayout.LayoutParams(layout.webViewWidth, layout.webViewHeight);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        webLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        webView.setLayoutParams(webLayoutParams);

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(uri);

        // Close icon
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        RelativeLayout.LayoutParams closeButtonParams = new RelativeLayout.LayoutParams(closeIconWidth, closeIconHeight);
        closeButtonParams.topMargin = layout.iconTopMargin;
        closeButtonParams.rightMargin = layout.iconEndMargin;
        closeButtonParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        closeButtonParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        imageView.setLayoutParams(closeButtonParams);
        // Add close action
        imageView.setOnClickListener(view -> finish());

        // Add views
        relativeLayout.addView(webView);
        relativeLayout.addView(imageView);
        setContentView(relativeLayout);

        switch (animation) {
            case ActivityConstants.Animate.TOP_TO_CENTER: {
                Animation animate = new TranslateAnimation(0, 0, screenHeight, 0);
                animate.setInterpolator(new DecelerateInterpolator());
                animate.setDuration(700);
                animate.setFillAfter(false);
                webView.startAnimation(animate);
                break;
            }
            case ActivityConstants.Animate.BOTTOM_BAR_TO_PADDED_BOTTOM_HALF: {
                Animation animate = new TranslateAnimation(0, 0, screenHeight, 0);
                animate.setInterpolator(new AccelerateDecelerateInterpolator());
                animate.setDuration(400);
                animate.setFillAfter(false);
                webView.startAnimation(animate);

                // Set up for the next loop animation
                myRunnable = () -> {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.setDuration(200);
                    animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());

                    ValueAnimator translateAnimator = ValueAnimator.ofFloat(-screenHeight, 0);
                    translateAnimator.setDuration(200);
                    translateAnimator.addUpdateListener(valueAnimator -> webView.setTranslationY((float) valueAnimator.getAnimatedValue()));

                    animatorSet.play(translateAnimator);
                    animatorSet.start();

                    handler.postDelayed(myRunnable, 500);
                };
                handler.postDelayed(myRunnable, 500);
                break;
            }
            default:
                break;
        }
    }

    @NotNull private ActivityLayouts.BaseLayout getLayout(int orientation) {
        ActivityLayouts.BaseLayout layout;

        switch (orientation) {
            case ActivityConstants.Orientation.TOP_HALF:
                layout = new ActivityLayouts.TopHalfLayout();
                break;
            case ActivityConstants.Orientation.BOTTOM_HALF:
                layout = new ActivityLayouts.BottomHalfLayout();
                break;
            case ActivityConstants.Orientation.TOP_BAR:
                layout = new ActivityLayouts.TopBarLayout(ActivityConstants.barHeightPercentage, ActivityConstants.barWidthPercentage);
                break;
            case ActivityConstants.Orientation.BOTTOM_BAR:
                layout = new ActivityLayouts.BottomBarLayout(ActivityConstants.barHeightPercentage, ActivityConstants.barWidthPercentage);
                break;
            case ActivityConstants.Orientation.CENTER_PADDED:
                layout = new ActivityLayouts.CenterPaddedLayout(ActivityConstants.barHeightPercentage, ActivityConstants.barWidthPercentage);
                break;
            case ActivityConstants.Orientation.CENTER_WHOLE:
                layout = new ActivityLayouts.CenterWholeLayout();
                break;
            case ActivityConstants.Orientation.BOTTOM_RIGHT_QUARTER:
                layout = new ActivityLayouts.BRQLayout(ActivityConstants.quarterHeightPercentage, ActivityConstants.quarterWidthPercentage);
                break;
            case ActivityConstants.Orientation.BOTTOM_LEFT_QUARTER:
                layout = new ActivityLayouts.BLQLayout(ActivityConstants.quarterHeightPercentage, ActivityConstants.quarterWidthPercentage);
                break;
            case ActivityConstants.Orientation.TOP_LEFT_QUARTER:
                layout = new ActivityLayouts.TLQLayout(ActivityConstants.quarterHeightPercentage, ActivityConstants.quarterWidthPercentage);
                break;
            case ActivityConstants.Orientation.TOP_RIGHT_QUARTER:
                layout = new ActivityLayouts.TRQLayout(ActivityConstants.quarterHeightPercentage, ActivityConstants.quarterWidthPercentage);
                break;
            default:
                layout = new ActivityLayouts.BaseLayout();
                break;
        }

        layout.applyValues(screenHeight, screenWidth, closeIconHeight, closeIconWidth);
        return layout;
    }

    /**
     * Show the widget
     *
     * @param context The context
     * @param animate The animation
     * @param orientation The orientation
     * @param url The URL
     */
    public static void showActivity(@NonNull Context context, int animate, int orientation, @NonNull String url) {
        assert context != null;
        assert url != null && !url.isEmpty();
        assert animate >= 0;
        assert orientation >= 0;

        Intent intent = new Intent(context, TransparentActivity.class);
        intent.putExtra(ANIMATION_KEY, animate);
        intent.putExtra(ORIENTATION_KEY, orientation);
        intent.putExtra(URI_KEY, url);
        context.startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myRunnable != null) {
            handler.removeCallbacks(myRunnable);
        }
    }
}
