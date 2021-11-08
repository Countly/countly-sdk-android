package ly.count.android.sdk.messaging;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyStore;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Messaging support module.
 */

public class ModulePush {

    public static final String PUSH_EVENT_ACTION = "[CLY]_push_action";
    public static final String PUSH_EVENT_ACTION_ID_KEY = "i";
    public static final String PUSH_EVENT_ACTION_INDEX_KEY = "b";
    static final String KEY_ID = "c.i";
    static final String KEY_TITLE = "title";
    static final String KEY_MESSAGE = "message";
    static final String KEY_SOUND = "sound";
    static final String KEY_BADGE = "badge";
    static final String KEY_LINK = "c.l";
    static final String KEY_MEDIA = "c.m";
    static final String KEY_BUTTONS = "c.b";
    static final String KEY_BUTTONS_TITLE = "t";
    static final String KEY_BUTTONS_LINK = "l";

    static class MessageImpl implements CountlyPush.Message {
        final String id;
        private final String title, message, sound;
        private final Integer badge;
        private final Uri link;
        private final URL media;
        private final List<CountlyPush.Button> buttons;
        private final Map<String, String> data;

        static class Button implements CountlyPush.Button {
            private final CountlyPush.Message message;
            private final int index, icon;
            private final String title;
            private final Uri link;

            Button(CountlyPush.Message message, int index, String title, Uri link) {
                this.message = message;
                this.index = index;
                this.title = title;
                this.link = link;
                this.icon = 0;
            }

            Button(CountlyPush.Message message, int index, String title, Uri link, int icon) {
                this.message = message;
                this.index = index;
                this.title = title;
                this.link = link;
                this.icon = icon;
            }

            @Override
            public int index() {
                return index;
            }

            @Override
            public String title() {
                return title;
            }

            @Override
            public Uri link() {
                return link;
            }

            @Override
            public void recordAction(Context context) {
                message.recordAction(context, index);
            }

            @Override
            public int icon() {
                return icon;
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof Button)) {
                    return false;
                }
                Button b = (Button) obj;
                return b.index == index && (b.title == null ? title == null : b.title.equals(title)) && (b.link == null ? link == null : b.link.equals(link) && b.icon == icon);
            }
        }

        MessageImpl(Map<String, String> data) {
            this.data = data;
            this.id = data.get(KEY_ID);
            this.title = data.get(KEY_TITLE);
            this.message = data.get(KEY_MESSAGE);
            this.sound = data.get(KEY_SOUND);

            Countly.sharedInstance().L.d("[MessageImpl] constructed: " + id);
            Integer b = null;
            try {
                b = data.containsKey(KEY_BADGE) ? Integer.parseInt(data.get(KEY_BADGE)) : null;
            } catch (NumberFormatException e) {
                Countly.sharedInstance().L.w("[MessageImpl] Bad badge value received, ignoring");
            }
            this.badge = b;

            Uri uri = null;
            if (data.get(KEY_LINK) != null) {
                try {
                    uri = Uri.parse(data.get(KEY_LINK));
                } catch (Throwable e) {
                    Countly.sharedInstance().L.w("[MessageImpl] Cannot parse message link", e);
                }
            }
            this.link = uri;

            URL u = null;
            try {
                u = data.containsKey(KEY_MEDIA) ? new URL(data.get(KEY_MEDIA)) : null;
            } catch (MalformedURLException e) {
                Countly.sharedInstance().L.w("[MessageImpl] Bad media value received, ignoring");
            }
            this.media = u;

            this.buttons = new ArrayList<>();

            String json = data.get(KEY_BUTTONS);
            if (json != null) {
                try {
                    JSONArray array = new JSONArray(json);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject btn = array.getJSONObject(i);
                        if (btn.has(KEY_BUTTONS_TITLE) && btn.has(KEY_BUTTONS_LINK)) {
                            uri = null;
                            if (btn.getString(KEY_BUTTONS_LINK) != null) {
                                try {
                                    uri = Uri.parse(btn.getString(KEY_BUTTONS_LINK));
                                } catch (Throwable e) {
                                    Countly.sharedInstance().L.w("[MessageImpl] Cannot parse message link", e);
                                }
                            }

                            this.buttons.add(new Button(this, i + 1, btn.getString(KEY_BUTTONS_TITLE), uri));
                        }
                    }
                } catch (Throwable e) {
                    Countly.sharedInstance().L.w("[MessageImpl] Failed to parse buttons JSON", e);
                }
            }
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public String message() {
            return message;
        }

        @Override
        public String sound() {
            return sound;
        }

        @Override
        public Integer badge() {
            return badge;
        }

        @Override
        public Uri link() {
            return link;
        }

        @Override
        public URL media() {
            return media;
        }

        @Override
        public List<CountlyPush.Button> buttons() {
            return buttons;
        }

        @Override
        public Set<String> dataKeys() {
            return data.keySet();
        }

        @Override
        public boolean has(String key) {
            return data.containsKey(key);
        }

        @Override
        public String data(String key) {
            return data.get(key);
        }

        @Override
        public void recordAction(Context context) {
            recordAction(context, 0);
        }

        @Override
        public void recordAction(Context context, int buttonIndex) {
            if (Countly.sharedInstance().isInitialized()) {
                Map<String, Object> map = new HashMap<>();
                map.put(PUSH_EVENT_ACTION_ID_KEY, id);
                map.put(PUSH_EVENT_ACTION_INDEX_KEY, String.valueOf(buttonIndex));
                Countly.sharedInstance().events().recordEvent(PUSH_EVENT_ACTION, map, 1);
            } else {
                //we're not initialised, cache the data
                CountlyStore.cachePushData(id, String.valueOf(buttonIndex), context);
            }
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public int describeContents() {
            return id.hashCode();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeMap(data);
            Countly.sharedInstance().L.d("[MessageImpl] written: " + data.get(KEY_ID));
        }

        public static final Parcelable.Creator<MessageImpl> CREATOR = new Parcelable.Creator<MessageImpl>() {

            public MessageImpl createFromParcel(Parcel in) {
                Map<String, String> map = new HashMap<>();
                in.readMap(map, ClassLoader.getSystemClassLoader());
                Countly.sharedInstance().L.d("[MessageImpl] read: " + map.get(KEY_ID));
                return new MessageImpl(map);
            }

            public MessageImpl[] newArray(int size) {
                return new MessageImpl[size];
            }
        };
    }
}
