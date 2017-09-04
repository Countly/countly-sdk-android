package ly.count.android.sdk.internal;

/**
 * Serialization interface.
 */

interface Storable extends Byteable {
    Long storageId();
    String storagePrefix();
}
