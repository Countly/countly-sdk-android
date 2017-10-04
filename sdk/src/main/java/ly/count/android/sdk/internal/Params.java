package ly.count.android.sdk.internal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Object for application/x-www-form-urlencoded string building and manipulation
 */

class Params {
    private StringBuilder params;

    static final class Obj {
        private final String key;
        private final JSONObject json;
        private final Params params;

        Obj(String key, Params params) {
            this.params = params;
            this.key = key;
            this.json = new JSONObject();
        }

        // TODO: previous implementation omitted null & empty string values, check for correctness
        public Obj put(String key, Object value) {
            try {
                if (value instanceof Double) {
                    Double v = (Double) value;
                    if (v.isInfinite() || v.isNaN()) {
                        value = null;
                    }
                }
                json.put(key, value);
            } catch (JSONException e) {
                Log.wtf("Cannot put property into Params.Obj", e);
            }
            return this;
        }

        public Params add(){
            params.add(key, json.toString());
            return params;
        }
    }

    static final class Arr {
        private final String key;
        private final Collection<String> json;
        private final Params params;

        Arr(String key, Params params) {
            this.params = params;
            this.key = key;
            this.json = new ArrayList<>();
        }

        public Arr put(JSONable value) {
            json.add(value.toJSON());
            return this;
        }

        public Arr put(Collection collection) {
            for (Object value : collection) if (value instanceof JSONable) {
                json.add(((JSONable)value).toJSON());
            }
            return this;
        }

        public Params add() {
            if (json.size() > 0) {
                params.add(key, "[" + Utils.join(json, ",") + "]");
            } else {
                params.add(key, "[]");
            }
            return params;
        }
    }

    Params(Object... objects) {
        params = new StringBuilder();
        if (objects != null && objects.length == 1 && (objects[0] instanceof Object[])) {
            addObjects((Object[]) objects[0]);
        } else {
            addObjects(objects);
        }
    }

    Params(String params) {
        this.params = new StringBuilder(params);
    }

    Params() {
        this.params = new StringBuilder();
    }

    Params add(Object... objects) {
        return addObjects(objects);
    }

    Params add(String key, Object value) {
        if (params.length() > 0) {
            params.append("&");
        }
        params.append(key).append("=");
        if (value != null) {
            params.append(Utils.urlencode(value.toString()));
        }
        return this;
    }

    Params add(Params params) {
        if (params == null || params.length() == 0) {
            return this;
        }
        if (this.params.length() > 0) {
            this.params.append("&");
        }
        this.params.append(params.toString());
        return this;
    }

    Params add(String string) {
        if (params != null) {
            this.params.append(string);
        }
        return this;
    }

    Obj obj(String key) {
       return new Obj(key, this);
    }

    Arr arr(String key) {
       return new Arr(key, this);
    }

    String remove(String key) {
        List<String> pairs = new ArrayList<>(Arrays.asList(params.toString().split("&")));
        for (String pair : pairs) {
            String comps[] = pair.split("=");
            if (comps.length == 2 && comps[0].equals(key)) {
                pairs.remove(pair);
                this.params = new StringBuilder(Utils.join(pairs, "&"));
                return comps[1];
            }
        }
        return null;
    }

    String get(String key) {
        if (params.indexOf(key + "=") == -1) {
            return null;
        }
        String[] pairs = params.toString().split("&");
        for (String pair : pairs) {
            String comps[] = pair.split("=");
            if (comps.length == 2 && comps[0].equals(key)) {
                return comps[1];
            }
        }
        return null;
    }

    //todo can this receive only an even amount of objects? maybe return an error if an odd amount is returned?
    private Params addObjects(Object[] objects) {
        for (int i = 0; i < objects.length; i += 2) {
            add(objects[i] == null ? ("unknown" + i) : objects[i].toString(), objects.length > i + 1 ? objects[i + 1] : null);
        }
        return this;
    }

    int length() {
        return params.length();
    }

    void clear() {
        params = new StringBuilder();
    }

    public String toString(){
        return params.toString();
    }

    @Override
    public int hashCode() {
        return params.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Params)) {
            return false;
        }
        Params p = (Params)obj;

        return p.params.toString().equals(params.toString());
    }
}
