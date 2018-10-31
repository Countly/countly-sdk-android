package ly.count.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import ly.count.android.sdk.Countly;

/**
 * Demo Activity explaining how to record {@link ly.count.android.sdk.Event}s.
 */

public class ActivityExampleCustomEvents extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_custom_events);
    }

    public void onClickRecordEventProfileView(View v) {
        Countly.session(this).event("ProfileView").record();
    }

    public void onClickRecordEventFriendRequest(View v) {
        Countly.session(this).event("FriendRequest").setCount(3).record();
    }

    public void onClickRecordEventAddToCartWithCountSum(View v) {
        Countly.session(this).event("AddToCart").setCount(3).setSum(134).record();
    }

    public void onClickRecordEventInCheckout(View v) {
        Countly.session(this).event("InCheckout").setCount(1).setDuration(55).record();
    }

    public void onClickRecordEventProductView(View v) {
        Countly.session(this).event("ProductView").addSegment("category", "pants").record();
    }

    public void onClickRecordEventProductViewWithCount(View v) {
        Countly.session(this).event("ProductView").addSegment("category", "jackets").setCount(15).record();
    }

    public void onClickRecordEventAddToCartWithCategoryCountSum(View v) {
        Countly.session(this).event("AddToCart").addSegment("category", "jackets").setCount(25).setSum(10).record();
    }

    public void onClickRecordEventAddToCartWithCategoryCountSumDur(View v) {
        Countly.session(this).event("AddToCart").addSegment("category", "jackets").setCount(25).setSum(10).setDuration(50).record();
    }

    public void onClickStartTimedEvent(View v) {
        Countly.session(this).timedEvent("TimedEvent");
    }

    public void onClickEndTimedEvent(View v) {
        Countly.session(this).timedEvent("TimedEvent").endAndRecord();
    }

    public void onClickEndTimedEventWithData(View v) {
        Countly.session(this).timedEvent("TimedEvent").addSegment("wall", "orange").setCount(4).setSum(34).endAndRecord();
    }
}
