package ly.count.android.sdk.internal;

import android.app.Activity;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ly.count.android.sdk.Config;
import ly.count.android.sdk.CountlyPush;

/**
 * Messaging support module.
 */

public class ModulePush extends ModuleBase {
    private static final Log.Module L = Log.module("ModulePush");

    public static final String PUSH_EVENT_ACTION  = "[CLY]_push_action";
    public static final String PUSH_EVENT_ACTION_INDEX_KEY  = "i";

    public static final String KEY_ID = "c.i";
    public static final String KEY_TITLE = "title";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_SOUND = "sound";
    public static final String KEY_BADGE = "badge";
    public static final String KEY_LINK = "c.l";
    public static final String KEY_MEDIA = "c.m";
    public static final String KEY_BUTTONS = "c.b";
    public static final String KEY_BUTTONS_TITLE = "t";
    public static final String KEY_BUTTONS_LINK = "l";

    private String localeSent = null;

    static class MessageImpl implements CountlyPush.Message {
        final String id;
        private final String title, message, sound;
        private final Integer badge;
        private final URL link, media;
        private final List<CountlyPush.Button> buttons;
        private final Map<String, String> data;

        static class Button implements CountlyPush.Button {
            private final CountlyPush.Message message;
            private final int index;
            private final String title;
            private final URL link;

            Button(CountlyPush.Message message, int index, String title, URL link) {
                this.message = message;
                this.index = index;
                this.title = title;
                this.link = link;
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
            public URL link() {
                return link;
            }

            @Override
            public void recordAction(android.content.Context context) {
                message.recordAction(context, index);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == null || !(obj instanceof Button)) { return false; }
                Button b = (Button)obj;
                return b.index == index && (b.title == null ? title == null : b.title.equals(title)) && (b.link == null ? link == null : b.link.equals(link));
            }
        }

