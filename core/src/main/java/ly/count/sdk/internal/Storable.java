package ly.count.sdk.internal;

/**
 * Serialization interface.
 */

public interface Storable extends Byteable {
    Long storageId();
    String storagePrefix();
}
