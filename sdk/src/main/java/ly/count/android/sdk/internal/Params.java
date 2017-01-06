package ly.count.android.sdk.internal;

/**
 * Object for application/x-www-form-urlencoded string building and manipulation
 */

class Params {
    private StringBuilder params;

    Params(Object... objects) {
        params = new StringBuilder();
        if (objects.length == 1 && (objects[0] instanceof Object[])) {
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
        if (this.params.length() > 0) {
            this.params.append("&");
        }
        this.params.append(params.toString());
        return this;
    }

    private Params addObjects(Object[] objects) {
        for (int i = 0; i < objects.length; i += 2) {
            add(objects[i] == null ? ("unknown" + i) : objects[i].toString(), objects.length > i ? objects[i + 1] : null);
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
}
