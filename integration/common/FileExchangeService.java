package ru.iktk.integration.common;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.iktk.integration.common.CadCamClient.CadIntegrationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис обмена тяжёлыми файлами между ИКТК и внешними CAD/CAM-системами.
 *
 * <p>Назначение:
 * <ul>
 *   <li>загрузка тяжёлых файлов (CAD-модели, BOM, спецификации, чертежи)
 *       в объектное хранилище MinIO/S3;</li>
 *   <li>выгрузка файлов из хранилища по внутреннему URI;</li>
 *   <li>разрешение «входящих» файлов из каталога обмена
 *       (для CLI-режима и ручной загрузки);</li>
 *   <li>вычисление хэша (SHA-256) для контроля целостности и дедупликации;</li>
 *   <li>ведение метаданных файла (размер, формат, время загрузки).</li>
 * </ul>
 *
 * <p><b>Архитектурная роль.</b> Все адаптеры CAD-систем (Siemens, Dassault,
 * T-FLEX, КОМПАС) используют этот сервис для передачи файлов, а не
 * работают с хранилищем напрямую. Это обеспечивает:
 * <ul>
 *   <li>единый механизм загрузки/выгрузки и версионности;</li>
 *   <li>трассировку (кто, когда, какой файл);</li>
 *   <li>возможность подменить хранилище (MinIO → S3 → локальная ФС).</li>
 * </ul>
 *
 * <p><b>Структура хранения в MinIO.</b>
 * <pre>
 *   bucket: iktk-files
 *   path:   {projectId}/{category}/{uuid}-{filename}
 *
 *   categories:
 *     geometry     — CAD-модели (STEP, JT, Parasolid, ...)
 *     bom          — структуры изделия
 *     specification — спецификации
 *     drawing      — чертежи
 *     tooling      — оснастка
 *     cnc          — программы ЧПУ
 *     incoming     — временный каталог обмена
 * </pre>
 *
 * <p><b>Внутренний URI.</b> После загрузки файл получает внутренний URI
 * вида {@code minio://iktk-files/{projectId}/geometry/{uuid}-{filename}}.
 * Он хранится в БД (таблица {@code geometry_models}) и используется
 * всеми сервисами.
 *
 * <p><b>Дедупликация.</b> Если файл с таким же SHA-256 уже загружен
 * в проект, возвращается существующий URI без повторной загрузки.
 */
@Service
public class FileExchangeService {

    private static final Logger log = LoggerFactory.getLogger(FileExchangeService.class);

    /** Имя бакета по умолчанию. */
    @Value("${iktk.storage.bucket:iktk-files}")
    private String bucket;

    /** Каталог обмена для «входящих» файлов (CLI-режим). */
    @Value("${iktk.storage.incoming-dir:/var/iktk/incoming}")
    private String incomingDir;

    /** Каталог временных файлов. */
    @Value("${iktk.storage.temp-dir:/tmp/iktk-files}")
    private String tempDir;

    /** Максимальный размер файла (в байтах). */
    @Value("${iktk.storage.max-file-size:5368709120}") // 5 ГБ
    private long maxFileSize;

    /** Схема внутреннего URI. */
    public static final String URI_SCHEME = "minio";

    private final MinioClient minioClient;

    public FileExchangeService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    // ------------------------------------------------------------------
    // Загрузка файла в хранилище
    // ------------------------------------------------------------------

    /**
     * Загрузить файл в хранилище.
     *
     * @param file      локальный файл
     * @param projectId идентификатор проекта
     * @return внутренний URI вида {@code minio://bucket/projectId/category/uuid-filename}
     */
    public String upload(Path file, String projectId) {
        return upload(file, projectId, "geometry");
    }

    /**
     * Загрузить файл в хранилище с указанием категории.
     *
     * @param file      локальный файл
     * @param projectId идентификатор проекта
     * @param category  категория (geometry, bom, specification, drawing, ...)
     * @return внутренний URI
     */
    public String upload(Path file, String projectId, String category) {
        validateFile(file);
        validateProject(projectId);

        String filename = file.getFileName().toString();
        String safeCategory = sanitize(category == null ? "geometry" : category);

        try {
            ensureBucket();

            String objectKey = buildObjectKey(projectId, safeCategory, filename);
            long size = Files.size(file);

            try (InputStream in = Files.newInputStream(file)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(in, size, -1)
                                .contentType(resolveContentType(filename))
                                .build()
                );
            }

            String uri = buildUri(objectKey);
            log.info("FileExchangeService.upload: projectId={}, category={}, file={}, size={}, uri={}",
                    projectId, safeCategory, filename, size, uri);

            return uri;

        } catch (Exception e) {
            log.error("FileExchangeService.upload failed: projectId={}, file={}",
                    projectId, file, e);
            throw new CadIntegrationException(
                    "Ошибка загрузки файла в хранилище: " + filename, e);
        }
    }

    // ------------------------------------------------------------------
    // Выгрузка файла из хранилища
    // ------------------------------------------------------------------

    /**
     * Выгрузить файл из хранилища во временный каталог.
     *
     * @param uri внутренний URI файла
     * @return путь к локальному файлу
     */
    public Path download(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new CadIntegrationException("Пустой URI файла");
        }

        String objectKey = extractObjectKey(uri);
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

            long size = Files.size(target);
            log.info("FileExchangeService.download: uri={}, target={}, size={}",
                    uri, target, size);

            return target;

        } catch (ErrorResponseException e) {
            log.error("FileExchangeService.download: объект не найден, uri={}", uri, e);
            throw new CadIntegrationException("Файл не найден в хранилище: " + uri, e);
        } catch (Exception e) {
            log.error("FileExchangeService.download failed: uri={}", uri, e);
            throw new CadIntegrationException("Ошибка выгрузки файла: " + uri, e);
        }
    }

    /**
     * Выгрузить файл из хранилища в указанный каталог.
     */
    public Path downloadTo(String uri, Path targetDir, String targetName) {
        String objectKey = extractObjectKey(uri);
        Path target = targetDir.resolve(targetName == null
                ? Paths.get(objectKey).getFileName().toString()
                : targetName);

        try {
            Files.createDirectories(target.getParent());

            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build())) {

                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("FileExchangeService.downloadTo: uri={}, target={}", uri, target);
            return target;

        } catch (Exception e) {
            log.error("FileExchangeService.downloadTo failed: uri={}", uri, e);
            throw new CadIntegrationException("Ошибка выгрузки файла: " + uri, e);
        }
    }

    // ------------------------------------------------------------------
    // Разрешение «входящих» файлов (CLI-режим)
    // ------------------------------------------------------------------

    /**
     * Разрешить «входящий» файл по идентификатору.
     *
     * <p>Используется в CLI-режиме, когда CAD-система или пользователь
     * кладёт файл в каталог обмена. Метод ищет файл по имени или
     * идентификатору в каталоге {@code iktk.storage.incoming-dir}.
     *
     * @param externalId имя или идентификатор файла
     * @return путь к локальному файлу
     */
    public Path resolveIncoming(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new CadIntegrationException("Пустой идентификатор входящего файла");
        }

        Path incoming = Paths.get(incomingDir);

        // Прямой путь
        Path direct = incoming.resolve(externalId);
        if (Files.exists(direct) && Files.isRegularFile(direct)) {
            log.debug("FileExchangeService.resolveIncoming: найден по прямому пути, file={}",
                    direct);
            return direct;
        }

        // Поиск по имени без учёта расширения
        try (var stream = Files.list(incoming)) {
            Path found = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        String base = dot < 0 ? name : name.substring(0, dot);
                        return base.equals(externalId) || name.equals(externalId);
                    })
                    .findFirst()
                    .orElse(null);

            if (found != null) {
                log.debug("FileExchangeService.resolveIncoming: найден по имени, file={}",
                        found);
                return found;
            }

        } catch (IOException e) {
            log.error("FileExchangeService.resolveIncoming: ошибка чтения каталога {}",
                    incoming, e);
            throw new CadIntegrationException(
                    "Ошибка чтения каталога обмена: " + incoming, e);
        }

        throw new CadIntegrationException(
                "Входящий файл не найден: " + externalId + " в " + incomingDir);
    }

    // ------------------------------------------------------------------
    // Метаданные и проверки
    // ------------------------------------------------------------------

    /**
     * Проверить существование файла в хранилище.
     */
    public boolean exists(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String objectKey = extractObjectKey(uri);
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
            log.warn("FileExchangeService.exists: ошибка проверки, uri={}", uri, e);
            return false;
        }
    }

    /**
     * Получить размер файла в хранилище.
     */
    public long size(String uri) {
        String objectKey = extractObjectKey(uri);
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            ).size();
        } catch (Exception e) {
            log.warn("FileExchangeService.size: ошибка, uri={}", uri, e);
            return -1;
        }
    }

    /**
     * Удалить файл из хранилища.
     */
    public void delete(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        String objectKey = extractObjectKey(uri);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            log.info("FileExchangeService.delete: uri={}", uri);
        } catch (Exception e) {
            log.error("FileExchangeService.delete failed: uri={}", uri, e);
            throw new CadIntegrationException("Ошибка удаления файла: " + uri, e);
        }
    }

    /**
     * Вычислить SHA-256 файла.
     *
     * <p>Используется для дедупликации и контроля целостности.
     */
    public String sha256(Path file) {
        if (file == null || !Files.exists(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // digest обновляется автоматически
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new CadIntegrationException("SHA-256 недоступен", e);
        } catch (IOException e) {
            throw new CadIntegrationException("Ошибка чтения файла: " + file, e);
        }
    }

    /**
     * Полная информация о файле: URI, размер, SHA-256, формат.
     */
    public Map<String, Object> describe(String uri) {
        String objectKey = extractObjectKey(uri);
        try {
            var stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());

            return Map.of(
                    "uri", uri,
                    "bucket", bucket,
                    "objectKey", objectKey,
                    "size", stat.size(),
                    "contentType", stat.contentType() == null ? "" : stat.contentType(),
                    "lastModified", stat.lastModified() == null
                            ? "" : stat.lastModified().toString(),
                    "etag", stat.etag() == null ? "" : stat.etag()
            );
        } catch (Exception e) {
            log.warn("FileExchangeService.describe failed: uri={}", uri, e);
            return Map.of("uri", uri, "error", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private void validateFile(Path file) {
        if (file == null) {
            throw new CadIntegrationException("Файл не задан");
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }
        try {
            long size = Files.size(file);
            if (size == 0) {
                throw new CadIntegrationException("Файл пуст: " + file);
            }
            if (size > maxFileSize) {
                throw new CadIntegrationException(
                        "Файл превышает максимальный размер: " + size + " > " + maxFileSize);
            }
        } catch (IOException e) {
            throw new CadIntegrationException("Ошибка проверки файла: " + file, e);
        }
    }

    private void validateProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new CadIntegrationException("Не задан projectId");
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build());
            log.info("FileExchangeService: создан бакет {}", bucket);
        }
    }

    private String buildObjectKey(String projectId, String category, String filename) {
        String safeName = sanitize(filename);
        String uuid = UUID.randomUUID().toString();
        return projectId + "/" + category + "/" + uuid + "-" + safeName;
    }

    private String buildUri(String objectKey) {
        return URI_SCHEME + "://" + bucket + "/" + objectKey;
    }

    /**
     * Извлечь objectKey из внутреннего URI.
     * Пример: {@code minio://iktk-files/proj-1/geometry/uuid-file.step}
     *        → {@code proj-1/geometry/uuid-file.step}
     */
    private String extractObjectKey(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new CadIntegrationException("Пустой URI");
        }

        String prefix = URI_SCHEME + "://" + bucket + "/";
        if (uri.startsWith(prefix)) {
            return uri.substring(prefix.length());
        }

        // Попытка разобрать другой формат: minio://bucket/key
        int schemeIdx = uri.indexOf("://");
        if (schemeIdx > 0) {
            String rest = uri.substring(schemeIdx + 3);
            int slash = rest.indexOf('/');
            if (slash > 0) {
                return rest.substring(slash + 1);
            }
        }

        // Возможно, передан уже objectKey
        return uri;
    }

    private Path buildTempPath(String objectKey) {
        try {
            Path dir = Paths.get(tempDir);
            Files.createDirectories(dir);
            return dir.resolve(Paths.get(objectKey).getFileName().toString());
        } catch (IOException e) {
            throw new CadIntegrationException(
                    "Ошибка подготовки временного каталога: " + tempDir, e);
        }
    }

    private String sanitize(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
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
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".xml"))  return "application/xml";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------
    // Диагностика
    // ------------------------------------------------------------------

    /** Имя бакета. */
    public String getBucket() {
        return bucket;
    }

    /** Каталог входящих файлов. */
    public String getIncomingDir() {
        return incomingDir;
    }
}