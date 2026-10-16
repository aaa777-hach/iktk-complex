package ru.iktk.integration.dassault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.iktk.integration.common.CadCamClient;
import ru.iktk.integration.common.FileExchangeService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Адаптер интеграции с Dassault Systèmes DELMIA / 3DEXPERIENCE.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>импорт CAD-моделей (деталей и сборок) из 3DEXPERIENCE;</li>
 *   <li>извлечение структуры изделия (BOM) для сборочных единиц;</li>
 *   <li>извлечение КТЭ (конструктивно-технологических элементов);</li>
 *   <li>получение данных о связях и сопряжениях сборки (для ТП сборки);</li>
 *   <li>передачу тяжёлых файлов через {@link FileExchangeService} в MinIO.</li>
 * </ul>
 *
 * <p>Взаимодействие возможно двумя способами:
 * <ol>
 *   <li>через открытый API 3DEXPERIENCE (Web Services / REST / CAA);</li>
 *   <li>через файловый обмен STEP AP242 / JT / 3DXML.</li>
 * </ol>
 * Выбор способа определяется настройкой {@code iktk.dassault.mode}.
 *
 * <p>Особенности DELMIA/3DEXPERIENCE:
 * <ul>
 *   <li>BOM и связи хранятся в PLM-модели, извлекаются через сервисы
 *       {@code Engineering BOM} и {@code Manufacturing BOM};</li>
 *   <li>сопряжения (constraints) сборки доступны в DELMIA Assembly;</li>
 *   <li>поддерживаются 3DXML, JT, STEP AP242, IGES, Parasolid.</li>
 * </ul>
 */
@Component
public class DassaultAdapter {

    private static final Logger log = LoggerFactory.getLogger(DassaultAdapter.class);

    private final CadCamClient cadCamClient;
    private final FileExchangeService fileExchangeService;

    @Value("${iktk.dassault.mode:api}")
    private String mode;

    @Value("${iktk.dassault.endpoint:}")
    private String endpoint;

    @Value("${iktk.dassault.tenant:}")
    private String tenant;

    @Value("${iktk.dassault.timeout-ms:30000}")
    private long timeoutMs;

    public DassaultAdapter(CadCamClient cadCamClient,
                           FileExchangeService fileExchangeService) {
        this.cadCamClient = cadCamClient;
        this.fileExchangeService = fileExchangeService;
    }

    // ------------------------------------------------------------------
    // Импорт модели
    // ------------------------------------------------------------------

