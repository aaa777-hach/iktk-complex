package ru.iktk.recommendation.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Базовый класс интеграционных тестов recommendation-service.
 *
 * <p>Поднимает два контейнера:
 * <ul>
 *   <li>{@code postgres:16} — БД, Flyway применит V1, ddl-auto: validate проверит модель;</li>
 *   <li>{@code rabbitmq:3-management} — брокер для @RabbitListener и публикации событий.</li>
 * </ul>
 *
 * <p>Все контейнеры {@code static} — переиспользуются между тестами одного класса.
 * Порт — случайный ({@code getMappedPort}), с локальными 5432/5433/5672 не конфликтует.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("iktk_recommendation")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management")
                .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("spring.rabbitmq.virtual-host", () -> "/");

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // Feign-клиент не должен ходить на реальный 8094 в интеграционных тестах
        registry.add("iktk.clients.equipment-service.url", () -> "http://localhost:0");
    }
}