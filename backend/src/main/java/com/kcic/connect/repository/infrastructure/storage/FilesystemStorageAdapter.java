package com.kcic.connect.repository.infrastructure.storage;

import com.kcic.connect.repository.application.StorageAdapter;
import com.kcic.connect.repository.application.StorageException;
import com.kcic.connect.shared.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development and test adapter. Production uses the MinIO adapter; this keeps
 * local environments operable without an object-storage container. The root
 * directory must still be private to the application host.
 */
@Component
@ConditionalOnProperty(name = "kcic.storage.provider", havingValue = "filesystem")
public class FilesystemStorageAdapter implements StorageAdapter {

    private final Path root;

    public FilesystemStorageAdapter(AppProperties properties) {
        String configured = properties.storage() == null ? null : properties.storage().localRoot();
        this.root = Path.of(configured == null || configured.isBlank()
                ? System.getProperty("java.io.tmpdir") + "/kcic-storage"
                : configured).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return "filesystem";
    }

    @Override
    public void put(String bucket, String objectKey, Path source, long sizeBytes, String contentType) {
        Path target = resolve(bucket, objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new StorageException("Gagal menyimpan file ke storage lokal.", ex);
        }
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        Path target = resolve(bucket, objectKey);
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new StorageException("File tidak dapat dibaca dari storage lokal.", ex);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            Files.deleteIfExists(resolve(bucket, objectKey));
        } catch (IOException ex) {
            throw new StorageException("Gagal menghapus file dari storage lokal.", ex);
        }
    }

    private Path resolve(String bucket, String objectKey) {
        Path target = root.resolve(bucket).resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Lokasi object tidak valid.");
        }
        return target;
    }
}
