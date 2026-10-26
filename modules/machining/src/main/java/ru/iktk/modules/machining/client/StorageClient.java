package ru.iktk.modules.machining.client;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Клиент хранилища УП (MinIO/S3).
 *
 * <p>Сохраняет текстовые файлы (G-код) в bucket {@code iktk}.
 * URI объекта: {@code s3://<bucket>/<projectId>/<category>/<name>}.
 */
@Component
public class StorageClient {

    private static final Logger log = LoggerFactory.getLogger(StorageClient.class);

    @Value("${iktk.machining.storage.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${iktk.machining.storage.bucket:iktk}")
    private String bucket;

    @Value("${iktk.machining.storage.access-key:minioadmin}")
    private String accessKey;

    @Value("${iktk.machining.storage.secret-key:minioadmin}")
    private String secretKey;

    private MinioClient minio;

    @PostConstruct
    public void init() {
        log.info("StorageClient: init, endpoint={}, bucket={}, accessKey={}", endpoint, bucket, accessKey);
        this.minio = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("StorageClient: bucket '{}' создан", bucket);
            } else {
                log.info("StorageClient: bucket '{}' уже существует", bucket);
            }
        } catch (Exception e) {
            log.error("StorageClient: ошибка ensureBucket '{}': {}", bucket, e.getMessage(), e);
            throw new IllegalStateException("MinIO bucket init failed", e);
        }
    }

    /**
     * Загрузить текстовый файл.
     *
     * @return URI объекта: {@code s3://<bucket>/<projectId>/<category>/<name>}
     */
    public String uploadText(String text, String projectId, String category, String name) {
        String objectName = String.format("%s/%s/%s", projectId, category, name);
        try {
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType("text/plain; charset=utf-8")
                    .build());
            String uri = String.format("s3://%s/%s", bucket, objectName);
            log.info("StorageClient.uploadText OK: uri={}, bytes={}", uri, bytes.length);
            return uri;
        } catch (Exception e) {
            log.error("StorageClient.uploadText failed: objectName={}", objectName, e);
            throw new IllegalStateException("MinIO upload failed: " + objectName, e);
        }
    }

    /**
     * Удалить объект по URI {@code s3://<bucket>/<objectName>}.
     */
    public void delete(String uri) {
        if (uri == null || uri.isBlank()) {
            log.warn("StorageClient.delete: пустой URI");
            return;
        }
        String prefix = "s3://" + bucket + "/";
        if (!uri.startsWith(prefix)) {
            log.warn("StorageClient.delete: URI не того bucket: {}", uri);
            return;
        }
        String objectName = uri.substring(prefix.length());
        try {
            minio.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            log.info("StorageClient.delete OK: uri={}", uri);
        } catch (Exception e) {
            log.error("StorageClient.delete failed: uri={}", uri, e);
            throw new IllegalStateException("MinIO delete failed: " + uri, e);
        }
    }
}