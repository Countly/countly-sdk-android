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

    UserEditor setName(String value);
    UserEditor setUsername(String value);

    UserEditor inc(String key, int value);
    UserEditor mul(String key, int value);
    // ...

    User commit();
}
