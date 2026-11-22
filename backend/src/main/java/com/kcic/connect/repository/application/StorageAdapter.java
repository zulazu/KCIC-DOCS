package com.kcic.connect.repository.application;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Storage port owned by the repository module. Domain code never imports an
 * object-storage SDK; adapters implement this boundary.
 */
public interface StorageAdapter {

    /** Object-store provider code persisted in storage metadata. */
    String provider();

    void put(String bucket, String objectKey, Path source, long sizeBytes, String contentType);

    InputStream get(String bucket, String objectKey);

    void delete(String bucket, String objectKey);
}
