package ly.count.android.sdk.messaging;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Countly Messaging service message representation.
 */
public class Message implements Parcelable {
    private static final String TAG = "Countly|Message";

    static final class Button {
        int index;
        String title;
        String link;
    }

    private Bundle data;
    private int type;
    private List<Button> buttons;

    public Message(Bundle data) {
        this.data = data;
        this.readButtons();
        this.type = setType();
    }

    private static Map<String, Object> dataStore = new HashMap<>();

    private void readButtons() {
        this.buttons = new ArrayList<>();

        String json = data.getString("c.b");
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject btn = array.getJSONObject(i);
                    if (btn.has("t") && btn.has("l")) {
                        Button button = new Button();
                        button.index = i + 1;
                        button.title = btn.getString("t");
                        button.link = btn.getString("l");
                        this.buttons.add(button);
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public String getId() { return data.getString("c.i"); }
    public String getLink() { return data.getString("c.l"); }
    public String getMessage() { return data.getString("message"); }
    public String getTitle() { return data.getString("title"); }
    public String getMedia() { return data.getString("c.m"); }
    public List<Button> getButtons() { return buttons; }
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

        if (getTitle() != null && !"".equals(getTitle())) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_TITLE;
        }

        if (getMedia() != null && !"".equals(getMedia())) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_MEDIA;
        }

        if (getButtons() != null && getButtons().size() > 0) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_BUTTONS;
        }

        if (getLink() != null && !"".equals(getLink())) {
            t |= CountlyMessaging.NOTIFICATION_TYPE_URL;
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
    public boolean hasMessage() { return (type & CountlyMessaging.NOTIFICATION_TYPE_MESSAGE) > 0; }
    public boolean hasTitle() { return (type & CountlyMessaging.NOTIFICATION_TYPE_TITLE) > 0; }
    public boolean hasMedia() { return (type & CountlyMessaging.NOTIFICATION_TYPE_MEDIA) > 0; }
    public boolean hasButtons() { return (type & CountlyMessaging.NOTIFICATION_TYPE_BUTTONS) > 0; }
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
        } else if (hasMessage()) {
            if (activityClass == null) {
                activityClass = CountlyMessaging.getMainActivityClass(context);
            }
            if (activityClass != null) {
                Intent intent = new Intent(context, activityClass);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                return intent;
            }
        }
        return null;
    }

    public String getNotificationTitle(Context context) {
        return hasTitle() ? getTitle() : CountlyMessaging.getAppTitle(context);
    }

    /**
     * @return Message for Notification or AlertDialog
     */
    public String getNotificationMessage() {
        if (hasLink()) {
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
        data = in.readBundle(getClass().getClassLoader());
        this.readButtons();
        type = setType();
    }

    void prepare(final Runnable callback) {
        if (hasMedia()) {
            new AsyncTask<Void, Void, Void>(){
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        URL url = new URL(getMedia());
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setDoInput(true);
                        connection.connect();
                        InputStream input = connection.getInputStream();
                        dataStore.put(getMedia(), BitmapFactory.decodeStream(input));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    callback.run();
                    return null;
                }
            }.doInBackground();
        } else {
            callback.run();
        }
    }

    static Object getFromStore(String key) {
        return dataStore.get(key);
    }

    static Object removeFromStore(String key) {
        return dataStore.remove(key);
    }
}
