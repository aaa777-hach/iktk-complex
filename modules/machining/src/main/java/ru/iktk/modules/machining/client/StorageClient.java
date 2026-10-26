package ru.iktk.modules.machining.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Клиент хранилища УП (MinIO/S3).
 *
 * <p>В текущей версии — заглушка: логирует операции и возвращает синтетический URI.
 * Реальная интеграция с MinIO/S3 — в отдельной сессии.
 */
@Component
public class StorageClient {

    private static final Logger log = LoggerFactory.getLogger(StorageClient.class);

    @Value("${iktk.machining.storage.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${iktk.machining.storage.bucket:iktk}")
    private String bucket;

    /**
     * Загрузить текстовый файл (например, G-код).
     *
     * @return URI сохранённого объекта
     */
    public String uploadText(String text, String projectId, String category, String name) {
        String uri = String.format("%s/%s/%s/%s/%s", endpoint, bucket, projectId, category, name);
        int bytes = text == null ? 0 : text.getBytes().length;
        log.info("StorageClient.uploadText (STUB): uri={}, bytes={}", uri, bytes);
        return uri;
    }

    /**
     * Удалить объект по URI.
     */
    public void delete(String uri) {
        log.info("StorageClient.delete (STUB): uri={}", uri);
    }
}