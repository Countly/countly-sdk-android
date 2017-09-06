package ly.count.android.sdk.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.User;
import ly.count.android.sdk.UserEditor;

/**
 * Class for user profile data access & manipulation
 */

public class UserImpl extends User implements Storable {
    String name, username, email, org, phone, picturePath;
    byte[] picture;
    Gender gender;
    Integer birthyear;
    Map<String, Object> custom;

    UserImpl() {
        custom = new HashMap<>();
    }

    public String id() {
        return null;
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

    public Integer birthyear() {
        return birthyear;
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
            stream.writeObject(gender == null ? null : gender.toString());
            stream.writeInt(birthyear == null ? -1 : birthyear);
            stream.writeObject(custom);
            stream.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            Log.wtf("Cannot serialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
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

            String g = (String) stream.readObject();
            if (g != null) {
                gender = Gender.fromString(g);
            }

            int y = stream.readInt();
            if (y != -1) {
                birthyear = y;
            }

            custom = (Map<String, Object>) stream.readObject();
            if (custom == null) {
                custom = new HashMap<>();
            }

            return true;
        } catch (IOException | ClassNotFoundException e) {
            Log.wtf("Cannot deserialize session", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
                }
            }
            if (bytes != null) {
                try {
                    bytes.close();
                } catch (IOException e) {
                    Log.wtf("Cannot happen", e);
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
