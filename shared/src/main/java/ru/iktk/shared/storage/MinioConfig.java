package ru.iktk.shared.storage;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация MinIO-клиента для {@link StorageClient}.
 *
 * <p>Настройки через переменные окружения / application.yml:
 * <ul>
 *   <li>{@code iktk.storage.endpoint} — MinIO endpoint (default http://localhost:9000);</li>
 *   <li>{@code iktk.storage.access-key} — access key (default iktk-minio);</li>
 *   <li>{@code iktk.storage.secret-key} — secret key (default iktk_dev_password);</li>
 * </ul>
 *
 * <p>Бин создаётся, только если не задан другой {@link MinioClient}
 * (например, в конкретном сервисе).
 */
@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient(
            @Value("${iktk.storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${iktk.storage.access-key:iktk-minio}") String accessKey,
            @Value("${iktk.storage.secret-key:iktk_dev_password}") String secretKey) {
        log.info("MinioConfig: creating MinioClient for endpoint={}, accessKey={}",
                endpoint, accessKey);
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
