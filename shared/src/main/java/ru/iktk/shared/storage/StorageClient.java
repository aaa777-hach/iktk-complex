package ru.iktk.shared.storage;

import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Универсальный клиент объектного хранилища MinIO/S3 для ИКТК.
 *
 * <p>Общая библиотека, используемая всеми модулями и сервисами комплекса
 * для низкоуровневых операций с файлами:
 * <ul>
 *   <li>загрузка/выгрузка файлов и текстовых данных;</li>
 *   <li>удаление, копирование, перемещение объектов;</li>
 *   <li>проверка существования и получение метаданных;</li>
 *   <li>список объектов по префиксу;</li>
 *   <li>генерация presigned URL (GET/PUT);</li>
 *   <li>работа с бакетами (создание, проверка, удаление);</li>
 *   <li>вычисление SHA-256 для дедупликации и целостности.</li>
 * </ul>
 *
 * <p><b>Отличие от FileExchangeService (позиция 31).</b>
 * {@code FileExchangeService} — специализированный сервис обмена файлами
 * между CAD/CAM и ИКТК (категории, разрешение incoming, дедупликация).
 * {@code StorageClient} — низкоуровневая библиотека, не знающая о
 * бизнес-логике; используется модулями напрямую (например,
 * {@code CncProgramService} для сохранения УП, {@code documentation}
 * для экспорта, интеграциями для обмена).
 *
 * <p><b>Два уровня API:</b>
 * <ol>
 *   <li><b>Низкоуровневый:</b> методы с явными {@code bucket} и
 *       {@code objectKey} — полный контроль.</li>
 *   <li><b>Удобные обёртки:</b> методы с {@code projectId},
 *       {@code category}, {@code filename} — используют
 *       {@link #buildObjectKey} и бакет по умолчанию.</li>
 * </ol>
 *
 * <p><b>Формат внутреннего URI:</b>
 * <pre>
 *   minio://{bucket}/{objectKey}
 *   например: minio://iktk-files/proj-1/cnc/O_123_Fanuc.nc
 * </pre>
 *
 * <p><b>Конфигурация:</b>
 * <pre>
 *   iktk.storage.endpoint       — URL MinIO (http://minio:9000)
 *   iktk.storage.access-key     — access key
 *   iktk.storage.secret-key     — secret key
 *   iktk.storage.bucket         — бакет по умолчанию (iktk-files)
 *   iktk.storage.temp-dir       — каталог временных файлов
 *   iktk.storage.region         — регион (для AWS S3, опционально)
 *   iktk.storage.max-file-size  — максимальный размер файла (байт)
 * </pre>
 *
 * <p><b>Обработка ошибок:</b> все методы выбрасывают
 * {@link StorageException} — общее исключение библиотеки.
 */
@Component
public class StorageClient {

    private static final Logger log = LoggerFactory.getLogger(StorageClient.class);

    /** Схема внутреннего URI. */
    public static final String URI_SCHEME = "minio";

    /** Размер буфера для потоковой передачи. */
    private static final int BUFFER_SIZE = 8192;

    /** Часть размера для MinIO PutObject (-1 = неизвестно, streaming). */
    private static final long PART_SIZE_UNKNOWN = -1;

    /** Максимальное число объектов в одном list-запросе. */
    private static final int MAX_LIST_RESULTS = 10_000;

    @Value("${iktk.storage.bucket:iktk-files}")
    private String defaultBucket;

    @Value("${iktk.storage.temp-dir:/tmp/iktk-storage}")
    private String tempDir;

    @Value("${iktk.storage.max-file-size:5368709120}")
    private long maxFileSize;

    @Value("${iktk.storage.presigned-ttl-minutes:15}")
    private long presignedTtlMinutes;

    private final MinioClient minioClient;

    public StorageClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    // ==================================================================
    // 1. Низкоуровневый API: bucket + objectKey
    // ==================================================================

    /**
     * Загрузить файл в хранилище.
     *
     * @param bucket    имя бакета
     * @param objectKey ключ объекта
     * @param file      локальный файл
     */
    public void upload(String bucket, String objectKey, Path file) {
        validateFile(file);
        validateBucket(bucket);
        validateObjectKey(objectKey);

        try {
            ensureBucket(bucket);
            long size = Files.size(file);

            try (InputStream in = Files.newInputStream(file)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(in, size, PART_SIZE_UNKNOWN)
                                .contentType(resolveContentType(file.getFileName().toString()))
                                .build()
                );
            }

            log.info("StorageClient.upload: bucket={}, key={}, size={}",
                    bucket, objectKey, size);

        } catch (Exception e) {
            log.error("StorageClient.upload failed: bucket={}, key={}", bucket, objectKey, e);
            throw new StorageException("Ошибка загрузки файла: " + objectKey, e);
        }
    }

    /**
     * Загрузить текстовое содержимое.
     */
    public void uploadTextToBucket(String bucket, String objectKey, String content, String contentType) {
        validateBucket(bucket);
        validateObjectKey(objectKey);
        if (content == null) {
            throw new StorageException("Содержимое не задано: " + objectKey);
        }

        try {
            ensureBucket(bucket);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

            try (InputStream in = new ByteArrayInputStream(bytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(in, bytes.length, PART_SIZE_UNKNOWN)
                                .contentType(contentType == null
                                        ? "text/plain; charset=utf-8"
                                        : contentType)
                                .build()
                );
            }

            log.info("StorageClient.uploadText: bucket={}, key={}, bytes={}",
                    bucket, objectKey, bytes.length);

        } catch (Exception e) {
            log.error("StorageClient.uploadText failed: bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Ошибка загрузки текста: " + objectKey, e);
        }
    }

    /**
     * Выгрузить объект во временный каталог.
     */
    public Path download(String bucket, String objectKey) {
        validateBucket(bucket);
        validateObjectKey(objectKey);

        Path target = buildTempPath(objectKey);

        try {
            Files.createDirectories(target.getParent());

            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build())) {

                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("StorageClient.download: bucket={}, key={}, target={}, size={}",
                    bucket, objectKey, target, Files.size(target));

            return target;

        } catch (ErrorResponseException e) {
            log.error("StorageClient.download: объект не найден, bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Объект не найден: " + objectKey, e);
        } catch (Exception e) {
            log.error("StorageClient.download failed: bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Ошибка выгрузки объекта: " + objectKey, e);
        }
    }

    /**
     * Выгрузить объект в указанный файл.
     */
    public Path downloadTo(String bucket, String objectKey, Path target) {
        validateBucket(bucket);
        validateObjectKey(objectKey);
        if (target == null) {
            throw new StorageException("Не задан целевой файл");
        }

        try {
            Files.createDirectories(target.getParent());

            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build())) {

                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("StorageClient.downloadTo: bucket={}, key={}, target={}",
                    bucket, objectKey, target);
            return target;

        } catch (Exception e) {
            log.error("StorageClient.downloadTo failed: bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Ошибка выгрузки объекта: " + objectKey, e);
        }
    }

    /**
     * Прочитать объект как строку (UTF-8).
     */
    public String downloadText(String bucket, String objectKey) {
        validateBucket(bucket);
        validateObjectKey(objectKey);

        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {

            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("StorageClient.downloadText: bucket={}, key={}, chars={}",
                    bucket, objectKey, content.length());
            return content;

        } catch (Exception e) {
            log.error("StorageClient.downloadText failed: bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Ошибка чтения текста: " + objectKey, e);
        }
    }

    /**
     * Удалить объект.
     */
    public void delete(String bucket, String objectKey) {
        validateBucket(bucket);
        validateObjectKey(objectKey);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            log.info("StorageClient.delete: bucket={}, key={}", bucket, objectKey);
        } catch (Exception e) {
            log.error("StorageClient.delete failed: bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Ошибка удаления объекта: " + objectKey, e);
        }
    }

    /**
     * Проверить существование объекта.
     */
    public boolean exists(String bucket, String objectKey) {
        if (bucket == null || objectKey == null) {
            return false;
        }
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            log.warn("StorageClient.exists: ошибка проверки, bucket={}, key={}",
                    bucket, objectKey, e);
            return false;
        }
    }

    /**
     * Получить метаданные объекта.
     */
    public ObjectInfo stat(String bucket, String objectKey) {
        validateBucket(bucket);
        validateObjectKey(objectKey);

        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());

            return new ObjectInfo(
                    bucket,
                    objectKey,
                    stat.size(),
                    stat.contentType(),
                    stat.etag(),
                    stat.lastModified() == null ? null : stat.lastModified().toInstant(),
                    stat.userMetadata());

        } catch (Exception e) {
            log.error("StorageClient.stat failed: bucket={}, key={}", bucket, objectKey, e);
            throw new StorageException("Ошибка получения метаданных: " + objectKey, e);
        }
    }

    /**
     * Список объектов по префиксу.
     */
    public List<ObjectInfo> list(String bucket, String prefix) {
        validateBucket(bucket);

        List<ObjectInfo> result = new ArrayList<>();

        try {
            Iterable<io.minio.Result<Item>> items = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix == null ? "" : prefix)
                            .recursive(true)
                            .maxKeys(MAX_LIST_RESULTS)
                            .build());

            for (io.minio.Result<Item> item : items) {
                Item i = item.get();
                result.add(new ObjectInfo(
                        bucket,
                        i.objectName(),
                        i.size(),
                        null,
                        i.etag(),
                        i.lastModified() == null ? null : i.lastModified().toInstant(),
                        Map.of()));
            }

            log.debug("StorageClient.list: bucket={}, prefix={}, count={}",
                    bucket, prefix, result.size());
            return result;

        } catch (Exception e) {
            log.error("StorageClient.list failed: bucket={}, prefix={}", bucket, prefix, e);
            throw new StorageException("Ошибка получения списка объектов", e);
        }
    }

    /**
     * Копировать объект внутри хранилища.
     */
    public void copy(String sourceBucket, String sourceKey,
                     String targetBucket, String targetKey) {
        validateBucket(sourceBucket);
        validateObjectKey(sourceKey);
        validateBucket(targetBucket);
        validateObjectKey(targetKey);

        try {
            ensureBucket(targetBucket);

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(targetKey)
                            .source(CopySource.builder()
                                    .bucket(sourceBucket)
                                    .object(sourceKey)
                                    .build())
                            .build());

            log.info("StorageClient.copy: {}:{} → {}:{}",
                    sourceBucket, sourceKey, targetBucket, targetKey);

        } catch (Exception e) {
            log.error("StorageClient.copy failed: {}:{} → {}:{}",
                    sourceBucket, sourceKey, targetBucket, targetKey, e);
            throw new StorageException("Ошибка копирования объекта", e);
        }
    }

    /**
     * Переместить объект (копирование + удаление источника).
     */
    public void move(String sourceBucket, String sourceKey,
                     String targetBucket, String targetKey) {
        copy(sourceBucket, sourceKey, targetBucket, targetKey);
        delete(sourceBucket, sourceKey);
        log.info("StorageClient.move: {}:{} → {}:{}",
                sourceBucket, sourceKey, targetBucket, targetKey);
    }

    /**
     * Объединить несколько объектов в один (server-side compose).
     * Используется, например, для склейки больших УП из фрагментов.
     */
    public void compose(String bucket, String targetKey, List<String> sourceKeys) {
        validateBucket(bucket);
        validateObjectKey(targetKey);
        if (sourceKeys == null || sourceKeys.isEmpty()) {
            throw new StorageException("Не заданы исходные объекты для compose");
        }

        try {
            List<ComposeSource> sources = sourceKeys.stream()
                    .map(k -> ComposeSource.builder()
                            .bucket(bucket)
                            .object(k)
                            .build())
                    .toList();

            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucket)
                            .object(targetKey)
                            .sources(sources)
                            .build());

            log.info("StorageClient.compose: bucket={}, target={}, sources={}",
                    bucket, targetKey, sources.size());

        } catch (Exception e) {
            log.error("StorageClient.compose failed: bucket={}, target={}",
                    bucket, targetKey, e);
            throw new StorageException("Ошибка compose объектов", e);
        }
    }

    // ==================================================================
    // 2. Presigned URL
    // ==================================================================

    /**
     * Presigned URL для скачивания (GET).
     */
    public String presignedGetUrl(String bucket, String objectKey, Duration ttl) {
        validateBucket(bucket);
        validateObjectKey(objectKey);
        Duration effective = ttl == null
                ? Duration.ofMinutes(presignedTtlMinutes)
                : ttl;

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) effective.toSeconds(), TimeUnit.SECONDS)
                            .build());

            log.debug("StorageClient.presignedGetUrl: bucket={}, key={}, ttl={}s",
                    bucket, objectKey, effective.toSeconds());
            return url;

        } catch (Exception e) {
            log.error("StorageClient.presignedGetUrl failed: bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Ошибка генерации presigned GET URL", e);
        }
    }

    /**
     * Presigned URL для загрузки (PUT).
     */
    public String presignedPutUrl(String bucket, String objectKey, Duration ttl) {
        validateBucket(bucket);
        validateObjectKey(objectKey);
        Duration effective = ttl == null
                ? Duration.ofMinutes(presignedTtlMinutes)
                : ttl;

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) effective.toSeconds(), TimeUnit.SECONDS)
                            .build());

            log.debug("StorageClient.presignedPutUrl: bucket={}, key={}, ttl={}s",
                    bucket, objectKey, effective.toSeconds());
            return url;

        } catch (Exception e) {
            log.error("StorageClient.presignedPutUrl failed: bucket={}, key={}",
                    bucket, objectKey, e);
            throw new StorageException("Ошибка генерации presigned PUT URL", e);
        }
    }

    // ==================================================================
    // 3. Бакеты
    // ==================================================================

    /**
     * Проверить существование бакета.
     */
    public boolean bucketExists(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return false;
        }
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.warn("StorageClient.bucketExists: ошибка проверки, bucket={}", bucket, e);
            return false;
        }
    }

    /**
     * Создать бакет, если он не существует.
     */
    public void ensureBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new StorageException("Не задан bucket");
        }
        try {
            if (!minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("StorageClient.ensureBucket: создан бакет {}", bucket);
            }
        } catch (Exception e) {
            log.error("StorageClient.ensureBucket failed: bucket={}", bucket, e);
            throw new StorageException("Ошибка создания бакета: " + bucket, e);
        }
    }

    /**
     * Удалить бакет (только если пуст).
     */
    public void deleteBucket(String bucket) {
        validateBucket(bucket);
        try {
            minioClient.removeBucket(
                    RemoveBucketArgs.builder().bucket(bucket).build());
            log.info("StorageClient.deleteBucket: бакет {} удалён", bucket);
        } catch (Exception e) {
            log.error("StorageClient.deleteBucket failed: bucket={}", bucket, e);
            throw new StorageException("Ошибка удаления бакета: " + bucket, e);
        }
    }

    // ==================================================================
    // 4. Удобные обёртки: projectId + category + filename
    // ==================================================================

    /**
     * Загрузить файл с использованием бакета по умолчанию.
     */
    public String upload(Path file, String projectId, String category) {
        String objectKey = buildObjectKey(projectId, category, file.getFileName().toString());
        upload(defaultBucket, objectKey, file);
        return buildUri(defaultBucket, objectKey);
    }

    /**
     * Загрузить текстовое содержимое (обёртка для {@code CncProgramService}).
     *
     * @param content   содержимое
     * @param projectId идентификатор проекта
     * @param category  категория (`cnc`, `docs`, `geometry`, ...)
     * @param filename  имя файла
     * @return внутренний URI
     */
    public String uploadText(String content, String projectId, String category, String filename) {
        String objectKey = buildObjectKey(projectId, category, filename);
        uploadTextToBucket(defaultBucket, objectKey, content, resolveContentType(filename));
        return buildUri(defaultBucket, objectKey);
    }

    /**
     * Выгрузить объект по внутреннему URI.
     */
    public Path download(String uri) {
        UriParts parts = parseUri(uri);
        return download(parts.bucket(), parts.objectKey());
    }

    /**
     * Прочитать текстовый объект по внутреннему URI.
     */
    public String downloadText(String uri) {
        UriParts parts = parseUri(uri);
        return downloadText(parts.bucket(), parts.objectKey());
    }

    /**
     * Удалить объект по внутреннему URI.
     */
    public void delete(String uri) {
        UriParts parts = parseUri(uri);
        delete(parts.bucket(), parts.objectKey());
    }

    /**
     * Проверить существование объекта по внутреннему URI.
     */
    public boolean exists(String uri) {
        try {
            UriParts parts = parseUri(uri);
            return exists(parts.bucket(), parts.objectKey());
        } catch (StorageException e) {
            return false;
        }
    }

    // ==================================================================
    // 5. Утилиты: URI, ключи, хэш
    // ==================================================================

    /**
     * Построить ключ объекта: {projectId}/{category}/{uuid}-{filename}.
     */
    public String buildObjectKey(String projectId, String category, String filename) {
        String safeProject = sanitize(projectId == null ? "common" : projectId);
        String safeCategory = sanitize(category == null ? "files" : category);
        String safeName = sanitize(filename == null ? "unnamed" : filename);
        String uuid = UUID.randomUUID().toString();
        return safeProject + "/" + safeCategory + "/" + uuid + "-" + safeName;
    }

    /**
     * Построить внутренний URI.
     */
    public String buildUri(String bucket, String objectKey) {
        return URI_SCHEME + "://" + bucket + "/" + objectKey;
    }

    /**
     * Разобрать внутренний URI: {@code minio://bucket/key} → (bucket, key).
     */
    public UriParts parseUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new StorageException("Пустой URI");
        }

        String prefix = URI_SCHEME + "://";
        if (!uri.startsWith(prefix)) {
            // Возможно, передан просто objectKey — используем бакет по умолчанию
            return new UriParts(defaultBucket, uri);
        }

        String rest = uri.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            throw new StorageException("Некорректный URI: " + uri);
        }

        String bucket = rest.substring(0, slash);
        String objectKey = rest.substring(slash + 1);

        if (bucket.isBlank() || objectKey.isBlank()) {
            throw new StorageException("Некорректный URI: " + uri);
        }

        return new UriParts(bucket, objectKey);
    }

    /**
     * Вычислить SHA-256 файла.
     */
    public String sha256(Path file) {
        if (file == null || !Files.exists(file)) {
            throw new StorageException("Файл не найден: " + file);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 недоступен", e);
        } catch (IOException e) {
            throw new StorageException("Ошибка чтения файла: " + file, e);
        }
    }

    /**
     * Вычислить SHA-256 строки.
     */
    public String sha256(String content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 недоступен", e);
        }
    }

    // ==================================================================
    // 6. Вспомогательные методы
    // ==================================================================

    private void validateBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new StorageException("Не задан bucket");
        }
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new StorageException("Не задан objectKey");
        }
    }

    private void validateFile(Path file) {
        if (file == null) {
            throw new StorageException("Файл не задан");
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new StorageException("Файл не найден: " + file);
        }
        try {
            long size = Files.size(file);
            if (size == 0) {
                throw new StorageException("Файл пуст: " + file);
            }
            if (size > maxFileSize) {
                throw new StorageException(
                        "Файл превышает максимальный размер: " + size + " > " + maxFileSize);
            }
        } catch (IOException e) {
            throw new StorageException("Ошибка проверки файла: " + file, e);
        }
    }

    private Path buildTempPath(String objectKey) {
        try {
            Path dir = Paths.get(tempDir);
            Files.createDirectories(dir);
            String filename = Paths.get(objectKey).getFileName().toString();
            return dir.resolve(filename);
        } catch (IOException e) {
            throw new StorageException("Ошибка подготовки временного каталога: " + tempDir, e);
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "unnamed";
        }
        return value.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
    }

    private String resolveContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".step") || lower.endsWith(".stp")) return "model/step";
        if (lower.endsWith(".jt"))   return "model/jt";
        if (lower.endsWith(".x_t") || lower.endsWith(".x_b")) return "model/parasolid";
        if (lower.endsWith(".igs") || lower.endsWith(".iges")) return "model/iges";
        if (lower.endsWith(".stl"))  return "model/stl";
        if (lower.endsWith(".3dxml")) return "model/3dxml";
        if (lower.endsWith(".m3d"))  return "model/m3d";
        if (lower.endsWith(".a3d"))  return "model/a3d";
        if (lower.endsWith(".cdw"))  return "application/cdw";
        if (lower.endsWith(".spw"))  return "application/spw";
        if (lower.endsWith(".nc") || lower.endsWith(".tap") || lower.endsWith(".gcode"))
            return "text/x-gcode; charset=utf-8";
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".xml"))  return "application/xml";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".md"))   return "text/markdown; charset=utf-8";
        if (lower.endsWith(".txt"))  return "text/plain; charset=utf-8";
        if (lower.endsWith(".csv"))  return "text/csv; charset=utf-8";
        if (lower.endsWith(".zip"))  return "application/zip";
        return "application/octet-stream";
    }

    // ==================================================================
    // 7. Вспомогательные типы
    // ==================================================================

    /**
     * Разобранный внутренний URI.
     */
    public record UriParts(String bucket, String objectKey) {
    }

    /**
     * Информация об объекте.
     */
    public record ObjectInfo(
            String bucket,
            String objectKey,
            long size,
            String contentType,
            String etag,
            Instant lastModified,
            Map<String, String> userMetadata
    ) {
        /** Внутренний URI объекта. */
        public String uri() {
            return URI_SCHEME + "://" + bucket + "/" + objectKey;
        }
    }

    /**
     * Исключение библиотеки StorageClient.
     */
    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}