package ru.iktk.integration.siemens;

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
 * Адаптер интеграции с Siemens NX / Tecnomatix / Teamcenter.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>импорт CAD-моделей (детали и сборки) из Siemens;</li>
 *   <li>извлечение структуры изделия (BOM) для сборочных единиц;</li>
 *   <li>извлечение КТЭ (конструктивно-технологических элементов);</li>
 *   <li>передачу тяжёлых файлов через {@link FileExchangeService} в MinIO.</li>
 * </ul>
 *
 * <p>Взаимодействие с Siemens возможно двумя способами:
 * <ol>
 *   <li>через открытый API NX Open / Teamcenter SOA;</li>
 *   <li>через файловый обмен STEP AP242 / JT.</li>
 * </ol>
 * Выбор способа определяется настройкой {@code iktk.siemens.mode}.
 */
@Component
public class SiemensAdapter {

    private static final Logger log = LoggerFactory.getLogger(SiemensAdapter.class);

    private final CadCamClient cadCamClient;
    private final FileExchangeService fileExchangeService;

    @Value("${iktk.siemens.mode:api}")
    private String mode;

    @Value("${iktk.siemens.endpoint:}")
    private String endpoint;

    @Value("${iktk.siemens.timeout-ms:30000}")
    private long timeoutMs;

    public SiemensAdapter(CadCamClient cadCamClient,
                         FileExchangeService fileExchangeService) {
        this.cadCamClient = cadCamClient;
        this.fileExchangeService = fileExchangeService;
    }

    /**
     * Импорт CAD-модели из Siemens.
     *
     * @param externalId идентификатор модели в Siemens (UID, артикул или имя файла)
     * @param projectId  идентификатор проекта в ИКТК
     * @return результат импорта с внутренним URI и метаданными
     */
    public CompletableFuture<ImportResult> importModel(String externalId, String projectId) {
        log.info("SiemensAdapter.importModel start: externalId={}, projectId={}, mode={}",
                externalId, projectId, mode);

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

                log.info("SiemensAdapter.importModel done: externalId={}, uri={}, isAssembly={}",
                        externalId, storedUri, isAssembly);
                return result;

            } catch (Exception e) {
                log.error("SiemensAdapter.importModel failed: externalId={}", externalId, e);
                throw new SiemensIntegrationException(
                        "Ошибка импорта модели из Siemens: " + externalId, e);
            }
        });
    }

    /**
     * Извлечение BOM сборочной единицы.
     *
     * @param storedUri внутренний URI ранее импортированной сборки
     * @return дерево состава изделия
     */
    public CompletableFuture<BomTree> extractBom(String storedUri) {
        log.info("SiemensAdapter.extractBom start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                BomTree bom = cadCamClient.extractBom(localFile);

                if (bom == null || bom.getItems().isEmpty()) {
                    log.warn("SiemensAdapter.extractBom: BOM пуст для uri={}", storedUri);
                }

                log.info("SiemensAdapter.extractBom done: uri={}, positions={}",
                        storedUri, bom == null ? 0 : bom.getItems().size());
                return bom;

            } catch (Exception e) {
                log.error("SiemensAdapter.extractBom failed: uri={}", storedUri, e);
                throw new SiemensIntegrationException(
                        "Ошибка извлечения BOM из Siemens: " + storedUri, e);
            }
        });
    }

    /**
     * Извлечение конструктивно-технологических элементов (КТЭ).
     */
    public CompletableFuture<List<Feature>> extractFeatures(String storedUri) {
        log.info("SiemensAdapter.extractFeatures start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                List<Feature> features = cadCamClient.extractFeatures(localFile);
                log.info("SiemensAdapter.extractFeatures done: uri={}, features={}",
                        storedUri, features.size());
                return features;
            } catch (Exception e) {
                log.error("SiemensAdapter.extractFeatures failed: uri={}", storedUri, e);
                throw new SiemensIntegrationException(
                        "Ошибка извлечения КТЭ из Siemens: " + storedUri, e);
            }
        });
    }

    /**
     * Проверка доступности Siemens-эндпоинта.
     */
    public boolean healthCheck() {
        try {
            return cadCamClient.ping(endpoint, timeoutMs);
        } catch (Exception e) {
            log.warn("SiemensAdapter.healthCheck failed: endpoint={}", endpoint, e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Внутренние методы
    // ------------------------------------------------------------------

    private Path fetchViaApi(String externalId) {
        log.debug("SiemensAdapter: fetch via NX Open/Teamcenter SOA, externalId={}", externalId);
        return cadCamClient.exportFromApi(endpoint, externalId, timeoutMs);
    }

    private Path fetchViaFileExchange(String externalId) {
        log.debug("SiemensAdapter: fetch via file exchange, externalId={}", externalId);
        return fileExchangeService.resolveIncoming(externalId);
    }

    private void validate(Path file) {
        if (file == null) {
            throw new SiemensIntegrationException("Siemens вернул пустой файл");
        }
        if (!file.toFile().exists() || file.toFile().length() == 0) {
            throw new SiemensIntegrationException(
                    "Файл Siemens отсутствует или пуст: " + file);
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные типы (могут быть вынесены в отдельные файлы)
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

    /** Исключение интеграции с Siemens. */
    public static class SiemensIntegrationException extends RuntimeException {
        public SiemensIntegrationException(String message) { super(message); }
        public SiemensIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}