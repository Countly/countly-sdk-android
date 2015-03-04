package ly.count.android.sdk.messaging;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Countly Messaging service message representation.
 */
public class Message implements Parcelable {
    private static final String TAG = "Countly|Message";

    private Bundle data;
    private int type;

    public Message(Bundle data) {
        this.data = data;
        this.type = setType();
    }

    public String getId() { return data.getString("c.i"); }
    public String getLink() { return data.getString("c.l"); }
    public String getReview() { return data.getString("c.r"); }
    public String getMessage() { return data.getString("message"); }
    public String getSoundUri() { return data.getString("sound"); }
    public Bundle getData() { return data; }
    public int getType() { return type; }

    /**
     * Depending on message contents, it can represent different types of actions.
     * @return message type according to message contents.
     */
    private int setType() {
        int t = CountlyMessaging.NOTIFICATION_TYPE_UNKNOWN;

        if (getMessage() != null && !"".equals(getMessage())) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_MESSAGE;
        }

        if (getLink() != null && !"".equals(getLink())) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_URL;
        }

        if (getReview() != null && !"".equals(getReview())) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_REVIEW;
        }

        if ("true".equals(data.getString("c.s"))) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_SILENT;
        }

        if (getSoundUri() != null && !"".equals(getSoundUri())) {
            if ("default".equals(getSoundUri())) t |= CountlyMessaging.NOTIFICATION_TYPE_SOUND_DEFAULT;
            else t |= CountlyMessaging.NOTIFICATION_TYPE_SOUND_URI;
        }

        return t;
    }

    public boolean hasLink() { return (type & CountlyMessaging.NOTIFICATION_TYPE_URL) > 0; }
    public boolean hasReview() { return (type & CountlyMessaging.NOTIFICATION_TYPE_REVIEW) > 0; }
    public boolean hasMessage() { return (type & CountlyMessaging.NOTIFICATION_TYPE_MESSAGE) > 0; }
    public boolean isSilent() { return (type & CountlyMessaging.NOTIFICATION_TYPE_SILENT) > 0; }
    public boolean hasSoundUri() { return (type & CountlyMessaging.NOTIFICATION_TYPE_SOUND_URI) > 0; }
    public boolean hasSoundDefault() { return (type & CountlyMessaging.NOTIFICATION_TYPE_SOUND_DEFAULT) > 0; }
    public boolean isUnknown() { return type == CountlyMessaging.NOTIFICATION_TYPE_UNKNOWN; }

    /**
     * Message is considered valid only when it has Countly ID and its type is determined
     * @return whether this message is valid or not
     */
    public boolean isValid() {
        String id = data.getString("c.i");
        return !isUnknown() && id != null && id.length() == 24;
    }

    /**
     * Depending on message contents, different intents can be run.
     * @return Intent
     */
    public Intent getIntent(Context context, Class <? extends Activity> activityClass) {
        if (hasLink()) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse(getLink()));
        } else if (hasReview()) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
        } else if (hasMessage()) {
            Intent intent = new Intent(context, activityClass);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return intent;
        }
        return null;
    }

    public String getNotificationTitle(Context context) {
        return CountlyMessaging.getAppTitle(context);
    }

    /**
     * @return Message for Notification or AlertDialog
     */
    public String getNotificationMessage() {
        if (hasLink()) {
            return hasMessage() ? getMessage() : "";
        } else if (hasReview()) {
            return hasMessage() ? getMessage() : "";
        } else if (hasMessage()) {
            return getMessage();
        }
        return null;
    }

    @Override
    public String toString() {
        return data == null ? "empty" : data.toString();
    }

    @Override
    public int describeContents () {
        return 0;
    }

    @Override
    public void writeToParcel (Parcel dest, int flags) {
        dest.writeBundle(data);
    }
    public static final Creator<Message> CREATOR = new Creator<Message>() {
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    Message(Parcel in) {
        data = in.readBundle();
        type = setType();
    }
}
