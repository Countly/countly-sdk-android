package ly.count.sdk.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ly.count.sdk.User;
import ly.count.sdk.UserEditor;

/**
 * Class for user profile data access & manipulation
 */

public class UserImpl extends User implements Storable {
    private static final Log.Module L = Log.module("UserImpl");

    String id, name, username, email, org, phone, picturePath, locale, country, city, location;
    byte[] picture;
    Gender gender;
    Integer birthyear;
    Set<String> cohorts;
    Map<String, Object> custom;
    CtxCore ctx;

    public UserImpl(CtxCore ctx) {
        this.ctx = ctx;
        this.custom = new HashMap<>();
        this.cohorts = new HashSet<>();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String org() {
        return org;
    }

    public String phone() {
        return phone;
    }

    public byte[] picture() {
        return picture;
    }

    public String picturePath() {
        return picturePath;
    }

    public Gender gender() {
        return gender;
    }

    public String locale() {
        return locale;
    }

    public Integer birthyear() {
        return birthyear;
    }

    public String country() {
        return country;
    }

    public String city() {
        return city;
    }

    public String location() {
        return location;
    }

    public Set<String> cohorts() {
        return cohorts;
    }

    public Map<String, Object> custom() {
        return custom;
    }

    public UserEditor edit() {
        return new UserEditorImpl(this);
    }

    @Override
    public byte[] store() {
        ByteArrayOutputStream bytes = null;
        ObjectOutputStream stream = null;
        try {
            bytes = new ByteArrayOutputStream();
            stream = new ObjectOutputStream(bytes);
            stream.writeObject(name);
            stream.writeObject(username);
            stream.writeObject(email);
            stream.writeObject(org);
            stream.writeObject(phone);
            stream.writeInt(picture == null ? 0 : picture.length);
            if (picture != null) {
                stream.write(picture);
            }
            stream.writeObject(picturePath);
            stream.writeObject(gender == null ? null : gender.toString());
            stream.writeInt(birthyear == null ? -1 : birthyear);
            stream.writeObject(locale);
            stream.writeObject(country);
            stream.writeObject(city);
            stream.writeObject(location);
            stream.writeObject(cohorts == null || cohorts.size() == 0 ? null : cohorts);
            stream.writeObject(custom);
            stream.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            L.wtf("Cannot serialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean restore(byte[] data) {
        ByteArrayInputStream bytes = null;
        ObjectInputStream stream = null;

        try {
            bytes = new ByteArrayInputStream(data);
            stream = new ObjectInputStream(bytes);
            name = (String) stream.readObject();
            username = (String) stream.readObject();
            email = (String) stream.readObject();
            org = (String) stream.readObject();
            phone = (String) stream.readObject();

            int picLength = stream.readInt();
            if (picLength != 0) {
                picture = new byte[picLength];
                stream.readFully(picture);
            }
            picturePath = (String) stream.readObject();

            String g = (String) stream.readObject();
            if (g != null) {
                gender = Gender.fromString(g);
            }

            int y = stream.readInt();
            if (y != -1) {
                birthyear = y;
            }
            locale = (String) stream.readObject();
            country = (String) stream.readObject();
            city = (String) stream.readObject();
            location = (String) stream.readObject();

            cohorts = (Set<String>) stream.readObject();
            cohorts = cohorts == null ? new HashSet<String>() : cohorts;

            custom = (Map<String, Object>) stream.readObject();
            if (custom == null) {
                custom = new HashMap<>();
            }

            return true;
        } catch (IOException | ClassNotFoundException e) {
            L.wtf("Cannot deserialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    L.wtf("Cannot happen", e);
                }
            }
        }

        return false;
    }

    @Override
    public Long storageId() {
        return 0L;
    }

    @Override
    public String storagePrefix() {
        return "user";
    }
}
