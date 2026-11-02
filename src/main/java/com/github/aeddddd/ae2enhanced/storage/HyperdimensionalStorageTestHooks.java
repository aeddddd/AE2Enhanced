package com.github.aeddddd.ae2enhanced.storage;

import java.io.IOException;

/** 包级故障注入点；生产实例使用无操作实现。 */
interface HyperdimensionalStorageTestHooks {
    HyperdimensionalStorageTestHooks NONE = new HyperdimensionalStorageTestHooks() {};

    default void beforeWalWrite(StorageSection section) throws IOException {}

    default boolean failWalPermanently(StorageSection section) { return false; }

    default void beforeCheckpoint(StorageSection section) throws IOException {}

    default void beforeTruncation(StorageSection section) throws IOException {}

    default void beforeQuarantineWrite() throws IOException {}
}
