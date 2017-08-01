package ly.count.android.sdk;

/**
 * User Profile object: stores profile and exposes stored data. Doesn't download profile from the
 * server, so data is available only after setting it by using {@link #edit()} and {@link UserEditor#commit()}.
 */

public abstract class User {     // extends Storable, abstract because Storable is in .internal
    public enum Gender {
        FEMALE, MALE;           // such much not minorities-friendly
    }
//    abstract String id();  ? - shorthand to developer-specified id if any?
    abstract String name();
    abstract String username();
    abstract String email();
    abstract String org();
    abstract String phone();
    abstract byte[] picture();

    //    abstract Bitmap picture(); ? - android dependency
    abstract Gender gender();

    abstract UserEditor edit();

    // no set(Map) methods, legacy stuff should stay in Countly I guess
}
