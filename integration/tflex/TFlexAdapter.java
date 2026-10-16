package ru.iktk.integration.tflex;

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
 * Адаптер интеграции с T-FLEX CAD / T-FLEX DOCs (АО «Топ Системы»).
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>импорт CAD-моделей (деталей и сборок) из T-FLEX CAD;</li>
 *   <li>извлечение структуры изделия (BOM) из T-FLEX DOCs / сборки;</li>
 *   <li>извлечение КТЭ (конструктивно-технологических элементов);</li>
 *   <li>получение связей и сопряжений сборки для проектирования ТП сборки;</li>
 *   <li>передачу тяжёлых файлов через {@link FileExchangeService} в MinIO.</li>
 * </ul>
 *
 * <p><b>Роль в импортозамещении.</b> Согласно {@code final_TZ.md} (ред. 3,
 * п. 5) принята модель частичного импортозамещения: ядро — отечественное
 * ПО и ОС, интеграции — любые. T-FLEX относится к отечественным CAD-системам,
 * поэтому является приоритетным источником геометрии для предприятий,
 * работающих в контуре импортозамещения.
 *
 * <p>Взаимодействие возможно двумя способами:
 * <ol>
 *   <li>через API T-FLEX DOCs (PLM) / T-FLEX CAD API (COM/ActiveX, .NET);</li>
 *   <li>через файловый обмен: GRB, FRW, STEP AP242, IGES, JT, Parasolid, STL.</li>
 * </ol>
 * Выбор способа определяется настройкой {@code iktk.tflex.mode}.
 *
 * <p>Особенности T-FLEX:
 * <ul>
 *   <li>параметрическая модель — КТЭ извлекаются как параметрические элементы;</li>
 *   <li>BOM и состав изделия — через T-FLEX DOCs (PLM);</li>
 *   <li>сборки — через T-FLEX CAD Assembly, сопряжения — в модели сборки;</li>
 *   <li>поддерживаются ГОСТ-форматы чертежей и спецификаций.</li>
 * </ul>
 */
@Component
public class TFlexAdapter {

    private static final Logger log = LoggerFactory.getLogger(TFlexAdapter.class);

    private final CadCamClient cadCamClient;
    private final FileExchangeService fileExchangeService;

    @Value("${iktk.tflex.mode:api}")
    private String mode;

    @Value("${iktk.tflex.endpoint:}")
    private String endpoint;

    @Value("${iktk.tflex.docs-endpoint:}")
    private String docsEndpoint;

    @Value("${iktk.tflex.timeout-ms:30000}")
    private long timeoutMs;

    public TFlexAdapter(CadCamClient cadCamClient,
                        FileExchangeService fileExchangeService) {
        this.cadCamClient = cadCamClient;
        this.fileExchangeService = fileExchangeService;
    }

    // ------------------------------------------------------------------
    // Импорт модели
    // ------------------------------------------------------------------