    /**
     * Импорт CAD-модели из 3DEXPERIENCE.
     *
     * @param externalId идентификатор модели в Dassault (PLM URN, part number или имя файла)
     * @param projectId  идентификатор проекта в ИКТК
     * @return результат импорта с внутренним URI и метаданными
     */
    public CompletableFuture<ImportResult> importModel(String externalId, String projectId) {
        log.info("DassaultAdapter.importModel start: externalId={}, projectId={}, mode={}, tenant={}",
                externalId, projectId, mode, tenant);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = "api".equalsIgnoreCase(mode)
                        ? fetchViaApi(externalId)
                        : fetchViaFileExchange(externalId);

                validate(localFile);

                String storedUri = fileExchangeService.upload(localFile, projectId);

                Map<String, Object> metadata = cadCamClient.extractMetadata(localFile);
                boolean isAssembly = Boolean.TRUE.equals(metadata.get("isAssembly"));

                ImportResult result = new ImportResult(
                        externalId,
                        storedUri,
                        metadata,
                        isAssembly
                );

                log.info("DassaultAdapter.importModel done: externalId={}, uri={}, isAssembly={}",
                        externalId, storedUri, isAssembly);
                return result;

            } catch (Exception e) {
                log.error("DassaultAdapter.importModel failed: externalId={}", externalId, e);
                throw new DassaultIntegrationException(
                        "Ошибка импорта модели из Dassault: " + externalId, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение BOM
    // ------------------------------------------------------------------

    /**
     * Извлечение BOM сборочной единицы.
     *
     * <p>Для Dassault/3DEXPERIENCE извлекается Engineering BOM:
     * состав изделия с позициями, количествами, материалами.
     */
    public CompletableFuture<BomTree> extractBom(String storedUri) {
        log.info("DassaultAdapter.extractBom start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                BomTree bom = cadCamClient.extractBom(localFile);

                if (bom == null || bom.getItems().isEmpty()) {
                    log.warn("DassaultAdapter.extractBom: BOM пуст для uri={}", storedUri);
                }

                log.info("DassaultAdapter.extractBom done: uri={}, positions={}",
                        storedUri, bom == null ? 0 : bom.getItems().size());
                return bom;

            } catch (Exception e) {
                log.error("DassaultAdapter.extractBom failed: uri={}", storedUri, e);
                throw new DassaultIntegrationException(
                        "Ошибка извлечения BOM из Dassault: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение КТЭ
    // ------------------------------------------------------------------

    /**
     * Извлечение конструктивно-технологических элементов (КТЭ).
     */
    public CompletableFuture<List<Feature>> extractFeatures(String storedUri) {
        log.info("DassaultAdapter.extractFeatures start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                List<Feature> features = cadCamClient.extractFeatures(localFile);
                log.info("DassaultAdapter.extractFeatures done: uri={}, features={}",
                        storedUri, features.size());
                return features;
            } catch (Exception e) {
                log.error("DassaultAdapter.extractFeatures failed: uri={}", storedUri, e);
                throw new DassaultIntegrationException(
                        "Ошибка извлечения КТЭ из Dassault: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение связей и сопряжений сборки
    // ------------------------------------------------------------------

    /**
     * Извлечение связей и сопряжений сборочной единицы.
     *
     * <p>Используется для проектирования ТП сборки: последовательность
     * установки деталей, базирование, крепёж.
     *
     * <p>Для Dassault доступно через DELMIA Assembly / 3DEXPERIENCE
     * Manufacturing BOM.
     */
    public CompletableFuture<List<AssemblyConnection>> extractConnections(String storedUri) {
        log.info("DassaultAdapter.extractConnections start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                List<AssemblyConnection> connections =
                        cadCamClient.extractConnections(localFile);

                log.info("DassaultAdapter.extractConnections done: uri={}, connections={}",
                        storedUri, connections.size());
                return connections;
            } catch (Exception e) {
                log.error("DassaultAdapter.extractConnections failed: uri={}", storedUri, e);
                throw new DassaultIntegrationException(
                        "Ошибка извлечения сопряжений из Dassault: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Health check
    // ------------------------------------------------------------------

    /**
     * Проверка доступности Dassault-эндпоинта.
     */
    public boolean healthCheck() {
        try {
            return cadCamClient.ping(endpoint, timeoutMs);
        } catch (Exception e) {
            log.warn("DassaultAdapter.healthCheck failed: endpoint={}, tenant={}",
                    endpoint, tenant, e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Внутренние методы
    // ------------------------------------------------------------------

    private Path fetchViaApi(String externalId) {
        log.debug("DassaultAdapter: fetch via 3DEXPERIENCE Web Services, externalId={}", externalId);
        return cadCamClient.exportFromApi(endpoint, externalId, timeoutMs);
    }

    private Path fetchViaFileExchange(String externalId) {
        log.debug("DassaultAdapter: fetch via file exchange (3DXML/JT/STEP), externalId={}",
                externalId);
        return fileExchangeService.resolveIncoming(externalId);
    }

    private void validate(Path file) {
        if (file == null) {
            throw new DassaultIntegrationException("Dassault вернул пустой файл");
        }
        if (!file.toFile().exists() || file.toFile().length() == 0) {
            throw new DassaultIntegrationException(
                    "Файл Dassault отсутствует или пуст: " + file);
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные типы
    // ------------------------------------------------------------------

    /** Результат импорта модели. */
    public static class ImportResult {
        private final String externalId;
        private final String storedUri;
        private final Map<String, Object> metadata;
        private final boolean assembly;

        public ImportResult(String externalId, String storedUri,
                            Map<String, Object> metadata, boolean assembly) {
            this.externalId = externalId;
            this.storedUri = storedUri;
            this.metadata = metadata;
            this.assembly = assembly;
        }

        public String getExternalId() { return externalId; }
        public String getStoredUri() { return storedUri; }
        public Map<String, Object> getMetadata() { return metadata; }
        public boolean isAssembly() { return assembly; }
    }

    /** Дерево состава изделия. */
    public static class BomTree {
        private final List<BomItem> items;
        public BomTree(List<BomItem> items) { this.items = items; }
        public List<BomItem> getItems() { return items; }
    }

    /** Позиция BOM. */
    public static class BomItem {
        private final String position;
        private final String designation;
        private final int quantity;
        private final String material;

        public BomItem(String position, String designation,
                       int quantity, String material) {
            this.position = position;
            this.designation = designation;
            this.quantity = quantity;
            this.material = material;
        }

        public String getPosition() { return position; }
        public String getDesignation() { return designation; }
        public int getQuantity() { return quantity; }
        public String getMaterial() { return material; }
    }

    /** Конструктивно-технологический элемент. */
    public static class Feature {
        private final String type;
        private final Map<String, Object> params;

        public Feature(String type, Map<String, Object> params) {
            this.type = type;
            this.params = params;
        }

        public String getType() { return type; }
        public Map<String, Object> getParams() { return params; }
    }

    /** Связь/сопряжение сборки. */
    public static class AssemblyConnection {
        private final String type;
        private final String part1;
        private final String part2;
        private final Map<String, Object> params;

        public AssemblyConnection(String type, String part1, String part2,
                                  Map<String, Object> params) {
            this.type = type;
            this.part1 = part1;
            this.part2 = part2;
            this.params = params;
        }

        public String getType() { return type; }
        public String getPart1() { return part1; }
        public String getPart2() { return part2; }
        public Map<String, Object> getParams() { return params; }
    }

    /** Исключение интеграции с Dassault. */
    public static class DassaultIntegrationException extends RuntimeException {
        public DassaultIntegrationException(String message) { super(message); }
        public DassaultIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}