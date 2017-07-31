package ly.count.android.sdk.internal;

/**
 * Serialization interface.
 */

interface Storable {
    Long storageId();
    String storagePrefix();
    byte[] store();
    boolean restore(byte[] data);
}
