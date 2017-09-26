package ly.count.android.sdk.internal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ly.count.android.sdk.User;
import ly.count.android.sdk.UserEditor;

class UserEditorImpl implements UserEditor {
    static class Op {
        static final String INC = "$inc";
        static final String MUL = "$mul";
        static final String MIN = "$min";
        static final String MAX = "$max";
        static final String SET_ONCE = "$setOnce";
        static final String PULL = "$pull";
        static final String PUSH = "$push";
        static final String PUSH_UNIQUE = "$addToSet";

        final String op;
        final String key;
        final Object value;

        Op(String op, String key, Object value) {
            this.op = op;
            this.key = key;
            this.value = value;
        }

        public void apply(Map<String, Object> custom) {
//            switch (op) {
//                case INC:
//                    Object existing = custom.get(key);
//                    if (existing == null || !(existing instanceof Integer)) {
//                        custom.put(key, value);
//                    } else {
//                        custom.put(key, (Integer) custom.get(key) + (Integer) value);
//                    }
//                    break;
//                case MUL:
//                    Object existing = custom.get(key);
//                    if (existing == null || !(existing instanceof Integer)) {
//                        custom.put(key, value);
//                    } else {
//                        custom.put(key, (Integer) custom.get(key) + (Integer) value);
//                    }
//                    break;
//            }
        }

        public void apply(JSONObject json) throws JSONException {
            JSONObject object;
            switch (op) {
                case INC:
                case MUL:
                    object = json.optJSONObject(key);
                    if (object == null) {
                        object = new JSONObject();
                    }
                    if (op.equals(INC)) {
                        int n = object.optInt(op, 0);
                        object.put(op, n + (int)value);
                    } else {
                        double n = object.optDouble(op, 1);
                        object.put(op, n * (double)value);
                    }
                    json.put(key, object);
                    break;
                case MIN:
                case MAX:
                    object = json.optJSONObject(key);
                    if (object == null) {
                        object = new JSONObject();
                    }
                    if (object.has(op)) {
                        object.put(op, op.equals(MIN) ? Math.min(object.getDouble(op), (Double)value) : Math.max(object.getDouble(op), (Double)value));
                    } else {
                        object.put(op, value);
                    }
                    json.put(key, object);
                    break;
                case SET_ONCE:
                    object = json.optJSONObject(key);
                    if (object == null) {
                        object = new JSONObject();
                    }
                    object.put(op, value);
                    json.put(key, object);
                    break;
                case PULL:
                case PUSH:
                case PUSH_UNIQUE:
                    object = json.optJSONObject(key);
                    if (object == null) {
                        object = new JSONObject();
                    }
                    object.accumulate(op, value);
                    json.put(key, object);
                    break;

            }

        }
    }
    static final String NAME = "name";
    static final String USERNAME = "username";
    static final String EMAIL = "email";
    static final String ORG = "org";
    static final String PHONE = "phone";
    static final String PICTURE = "picture";
    static final String PICTURE_PATH = "picturePath";
    static final String PICTURE_IN_USER_PROFILE = "[CLY]_USER_PROFILE_PICTURE";
    static final String GENDER = "gender";
    static final String BIRTHYEAR = "byear";
    static final String CUSTOM = "custom";

    private final UserImpl user;
    private final Map<String, Object> sets;
    private final List<Op> ops;
    private final List<String> cohortsToAdd, cohortsToRemove;

    UserEditorImpl(UserImpl user) {
        this.user = user;
        this.sets = new HashMap<>();
        this.ops = new ArrayList<>();
        this.cohortsToAdd = new ArrayList<>();
        this.cohortsToRemove = new ArrayList<>();
    }

