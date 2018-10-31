package ly.count.sdk.internal;

import ly.count.sdk.internal.Byteable;

/**
 * Serialization interface.
 */

public interface Storable extends Byteable {
    Long storageId();
    String storagePrefix();
}
