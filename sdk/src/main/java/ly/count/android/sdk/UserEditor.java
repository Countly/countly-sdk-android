package ly.count.android.sdk;

/**
 * Created by artem on 01/08/2017.
 */

public interface UserEditor {
    /**
     * Sets property of user profile to the value supplied. All standard Countly properties
     * like name, username, etc. (see {@link User}) are detected by key and put into standard
     * profile properties, others are put into custom property:
     * {name: "John", username: "johnsnow", custom: {lord: true, kingdom: "North", dead: false}}
     *
     * @see User
     * @param key name of user profile property
     * @param value value for this property, null to delete property
     * @return this instance for method chaining
     */
    UserEditor set(String key, Object value);
    UserEditor setCustom(String key, Object value);

    UserEditor setName(String value);
    UserEditor setUsername(String value);
    UserEditor setEmail(String value);
    UserEditor setOrg(String value);
    UserEditor setPhone(String value);
    UserEditor setPicture(byte[] picture);
    UserEditor setPicturePath(String picturePath);
    UserEditor setGender(Object gender);
    UserEditor setBirthyear(int birthyear);
    UserEditor setBirthyear(String birthyear);

    UserEditor inc(String key, int by);
    UserEditor mul(String key, double by);
    UserEditor min(String key, double value);
    UserEditor max(String key, double value);
    UserEditor setOnce(String key, Object value);
    UserEditor pull(String key, Object value);
    UserEditor push(String key, Object value);
    UserEditor pushUnique(String key, Object value);
    // ...

    User commit();
}