    void perform(JSONObject changes, Set<String> cohortsAdded, Set<String> cohortsRemoved) throws JSONException{
        for (String key : sets.keySet()) {
            Object value = sets.get(key);
            switch (key) {
                case NAME:
                    if (value == null || value instanceof String) {
                        user.name = (String) value;
                    } else {
                        Log.w("user.name will be cast to String");
                        user.name = value.toString();
                    }
                    changes.put(NAME, value == null ? JSONObject.NULL : user.name);
                    break;
                case USERNAME:
                    if (value == null || value instanceof String) {
                        user.username = (String) value;
                    } else {
                        Log.w("user.username will be cast to String");
                        user.username = value.toString();
                    }
                    changes.put(USERNAME, value == null ? JSONObject.NULL : user.username);
                    break;
                case EMAIL:
                    if (value == null || value instanceof String) {
                        user.email = (String) value;
                    } else {
                        Log.w("user.email will be cast to String");
                        user.email = value.toString();
                    }
                    changes.put(EMAIL, value == null ? JSONObject.NULL : user.email);
                    break;
                case ORG:
                    if (value == null || value instanceof String) {
                        user.org = (String) value;
                    } else {
                        Log.w("user.org will be cast to String");
                        user.org = value.toString();
                    }
                    changes.put(ORG, value == null ? JSONObject.NULL : user.org);
                    break;
                case PHONE:
                    if (value == null || value instanceof String) {
                        user.phone = (String) value;
                    } else {
                        Log.w("user.phone will be cast to String");
                        user.phone = value.toString();
                    }
                    changes.put(PHONE, value == null ? JSONObject.NULL : user.phone);
                    break;
                case PICTURE:
                    if (value == null) {
                        user.picture = null;
                        user.picturePath = null;
                        changes.put(PICTURE_PATH, JSONObject.NULL);
                    } else if (value instanceof byte[]) {
                        user.picture = (byte[]) value;
                        changes.put(PICTURE_PATH, PICTURE_IN_USER_PROFILE);
                    } else {
                        Log.wtf("Won't set user picture (must be of type byte[])");
                    }
                    break;
                case PICTURE_PATH:
                    if (value == null) {
                        user.picture = null;
                        user.picturePath = null;
                        changes.put(PICTURE_PATH, JSONObject.NULL);
                    } else if (value instanceof String) {
                        try {
                            user.picturePath = new URI((String) value).toString();
                            changes.put(PICTURE_PATH, user.picturePath);
                        } catch (URISyntaxException e) {
                            Log.wtf("Supplied picturePath is not parsable to java.net.URI");
                        }
                    } else {
                        Log.wtf("Won't set user picturePath (must be String or null)");
                    }
                    break;
                case GENDER:
                    if (value == null || value instanceof User.Gender) {
                        user.gender = (User.Gender) value;
                        changes.put(GENDER, user.gender == null ? JSONObject.NULL : user.gender.toString());
                    } else if (value instanceof String) {
                        User.Gender gender = User.Gender.fromString((String) value);
                        if (gender == null) {
                            Log.wtf("Cannot parse gender string: " + value + " (must be one of 'F' & 'M')");
                        } else {
                            user.gender = gender;
                            changes.put(GENDER, user.gender.toString());
                        }
                    } else {
                        Log.wtf("Won't set user gender (must be of type User.Gender or one of following Strings: 'F', 'M')");
                    }
                    break;
                case BIRTHYEAR:
                    if (value == null || value instanceof Integer) {
                        user.birthyear = (Integer) value;
                        changes.put(BIRTHYEAR, value == null ? JSONObject.NULL : user.birthyear);
                    } else if (value instanceof String) {
                        try {
                            user.birthyear = Integer.parseInt((String) value);
                            changes.put(BIRTHYEAR, user.birthyear);
                        } catch (NumberFormatException e) {
                            Log.wtf("user.birthyear must be either Integer or String which can be parsed to Integer", e);
                        }
                    } else {
                        Log.wtf("Won't set user birthyear (must be of type Integer or String which can be parsed to Integer)");
                    }
                    break;
                default:
                    if (value == null || value instanceof String || value instanceof Integer || value instanceof Float || value instanceof Double || value instanceof Boolean
                            || value instanceof String[] || value instanceof Integer[] || value instanceof Float[] || value instanceof Double[] || value instanceof Boolean[] || value instanceof Object[]) {
                        if (!changes.has(CUSTOM)) {
                            changes.put(CUSTOM, new JSONObject());
                        }
                        changes.getJSONObject(CUSTOM).put(key, value);
                        if (value == null) {
                            user.custom.remove(key);
                        } else {
                            user.custom.put(key, value);
                        }
                    } else {
                        Log.wtf("Type of value " + value + " '" + value.getClass().getSimpleName() + "' is not supported yet, thus user property is not stored");
                    }
                    break;
            }
        }
        if (ops.size() > 0 && !changes.has(CUSTOM)) {
            changes.put(CUSTOM, new JSONObject());
        }
        for (Op op : ops) {
            op.apply(changes.getJSONObject(CUSTOM));
            op.apply(user.custom);
        }

        user.cohorts.addAll(cohortsToAdd);
        user.cohorts.removeAll(cohortsToRemove);
        cohortsAdded.addAll(cohortsToAdd);
        cohortsRemoved.addAll(cohortsToRemove);
    }

