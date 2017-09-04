package ly.count.android.sdk.internal;

/**
 * Serialization interface.
 */

public interface Byteable {
    byte[] store();
    boolean restore(byte[] data);
}