    /**
     * Импорт CAD-модели из T-FLEX CAD.
     *
     * @param externalId идентификатор модели в T-FLEX (имя документа,
     *                   GUID, URN в DOCs или имя файла)
     * @param projectId  идентификатор проекта в ИКТК
     * @return результат импорта с внутренним URI и метаданными
     */
    public CompletableFuture<ImportResult> importModel(String externalId, String projectId) {
        log.info("TFlexAdapter.importModel start: externalId={}, projectId={}, mode={}",
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

                log.info("TFlexAdapter.importModel done: externalId={}, uri={}, isAssembly={}",
                        externalId, storedUri, isAssembly);
                return result;

            } catch (Exception e) {
                log.error("TFlexAdapter.importModel failed: externalId={}", externalId, e);
                throw new TFlexIntegrationException(
                        "Ошибка импорта модели из T-FLEX: " + externalId, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение BOM
    // ------------------------------------------------------------------

    /**
     * Извлечение BOM сборочной единицы.
     *
     * <p>Для T-FLEX состав изделия извлекается через T-FLEX DOCs (PLM)
     * или из модели сборки T-FLEX CAD. Поддерживается чтение
     * ГОСТ-спецификаций.
     */
    public CompletableFuture<BomTree> extractBom(String storedUri) {
        log.info("TFlexAdapter.extractBom start: uri={}, docsEndpoint={}",
                storedUri, docsEndpoint);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                BomTree bom = cadCamClient.extractBom(localFile);

                if (bom == null || bom.getItems().isEmpty()) {
                    log.warn("TFlexAdapter.extractBom: BOM пуст для uri={}", storedUri);
                }

                log.info("TFlexAdapter.extractBom done: uri={}, positions={}",
                        storedUri, bom == null ? 0 : bom.getItems().size());
                return bom;

            } catch (Exception e) {
                log.error("TFlexAdapter.extractBom failed: uri={}", storedUri, e);
                throw new TFlexIntegrationException(
                        "Ошибка извлечения BOM из T-FLEX: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение КТЭ
    // ------------------------------------------------------------------

    /**
     * Извлечение конструктивно-технологических элементов (КТЭ).
     *
     * <p>В T-FLEX модель параметрическая: КТЭ определяются как элементы
     * построения (отверстия, карманы, пазы, фаски, уклоны, радиусы и т.п.)
     * и извлекаются с параметрами.
     */
    public CompletableFuture<List<Feature>> extractFeatures(String storedUri) {
        log.info("TFlexAdapter.extractFeatures start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                List<Feature> features = cadCamClient.extractFeatures(localFile);
                log.info("TFlexAdapter.extractFeatures done: uri={}, features={}",
                        storedUri, features.size());
                return features;
            } catch (Exception e) {
                log.error("TFlexAdapter.extractFeatures failed: uri={}", storedUri, e);
                throw new TFlexIntegrationException(
                        "Ошибка извлечения КТЭ из T-FLEX: " + storedUri, e);
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
     */
    public CompletableFuture<List<AssemblyConnection>> extractConnections(String storedUri) {
        log.info("TFlexAdapter.extractConnections start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                List<AssemblyConnection> connections =
                        cadCamClient.extractConnections(localFile);

                log.info("TFlexAdapter.extractConnections done: uri={}, connections={}",
                        storedUri, connections.size());
                return connections;
            } catch (Exception e) {
                log.error("TFlexAdapter.extractConnections failed: uri={}", storedUri, e);
                throw new TFlexIntegrationException(
                        "Ошибка извлечения сопряжений из T-FLEX: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение спецификации (ГОСТ)
    // ------------------------------------------------------------------

    /**
     * Извлечение спецификации по ГОСТ из T-FLEX DOCs.
     *
     * <p>Возвращает структурированные разделы спецификации
     * (документация, комплексы, сборочные единицы, детали, стандартные
     * изделия, материалы и т.д.) с позициями и обозначениями.
     */
    public CompletableFuture<Specification> extractSpecification(String storedUri) {
        log.info("TFlexAdapter.extractSpecification start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                Specification spec = cadCamClient.extractSpecification(localFile);

                if (spec == null || spec.getSections().isEmpty()) {
                    log.warn("TFlexAdapter.extractSpecification: спецификация пуста, uri={}",
                            storedUri);
                }

                log.info("TFlexAdapter.extractSpecification done: uri={}, sections={}",
                        storedUri, spec == null ? 0 : spec.getSections().size());
                return spec;

            } catch (Exception e) {
                log.error("TFlexAdapter.extractSpecification failed: uri={}", storedUri, e);
                throw new TFlexIntegrationException(
                        "Ошибка извлечения спецификации из T-FLEX: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Health check
    // ------------------------------------------------------------------

    /**
     * Проверка доступности T-FLEX-эндпоинта (CAD и DOCs).
     */
    public boolean healthCheck() {
        try {
            boolean cadOk = cadCamClient.ping(endpoint, timeoutMs);
            boolean docsOk = docsEndpoint == null || docsEndpoint.isBlank()
                    || cadCamClient.ping(docsEndpoint, timeoutMs);

            log.debug("TFlexAdapter.healthCheck: cadOk={}, docsOk={}", cadOk, docsOk);
            return cadOk && docsOk;
        } catch (Exception e) {
            log.warn("TFlexAdapter.healthCheck failed: endpoint={}, docsEndpoint={}",
                    endpoint, docsEndpoint, e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Внутренние методы
    // ------------------------------------------------------------------

    private Path fetchViaApi(String externalId) {
        log.debug("TFlexAdapter: fetch via T-FLEX API, externalId={}", externalId);
        return cadCamClient.exportFromApi(endpoint, externalId, timeoutMs);
    }

    private Path fetchViaFileExchange(String externalId) {
        log.debug("TFlexAdapter: fetch via file exchange (GRB/FRW/STEP), externalId={}",
                externalId);
        return fileExchangeService.resolveIncoming(externalId);
    }

    private void validate(Path file) {
        if (file == null) {
            throw new TFlexIntegrationException("T-FLEX вернул пустой файл");
        }
        if (!file.toFile().exists() || file.toFile().length() == 0) {
            throw new TFlexIntegrationException(
                    "Файл T-FLEX отсутствует или пуст: " + file);
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

    /** Спецификация по ГОСТ. */
    public static class Specification {
        private final List<SpecificationSection> sections;

        public Specification(List<SpecificationSection> sections) {
            this.sections = sections == null ? List.of() : sections;
        }

        public List<SpecificationSection> getSections() { return sections; }
    }

    /** Раздел спецификации. */
    public static class SpecificationSection {
        private final String name;
        private final List<BomItem> items;

        public SpecificationSection(String name, List<BomItem> items) {
            this.name = name;
            this.items = items == null ? List.of() : items;
        }

        public String getName() { return name; }
        public List<BomItem> getItems() { return items; }
    }

    /** Исключение интеграции с T-FLEX. */
    public static class TFlexIntegrationException extends RuntimeException {
        public TFlexIntegrationException(String message) { super(message); }
        public TFlexIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}