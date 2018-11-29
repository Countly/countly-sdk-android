package ly.count.sdk;

import java.util.Map;
import java.util.Set;

//import ly.count.sdk.android.internal.Device;

/**
 * User Profile object: stores profile and exposes stored data. Doesn't download profile from the
 * server, so data is available only after setting it by using {@link #edit()} and {@link UserEditor#commit()}.
 */

public abstract class User {
    public enum Gender {
        FEMALE("F"), MALE("M");

        private String value;

        Gender(String value) {
            this.value = value;
        }

        public String toString() {
            return this.value;
        }

        public static Gender fromString(String v) {
            if (FEMALE.value.equals(v)) {
                return FEMALE;
            }
            if (MALE.value.equals(v)) {
                return MALE;
            }
            return null;
        }
    }

    /**
     * Current device id
     *
     * @return id string if device id available, null otherwise
     */
    public abstract String id();

    /**
     * Current user name
     *
     * @return name string if it was set previously, null otherwise
     */
    public abstract String name();

    /**
     * Current username
     *
     * @return username string if it was set previously, null otherwise
     */
    public abstract String username();

    /**
     * Current user email
     *
     * @return email string if it was set previously, null otherwise
     */
    public abstract String email();

    /**
     * Current user organization
     *
     * @return organization string if it was set previously, null otherwise
     */
    public abstract String org();

    /**
     * Current user phone
     *
     * @return phone string if it was set previously, null otherwise
     */
    public abstract String phone();

    /**
     * Current user picture
     *
     * @return picture bytes if it was set previously, null otherwise
     */
    public abstract byte[] picture();

    /**
     * Current user picture path
     *
     * @return picture path string if it was set previously, null otherwise
     */
    public abstract String picturePath();

    /**
     * Current user gender
     *
     * @return gender if it was set previously, null otherwise
     */
    public abstract Gender gender();

    /**
     * Current user year of birth
     *
     * @return year of birth if it was set previously, null otherwise
     */
    public abstract Integer birthyear();

    /**
     * Current user locale in "lang_COUNTRY", like en_US or de_DE if default device locale is overridden.
     * If not set, Countly will use {@link Device#getLocale()}.
     *
     * @return current user locale
     */
    public abstract String locale();

    /**
     * Current user country:
     * <ul>
     *     <li>"ISO_CODE" of the country if set</li>
     *     <li>"" if set to do not record, that is user opt out from location services</li>
     *     <li>{code null} if not specified, geoip is used on the server</li>
     * </ul>
     *
     * @return current user country
     */
    public abstract String country();

    /**
     * Current user city:
     * <ul>
     *     <li>"SOME CITY NAME" if set</li>
     *     <li>"" if set to do not record, that is user opt out from location services</li>
     *     <li>{code null} if not specified, geoip is used on the server</li>
     * </ul>
     *
     * @return current user city
     */
    public abstract String city();

    /**
     * Current user location (string of "LAT,LON" format):
     * <ul>
     *     <li>"LAT,LON" if set</li>
     *     <li>"" if set to do not record, that is user opt out from location services</li>
     *     <li>{code null} if not specified, geoip is used on the server</li>
     * </ul>
     *
     * @return current user location
     */
    public abstract String location();

    /**
     * Current user cohorts set
     *
     * @return cohorts if it was set previously, null otherwise
     */
    public abstract Set<String> cohorts();

    /**
     * Current user custom data map
     *
     * @return custom data if it was set previously, empty map otherwise
     */
    public abstract Map<String, Object> custom();

    /**
     * Edit this user. Changes only applied after {@link UserEditor#commit()} call.
     *
     * @return new {@link UserEditor} object
     */
    public abstract UserEditor edit();
}
