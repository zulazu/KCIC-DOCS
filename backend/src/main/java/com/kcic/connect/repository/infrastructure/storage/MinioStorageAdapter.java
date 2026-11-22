package com.kcic.connect.repository.infrastructure.storage;

import com.kcic.connect.repository.application.StorageAdapter;
import com.kcic.connect.repository.application.StorageException;
import com.kcic.connect.shared.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "kcic.storage.provider", havingValue = "minio", matchIfMissing = true)
public class MinioStorageAdapter implements StorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageAdapter.class);

    private final AppProperties properties;
    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();
    private volatile MinioClient client;

    public MinioStorageAdapter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String provider() {
        return "minio";
    }

    @Override
    public void put(String bucket, String objectKey, Path source, long sizeBytes, String contentType) {
        ensureBucket(bucket);
        try (InputStream stream = Files.newInputStream(source)) {
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, sizeBytes, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            log.error("MinIO put failed bucket={} key={}", bucket, objectKey, ex);
            throw new StorageException("Gagal menyimpan file ke object storage.", ex);
        }
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        try {
            return client().getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            log.error("MinIO get failed bucket={} key={}", bucket, objectKey, ex);
            throw new StorageException("File tidak dapat dibaca dari object storage.", ex);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            log.error("MinIO delete failed bucket={} key={}", bucket, objectKey, ex);
            throw new StorageException("Gagal menghapus file dari object storage.", ex);
        }
    }

    private void ensureBucket(String bucket) {
        if (ensuredBuckets.contains(bucket)) {
            return;
        }
        synchronized (ensuredBuckets) {
            if (ensuredBuckets.contains(bucket)) {
                return;
            }
            try {
                MinioClient minio = client();
                if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                ensuredBuckets.add(bucket);
            } catch (Exception ex) {
                log.error("MinIO bucket setup failed bucket={}", bucket, ex);
                throw new StorageException("Bucket object storage tidak tersedia.", ex);
            }
        }
    }

    private MinioClient client() {
        MinioClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                AppProperties.Storage storage = properties.storage();
                if (storage == null || isBlank(storage.endpoint())) {
                    throw new StorageException("Object storage belum dikonfigurasi.");
                }
                if (isBlank(storage.accessKey()) || isBlank(storage.secretKey())) {
                    throw new StorageException("Kredensial object storage belum dikonfigurasi.");
                }
                MinioClient.Builder builder = MinioClient.builder()
                        .endpoint(storage.endpoint())
                        .credentials(storage.accessKey(), storage.secretKey());
                if (storage.region() != null && !storage.region().isBlank()) {
                    builder.region(storage.region());
                }
                client = builder.build();
            }
            return client;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
