package ly.count.android.sdk;

import java.util.Map;

/**
 * User Profile object: stores profile and exposes stored data. Doesn't download profile from the
 * server, so data is available only after setting it by using {@link #edit()} and {@link UserEditor#commit()}.
 */

public abstract class User {     // extends Storable, abstract because Storable is in .internal
    public enum Gender {
        FEMALE("F"), MALE("M");           // such much not minorities-friendly

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

    public abstract String id();
    public abstract String name();
    public abstract String username();
    public abstract String email();
    public abstract String org();
    public abstract String phone();
    public abstract String picturePath();
    public abstract byte[] picture();
    //    abstract Bitmap picture(); ? - android dependency
    public abstract Gender gender();
    public abstract Integer birthyear();
    public abstract Map<String, Object> custom();

    public abstract UserEditor edit();

    // no set(Map) methods, legacy stuff should stay in Countly I guess
}