    @Override
    public UserEditor set(String key, Object value) {
        sets.put(key, value);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public UserEditor setCustom(String key, Object value) {
        if (!sets.containsKey(CUSTOM)) {
            sets.put(CUSTOM, new HashMap<String, Object>());
        }
        Map<String, Object> custom = (Map<String, Object>) sets.get(CUSTOM);
        custom.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    private UserEditor setCustomOp(String op, String key, Object value) {
        ops.add(new Op(op, key, value));
        return this;
    }

    @Override
    public UserEditor setName(String value) {
        return set(NAME, value);
    }

    @Override
    public UserEditor setUsername(String value) {
        return set(USERNAME, value);
    }

    @Override
    public UserEditor setEmail(String value) {
        return set(EMAIL, value);
    }

    @Override
    public UserEditor setOrg(String value) {
        return set(ORG, value);
    }

    @Override
    public UserEditor setPhone(String value) {
        return set(PHONE, value);
    }

    @Override
    public UserEditor setPicture(byte[] picture) {
        return set(PICTURE, picture);
    }

    public UserEditor setPicturePath(String picturePath) {
        return set(PICTURE_PATH, picturePath);
    }

    @Override
    public UserEditor setGender(Object gender) {
        return set(GENDER, gender);
    }

    @Override
    public UserEditor setBirthyear(int birthyear) {
        return set(BIRTHYEAR, birthyear);
    }

    @Override
    public UserEditor setBirthyear(String birthyear) {
        return set(BIRTHYEAR, birthyear);
    }

    @Override
    public UserEditor inc(String key, int by) {
        return setCustomOp(Op.INC, key, by);
    }

    @Override
    public UserEditor mul(String key, double by) {
        return setCustomOp(Op.MUL, key, by);
    }

    @Override
    public UserEditor min(String key, double value) {
        return setCustomOp(Op.MIN, key, value);
    }

    @Override
    public UserEditor max(String key, double value) {
        return setCustomOp(Op.MAX, key, value);
    }

    @Override
    public UserEditor setOnce(String key, Object value) {
        if (value == null) {
            Log.wtf("$setOnce operation operand cannot be null: key " + key);
            return this;
        } else {
            return setCustomOp(Op.SET_ONCE, key, value);
        }
    }

    @Override
    public UserEditor pull(String key, Object value) {
        if (value == null) {
            Log.wtf("$pull operation operand cannot be null: key " + key);
            return this;
        } else {
            return setCustomOp(Op.PULL, key, value);
        }
    }

    @Override
    public UserEditor push(String key, Object value) {
        if (value == null) {
            Log.wtf("$push operation operand cannot be null: key " + key);
            return this;
        } else {
            return setCustomOp(Op.PUSH, key, value);
        }
    }

    @Override
    public UserEditor pushUnique(String key, Object value) {
        if (value == null) {
            Log.wtf("pushUnique / $addToSet operation operand cannot be null: key " + key);
            return this;
        } else {
            return setCustomOp(Op.PUSH_UNIQUE, key, value);
        }
    }

    @Override
    public UserEditor addToCohort(String key) {
        if (cohortsToRemove.contains(key)) {
            cohortsToRemove.remove(key);
        }
        cohortsToAdd.add(key);
        return this;
    }

    @Override
    public UserEditor removeFromCohort(String key) {
        if (cohortsToAdd.contains(key)) {
            cohortsToAdd.remove(key);
        }
        cohortsToRemove.add(key);
        return this;
    }

    @Override
    public User commit() {
        try {
            final JSONObject changes = new JSONObject();
            final Set<String> cohortsAdded = new HashSet<>();
            final Set<String> cohortsRemoved = new HashSet<>();

            perform(changes, cohortsAdded, cohortsRemoved);

            Storage.push(user.ctx, user);

            ModuleRequests.injectParams(user.ctx, new ModuleRequests.ParamsInjector() {
                @Override
                public void call(Params params) {
                    params.add("user_details", changes.toString());
                    if (cohortsAdded.size() > 0) {
                        params.add("add_cohorts", new JSONArray(cohortsAdded).toString());
                    }
                    if (cohortsRemoved.size() > 0) {
                        params.add("remove_cohorts", new JSONArray(cohortsRemoved).toString());
                    }
                }
            });
        } catch (JSONException e) {
            Log.wtf("Exception while committing changes to User profile", e);
        }

        sets.clear();
        ops.clear();
        cohortsToAdd.clear();
        cohortsToRemove.clear();

        return user;
    }
}