        MessageImpl(Map<String, String> data) {
            this.data = data;
            this.id = data.get(KEY_ID);
            this.title = data.get(KEY_TITLE);
            this.message = data.get(KEY_MESSAGE);
            this.sound = data.get(KEY_SOUND);

            Integer b = null;
            try {
                b = data.containsKey(KEY_BADGE) ? Integer.parseInt(data.get(KEY_BADGE)) : null;
            } catch (NumberFormatException e) {
                L.w("Bad badge value received, ignoring");
            }
            this.badge = b;

            URL u = null;
            try {
                u = data.containsKey(KEY_LINK) ? new URL(data.get(KEY_LINK)) : null ;
            } catch (MalformedURLException e) {
                L.w("Bad link value received, ignoring");
            }
            this.link = u;

            u = null;
            try {
                u = data.containsKey(KEY_MEDIA) ? new URL(data.get(KEY_MEDIA)) : null ;
            } catch (MalformedURLException e) {
                L.w("Bad media value received, ignoring");
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
                            u = null;
                            try {
                                u = new URL(btn.getString(KEY_BUTTONS_LINK));
                            } catch (MalformedURLException e) {
                                L.w("Bad button link value received, ignoring");
                            }
                            if (u != null) {
                                this.buttons.add(new Button(this, i, btn.getString(KEY_BUTTONS_TITLE), u));
                            }
                        }
                    }
                } catch (Throwable e) {
                    L.w("Failed to parse buttons JSON", e);
                }
            }

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
        public URL link() {
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
        public void recordAction(android.content.Context context) {
            recordAction(context, 0);
        }

        @Override
        public void recordAction(android.content.Context context, int buttonIndex) {
            Core.instance.sessionLeadingOrNew(context)
                    .event(PUSH_EVENT_ACTION)
                    .addSegment(PUSH_EVENT_ACTION_INDEX_KEY, String.valueOf(buttonIndex))
                    .record();
        }

        @Override
        public int describeContents() {
            return id.hashCode();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeMap(data);
        }

        public static final Parcelable.Creator<MessageImpl> CREATOR = new Parcelable.Creator<MessageImpl>() {

            public MessageImpl createFromParcel(Parcel in) {
                Map<String, String> map = new HashMap<>();
                in.readMap(map, ClassLoader.getSystemClassLoader());
                return new MessageImpl(map);
            }

            public MessageImpl[] newArray(int size) {
                return new MessageImpl[size];
            }
        };
    }

    static final String FIREBASE_MESSAGING_CLASS = "com.google.firebase.messaging.FirebaseMessaging";

    private InternalConfig config;

    @Override
    @SuppressWarnings("unchecked")
    public void init(InternalConfig config) throws IllegalStateException{
        this.config = config;

        if (!Utils.reflectiveClassExists(FIREBASE_MESSAGING_CLASS)) {
            throw new IllegalStateException("No Firebase messaging library in class path. Please either add it to your gradle config or disable Push feature.");
        }

        if (config.getPushActivityClass() != null && !config.isLimited()) {
            try {
                CountlyPush.pushActivityClass = (Class<? extends Activity>) Class.forName(config.getPushActivityClass());
            } catch (Throwable e) {
                L.wtf("No class found for push activity class name " + config.getPushActivityClass(), e);
            }
        }
    }

    @Override
    public void onActivityStarted(Context ctx) {
        if (CountlyPush.pushActivityClass == null && !config.isLimited()) {
            CountlyPush.pushActivityClass = ctx.getActivity().getClass();
        }
    }

    @Override
    public void onActivityStopped(Context ctx) {
        if (config.getPushActivityClass() == null && !config.isLimited()) {
            CountlyPush.pushActivityClass = null;
        }
    }

    @Override
    public void onContextAcquired(final Context ctx) {
        if (config.getDeviceId(Config.DeviceIdRealm.FCM_TOKEN) == null) {
            Core.instance.acquireId(ctx, new Config.DID(Config.DeviceIdRealm.FCM_TOKEN, Config.DeviceIdStrategy.INSTANCE_ID, null), false, new Tasks.Callback<Config.DID>() {
                @Override
                public void call(Config.DID did) throws Exception {
                    if (did == null) {
                        L.w("Couldn't acquire FCM token, messaging doesn't work yet");
                    } else {
                        L.i("Got FCM token: " + did.id);
                        Core.onDeviceId(ctx, did, null);
                    }
                }
            });
        }
    }

    @Override
    public void onDeviceId(Context ctx, final Config.DID deviceId, Config.DID oldDeviceId) {
        if (deviceId != null && deviceId.realm == Config.DeviceIdRealm.FCM_TOKEN) {
            localeSent = Device.getLocale();
            ModuleRequests.injectParams(ctx, new ModuleRequests.ParamsInjector() {
                @Override
                public void call(Params params) {
                    params.add("token_session", 1,
                            "locale", localeSent,
                            "android_token", deviceId.id,
                            "test_mode", config.isTestModeEnabled() ? 2 : 0);
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(Context ctx) {
        // send updated locale in case it has been changed
        if (Utils.isNotEqual(localeSent, Device.getLocale())) {
            ModuleRequests.injectParams(ctx, new ModuleRequests.ParamsInjector() {
                @Override
                public void call(Params params) {
                    params.add("token_session", 1, "locale", Device.getLocale());
                }
            });
        }
    }

    static CountlyPush.Message decodeMessage(Map<String, String> data) {
        MessageImpl message = new MessageImpl(data);
        return message.id == null ? null : message;
    }

    static void subscribe(String topic) {
        if (Utils.isEmpty(topic)) {
            L.wtf("Topic cannot be null or empty");
        } else {
//            eFgsAAZPfIU
            Core.instance.user().edit().addToCohort(topic).commit();

            Object instance = Utils.reflectiveCall(FIREBASE_MESSAGING_CLASS, null, "getInstance");
            if (instance != null && instance != Boolean.FALSE) {
                Object result = Utils.reflectiveCall(null, instance, "subscribeToTopic", topic);
                if (result == Boolean.FALSE) {
                    L.w("Couldn't subscribe to Firebase topic on firebase, but still adding user to this cohort");
                }
            } else {
                L.w("Couldn't subscribe to Firebase topic on firebase (getInstance returned " + instance + "), but still adding user to this cohort");
            }
        }
    }

    static void unsubscribe(String topic) {
        if (Utils.isEmpty(topic)) {
            L.wtf("Topic cannot be null or empty");
        } else {
            Core.instance.user().edit().removeFromCohort(topic).commit();

            Object instance = Utils.reflectiveCall(FIREBASE_MESSAGING_CLASS, null, "getInstance");
            if (instance != null && instance != Boolean.FALSE) {
                Object result = Utils.reflectiveCall(null, instance, "unsubscribeFromTopic", topic);
                if (result == Boolean.FALSE) {
                    L.w("Couldn't unsubscribe from Firebase topic on firebase, but still removing this cohort from the user");
                }
            } else {
                L.w("Couldn't unsubscribe from Firebase topic on firebase (getInstance returned " + instance + "), but still removing this cohort from the user");
            }
        }
    }
}
