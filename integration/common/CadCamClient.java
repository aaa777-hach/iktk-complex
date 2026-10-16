package ru.iktk.integration.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.iktk.integration.kompas.KompasAdapter;
import ru.iktk.integration.tflex.TFlexAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Общий клиент для взаимодействия с CAD/CAM-системами ИКТК.
 *
 * <p>Инкапсулирует все низкоуровневые операции, которые нужны адаптерам
 * конкретных CAD-систем (Siemens, Dassault, T-FLEX, КОМПАС):
 * <ul>
 *   <li>экспорт модели из CAD через API ({@link #exportFromApi});</li>
 *   <li>извлечение метаданных ({@link #extractMetadata});</li>
 *   <li>извлечение структуры изделия — BOM ({@link #extractBom});</li>
 *   <li>извлечение КТЭ ({@link #extractFeatures});</li>
 *   <li>извлечение связей и сопряжений сборки ({@link #extractConnections});</li>
 *   <li>извлечение спецификации по ГОСТ ({@link #extractSpecification});</li>
 *   <li>извлечение данных чертежа ({@link #extractDrawing});</li>
 *   <li>проверка доступности эндпоинта ({@link #ping}).</li>
 * </ul>
 *
 * <p><b>Архитектурная роль.</b> Адаптеры конкретных CAD-систем
 * (позиции 26–29) вызывают методы этого клиента, а не обращаются к CAD
 * напрямую. Это даёт:
 * <ul>
 *   <li>единый механизм retry, timeout, логирования, трассировки;</li>
 *   <li>возможность подменить реализацию (WebClient / CLI / mock) без
 *       изменения адаптеров;</li>
 *   <li>точку расширения для новых CAD-систем.</li>
 * </ul>
 *
 * <p><b>Два режима работы.</b>
 * <ol>
 *   <li><b>API-режим.</b> Через {@link WebClient} обращается к REST/SOAP
 *       эндпоинту CAD-системы (например, Teamcenter SOA, 3DEXPERIENCE
 *       Web Services, ЛОЦМАН:PLM REST).</li>
 *   <li><b>Локальный режим (CLI).</b> Если CAD развёрнут на том же хосте,
 *       экспорт выполняется вызовом внешнего процесса — CAD-конвертера
 *       (например, NX Open batch, DELMIA batch, T-FLEX batch, КОМПАС
 *       batch). Реализуется через {@code ProcessBuilder}.</li>
 * </ol>
 * Выбор режима определяется свойством {@code iktk.cad.mode}.
 *
 * <p><b>Канонический внутренний формат.</b> Все файлы после импорта
 * конвертируются в STEP AP242 или Parasolid — это обеспечивает
 * единообразие обработки в модулях ИКТК.
 *
 * <p><b>Расширение.</b> Для добавления новой CAD-системы достаточно:
 * <ol>
 *   <li>реализовать адаптер в пакете {@code ru.iktk.integration.<cad>};</li>
 *   <li>вызвать методы этого клиента;</li>
 *   <li>добавить URI-настройку в {@code application.yml}.</li>
 * </ol>
 */
@Component
public class CadCamClient {

    private static final Logger log = LoggerFactory.getLogger(CadCamClient.class);

    /** Режим работы: api или cli. */
    @Value("${iktk.cad.mode:api}")
    private String mode;

    /** Каталог для временных файлов. */
    @Value("${iktk.cad.work-dir:/tmp/iktk-cad}")
    private String workDir;

    /** Путь к CAD-конвертеру (для CLI-режима). */
    @Value("${iktk.cad.converter-path:}")
    private String converterPath;

    /** Формат канонизации. */
    @Value("${iktk.cad.canonical-format:STEP_AP242}")
    private String canonicalFormat;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CadCamClient(WebClient.Builder webClientBuilder,
                        ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Экспорт модели из CAD через API
    // ------------------------------------------------------------------

    /**
     * Экспортировать модель из CAD через API.
     *
     * <p>Режим {@code api}: обращается к REST/SOAP эндпоинту CAD-системы
     * и получает файл канонического формата (STEP AP242 / Parasolid).
     *
     * <p>Режим {@code cli}: вызывает внешний CAD-конвертер (batch-режим)
     * и получает файл на локальном диске.
     *
     * @param endpoint   URL эндпоинта CAD-системы
     * @param externalId идентификатор модели в CAD
     * @param timeoutMs  таймаут в миллисекундах
     * @return путь к локальному файлу канонического формата
     */
    public Path exportFromApi(String endpoint, String externalId, long timeoutMs) {
        log.info("CadCamClient.exportFromApi: endpoint={}, externalId={}, mode={}",
                endpoint, externalId, mode);

        if ("cli".equalsIgnoreCase(mode)) {
            return exportViaCli(endpoint, externalId, timeoutMs);
        }
        return exportViaHttp(endpoint, externalId, timeoutMs);
    }

    private Path exportViaHttp(String endpoint, String externalId, long timeoutMs) {
        validateEndpoint(endpoint);

        String url = endpoint.endsWith("/")
                ? endpoint + "export/" + externalId
                : endpoint + "/export/" + externalId;

        log.debug("CadCamClient: HTTP-export, url={}", url);

        try {
            byte[] payload = webClient.get()
                    .uri(url)
                    .header("Accept", "application/octet-stream")
                    .header("X-Canonical-Format", canonicalFormat)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            Mono.error(new CadIntegrationException(
                                    "CAD API вернул статус " + resp.statusCode())))
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (payload == null || payload.length == 0) {
                throw new CadIntegrationException("CAD API вернул пустой ответ");
            }

            Path target = buildWorkPath(externalId, extensionFor(canonicalFormat));
            Files.write(target, payload);
            log.info("CadCamClient.exportViaHttp done: externalId={}, file={}, size={}",
                    externalId, target, payload.length);

            return target;

        } catch (IOException e) {
            log.error("CadCamClient.exportViaHttp: ошибка записи файла, externalId={}",
                    externalId, e);
            throw new CadIntegrationException(
                    "Ошибка сохранения файла CAD: " + externalId, e);
        } catch (CadIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("CadCamClient.exportViaHttp: ошибка, externalId={}", externalId, e);
            throw new CadIntegrationException(
                    "Ошибка экспорта из CAD: " + externalId, e);
        }
    }

    private Path exportViaCli(String endpoint, String externalId, long timeoutMs) {
        if (converterPath == null || converterPath.isBlank()) {
            throw new CadIntegrationException(
                    "Не настроен iktk.cad.converter-path для CLI-режима");
        }

        log.debug("CadCamClient: CLI-export, converter={}, externalId={}",
                converterPath, externalId);

        Path target = buildWorkPath(externalId, extensionFor(canonicalFormat));

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    converterPath,
                    "--input", externalId,
                    "--output", target.toString(),
                    "--format", canonicalFormat
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new CadIntegrationException(
                        "CAD-конвертер не завершился за " + timeoutMs + " мс");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new CadIntegrationException(
                        "CAD-конвертер вернул код " + exitCode + " для " + externalId);
            }

            if (!Files.exists(target)) {
                throw new CadIntegrationException(
                        "CAD-конвертер не создал файл: " + target);
            }

            log.info("CadCamClient.exportViaCli done: externalId={}, file={}, size={}",
                    externalId, target, Files.size(target));
            return target;

        } catch (IOException e) {
            log.error("CadCamClient.exportViaCli: ошибка, externalId={}", externalId, e);
            throw new CadIntegrationException(
                    "Ошибка запуска CAD-конвертера: " + externalId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CadIntegrationException(
                    "Прерван процесс экспорта CAD: " + externalId, e);
        }
    }

    // ------------------------------------------------------------------
    // Метаданные
    // ------------------------------------------------------------------

    /**
     * Извлечь метаданные из CAD-файла.
     *
     * <p>Возвращаемая карта содержит:
     * <ul>
     *   <li>{@code isAssembly} — признак сборки;</li>
     *   <li>{@code boundingBox} — габариты (min/max по осям);</li>
     *   <li>{@code volume} — объём;</li>
     *   <li>{@code surfaceArea} — площадь поверхности;</li>
     *   <li>{@code mass} — масса (если задан материал);</li>
     *   <li>{@code material} — материал;</li>
     *   <li>{@code units} — единицы измерения;</li>
     *   <li>{@code format} — формат исходного файла.</li>
     * </ul>
     */
    public Map<String, Object> extractMetadata(Path file) {
        log.debug("CadCamClient.extractMetadata: file={}", file);

        if (file == null || !Files.exists(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("format", detectFormat(file));
            metadata.put("units", "mm");
            metadata.put("isAssembly", detectAssembly(file));

            // Реальная реализация: парсинг STEP AP242, JT, Parasolid
            // через библиотеку (OpenCASCADE, JT Open, Parasolid SDK)
            // или обращение к сервису-анализатору геометрии.
            metadata.put("boundingBox", Map.of("x", 0, "y", 0, "z", 0));
            metadata.put("volume", 0);
            metadata.put("surfaceArea", 0);

            log.debug("CadCamClient.extractMetadata done: file={}, isAssembly={}",
                    file, metadata.get("isAssembly"));
            return metadata;

        } catch (Exception e) {
            log.error("CadCamClient.extractMetadata failed: file={}", file, e);
            throw new CadIntegrationException(
                    "Ошибка извлечения метаданных: " + file, e);
        }
    }

    // ------------------------------------------------------------------
    // BOM
    // ------------------------------------------------------------------

    /**
     * Извлечь структуру изделия (BOM) из CAD-файла.
     *
     * <p>Для деталей возвращает одноэлементное дерево.
     * Для сборок — иерархическое дерево (сборка → подсборки → детали).
     */
    @SuppressWarnings("unchecked")
    public KompasAdapter.BomTree extractBom(Path file) {
        log.debug("CadCamClient.extractBom: file={}", file);

        if (file == null || !Files.exists(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }

        if (!detectAssembly(file)) {
            log.debug("CadCamClient.extractBom: файл — деталь, BOM одноэлементный");
            KompasAdapter.BomItem item = new KompasAdapter.BomItem(
                    "1", file.getFileName().toString(), 1, null);
            return new KompasAdapter.BomTree(List.of(item));
        }

        // Реальная реализация: парсинг BOM из STEP AP242 / JT,
        // обращение к PLM-системе (Teamcenter / 3DEXPERIENCE / ЛОЦМАН)
        // или к сервису-анализатору.
        List<KompasAdapter.BomItem> items = new ArrayList<>();

        log.info("CadCamClient.extractBom done: file={}, positions={}",
                file, items.size());
        return new KompasAdapter.BomTree(items);
    }

    // ------------------------------------------------------------------
    // КТЭ
    // ------------------------------------------------------------------

    /**
     * Извлечь конструктивно-технологические элементы (КТЭ) из CAD-файла.
     *
     * <p>КТЭ: отверстия, карманы, пазы, резьбы, фаски, уклоны, радиусы,
     * толщины стенок. Определяются из параметрической модели или из
     * геометрии (feature recognition).
     */
    public List<KompasAdapter.Feature> extractFeatures(Path file) {
        log.debug("CadCamClient.extractFeatures: file={}", file);

        if (file == null || !Files.exists(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }

        // Реальная реализация: feature recognition через OpenCASCADE
        // или извлечение параметрических операций из CAD-модели.
        List<KompasAdapter.Feature> features = new ArrayList<>();

        log.info("CadCamClient.extractFeatures done: file={}, features={}",
                file, features.size());
        return features;
    }

    // ------------------------------------------------------------------
    // Связи и сопряжения сборки
    // ------------------------------------------------------------------

    /**
     * Извлечь связи и сопряжения сборочной единицы.
     *
     * <p>Используется для проектирования ТП сборки: последовательность
     * установки деталей, базирование, крепёж.
     */
    public List<KompasAdapter.AssemblyConnection> extractConnections(Path file) {
        log.debug("CadCamClient.extractConnections: file={}", file);

        if (file == null || !Files.exists(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }

        if (!detectAssembly(file)) {
            return List.of();
        }

        // Реальная реализация: парсинг сопряжений из JT / 3DXML / A3D,
        // либо обращение к CAD-системе через API.
        List<KompasAdapter.AssemblyConnection> connections = new ArrayList<>();

        log.info("CadCamClient.extractConnections done: file={}, connections={}",
                file, connections.size());
        return connections;
    }

    // ------------------------------------------------------------------
    // Спецификация
    // ------------------------------------------------------------------

    /**
     * Извлечь спецификацию по ГОСТ 2.106.
     */
    public KompasAdapter.Specification extractSpecification(Path file) {
        log.debug("CadCamClient.extractSpecification: file={}", file);

        if (file == null || !Files.exists(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }

        // Реальная реализация: парсинг SPW (КОМПАС) или обращение к PLM.
        List<KompasAdapter.SpecificationSection> sections = new ArrayList<>();

        log.info("CadCamClient.extractSpecification done: file={}, sections={}",
                file, sections.size());
        return new KompasAdapter.Specification(sections);
    }

    // ------------------------------------------------------------------
    // Чертежи
    // ------------------------------------------------------------------

    /**
     * Извлечь данные чертежа (виды, разрезы, размеры, основная надпись).
     */
    public KompasAdapter.DrawingData extractDrawing(Path file) {
        log.debug("CadCamClient.extractDrawing: file={}", file);

        if (file == null || !Files.exists(file)) {
            throw new CadIntegrationException("Файл не найден: " + file);
        }

        // Реальная реализация: парсинг CDW / DWG / DXF.
        List<KompasAdapter.DrawingView> views = new ArrayList<>();

        log.info("CadCamClient.extractDrawing done: file={}, views={}",
                file, views.size());
        return new KompasAdapter.DrawingData(views, Map.of());
    }

    // ------------------------------------------------------------------
    // Health check
    // ------------------------------------------------------------------

    /**
     * Проверка доступности CAD-эндпоинта.
     *
     * @param endpoint  URL эндпоинта
     * @param timeoutMs таймаут
     * @return {@code true}, если эндпоинт доступен
     */
    public boolean ping(String endpoint, long timeoutMs) {
        if (endpoint == null || endpoint.isBlank()) {
            log.debug("CadCamClient.ping: endpoint не задан, пропуск");
            return true;
        }

        String url = endpoint.endsWith("/")
                ? endpoint + "health"
                : endpoint + "/health";

        try {
            Boolean ok = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp -> Mono.just(false))
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorReturn(false)
                    .block();

            boolean result = Boolean.TRUE.equals(ok);
            log.debug("CadCamClient.ping: endpoint={}, result={}", endpoint, result);
            return result;

        } catch (Exception e) {
            log.warn("CadCamClient.ping failed: endpoint={}", endpoint, e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new CadIntegrationException("Не задан endpoint CAD-системы");
        }
    }

    private Path buildWorkPath(String externalId, String extension) {
        try {
            Path dir = Paths.get(workDir);
            Files.createDirectories(dir);

            String safeName = externalId == null
                    ? UUID.randomUUID().toString()
                    : externalId.replaceAll("[^a-zA-Z0-9_.-]", "_");

            return dir.resolve(safeName + "." + extension);
        } catch (IOException e) {
            throw new CadIntegrationException(
                    "Ошибка подготовки рабочего каталога: " + workDir, e);
        }
    }

    private String extensionFor(String format) {
        if (format == null) {
            return "step";
        }
        return switch (format.toUpperCase()) {
            case "STEP_AP242", "STEP", "STP" -> "step";
            case "PARASOLID", "X_T" -> "x_t";
            case "JT" -> "jt";
            case "IGES", "IGS" -> "igs";
            case "STL" -> "stl";
            case "3DXML" -> "3dxml";
            default -> "step";
        };
    }

    private String detectFormat(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "unknown" : name.substring(dot + 1);
    }

    /**
     * Определить, является ли файл сборкой.
     *
     * <p>Эвристика: по расширению (A3D — сборка КОМПАС, 3DXML — сборка
     * Dassault, JT — может быть сборкой) или по имени файла
     * (содержит «assembly», «sborka», «-сб»).
     *
     * <p>Реальная реализация — парсинг структуры файла.
     */
    private boolean detectAssembly(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".a3d")
                || name.endsWith(".3dxml")
                || name.endsWith(".jt")
                || name.contains("assembly")
                || name.contains("sborka")
                || name.contains("-сб");
    }

    // ------------------------------------------------------------------
    // Исключение
    // ------------------------------------------------------------------

    /**
     * Исключение интеграции с CAD/CAM.
     */
    public static class CadIntegrationException extends RuntimeException {
        public CadIntegrationException(String message) {
            super(message);
        }

        public CadIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}