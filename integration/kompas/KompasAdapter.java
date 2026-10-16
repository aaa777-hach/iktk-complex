package ru.iktk.integration.kompas;

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
 * Адаптер интеграции с КОМПАС-3D / ЛОЦМАН:PLM (АСКОН).
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>импорт CAD-моделей (деталей и сборок) из КОМПАС-3D;</li>
 *   <li>извлечение структуры изделия (BOM) из ЛОЦМАН:PLM / сборки КОМПАС;</li>
 *   <li>извлечение КТЭ (конструктивно-технологических элементов);</li>
 *   <li>получение связей и сопряжений сборки для проектирования ТП сборки;</li>
 *   <li>чтение спецификаций по ГОСТ 2.106 из КОМПАС/ЛОЦМАН;</li>
 *   <li>передачу тяжёлых файлов через {@link FileExchangeService} в MinIO.</li>
 * </ul>
 *
 * <p><b>Роль в импортозамещении.</b> КОМПАС-3D и ЛОЦМАН:PLM относятся
 * к отечественным CAD/PLM-системам (АСКОН). Согласно {@code final_TZ.md}
 * (ред. 3, п. 5) принята модель частичного импортозамещения: ядро —
 * отечественное ПО и ОС, интеграции — любые. КОМПАС — один из приоритетных
 * источников геометрии на предприятиях РФ.
 *
 * <p>Взаимодействие возможно двумя способами:
 * <ol>
 *   <li>через API КОМПАС-3D (COM/ActiveX, библиотеки КОМПАС-Макро,
 *       приложение-сервер) и ЛОЦМАН:PLM (REST/SOAP);</li>
 *   <li>через файловый обмен: CDW, FRW, M3D, A3D, STEP AP242, IGES,
 *       Parasolid, JT, STL.</li>
 * </ol>
 * Выбор способа определяется настройкой {@code iktk.kompas.mode}.
 *
 * <p>Особенности КОМПАС:
 * <ul>
 *   <li>чертежи и спецификации — в форматах CDW/SPW (ГОСТ);</li>
 *   <li>3D-модели — M3D (деталь), A3D (сборка);</li>
 *   <li>BOM и состав изделия — через ЛОЦМАН:PLM;</li>
 *   <li>сопряжения сборки — в A3D-модели (КОМПАС-3D Assembly).</li>
 * </ul>
 */
@Component
public class KompasAdapter {

    private static final Logger log = LoggerFactory.getLogger(KompasAdapter.class);

    private final CadCamClient cadCamClient;
    private final FileExchangeService fileExchangeService;

    @Value("${iktk.kompas.mode:api}")
    private String mode;

    @Value("${iktk.kompas.endpoint:}")
    private String endpoint;

    @Value("${iktk.kompas.locman-endpoint:}")
    private String locmanEndpoint;

    @Value("${iktk.kompas.timeout-ms:30000}")
    private long timeoutMs;

    public KompasAdapter(CadCamClient cadCamClient,
                         FileExchangeService fileExchangeService) {
        this.cadCamClient = cadCamClient;
        this.fileExchangeService = fileExchangeService;
    }

    // ------------------------------------------------------------------
    // Импорт модели
    // ------------------------------------------------------------------

    /**
     * Импорт CAD-модели из КОМПАС-3D.
     *
     * @param externalId идентификатор модели в КОМПАС/ЛОЦМАН (имя документа,
     *                   GUID, URN в ЛОЦМАН или имя файла)
     * @param projectId  идентификатор проекта в ИКТК
     * @return результат импорта с внутренним URI и метаданными
     */
    public CompletableFuture<ImportResult> importModel(String externalId, String projectId) {
        log.info("KompasAdapter.importModel start: externalId={}, projectId={}, mode={}",
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

                log.info("KompasAdapter.importModel done: externalId={}, uri={}, isAssembly={}",
                        externalId, storedUri, isAssembly);
                return result;

            } catch (Exception e) {
                log.error("KompasAdapter.importModel failed: externalId={}", externalId, e);
                throw new KompasIntegrationException(
                        "Ошибка импорта модели из КОМПАС: " + externalId, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение BOM
    // ------------------------------------------------------------------

    /**
     * Извлечение BOM сборочной единицы.
     *
     * <p>Для КОМПАС состав изделия извлекается через ЛОЦМАН:PLM или
     * из сборки КОМПАС-3D (A3D). Поддерживается чтение ГОСТ-спецификаций.
     */
    public CompletableFuture<BomTree> extractBom(String storedUri) {
        log.info("KompasAdapter.extractBom start: uri={}, locmanEndpoint={}",
                storedUri, locmanEndpoint);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                BomTree bom = cadCamClient.extractBom(localFile);

                if (bom == null || bom.getItems().isEmpty()) {
                    log.warn("KompasAdapter.extractBom: BOM пуст для uri={}", storedUri);
                }

                log.info("KompasAdapter.extractBom done: uri={}, positions={}",
                        storedUri, bom == null ? 0 : bom.getItems().size());
                return bom;

            } catch (Exception e) {
                log.error("KompasAdapter.extractBom failed: uri={}", storedUri, e);
                throw new KompasIntegrationException(
                        "Ошибка извлечения BOM из КОМПАС: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение КТЭ
    // ------------------------------------------------------------------

    /**
     * Извлечение конструктивно-технологических элементов (КТЭ).
     *
     * <p>В КОМПАС-3D модель строится из операций (выдавливание, вращение,
     * кинематическая операция, отверстие, фаска, скругление и т.п.),
     * которые извлекаются как КТЭ с параметрами.
     */
    public CompletableFuture<List<Feature>> extractFeatures(String storedUri) {
        log.info("KompasAdapter.extractFeatures start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                List<Feature> features = cadCamClient.extractFeatures(localFile);
                log.info("KompasAdapter.extractFeatures done: uri={}, features={}",
                        storedUri, features.size());
                return features;
            } catch (Exception e) {
                log.error("KompasAdapter.extractFeatures failed: uri={}", storedUri, e);
                throw new KompasIntegrationException(
                        "Ошибка извлечения КТЭ из КОМПАС: " + storedUri, e);
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
     * <p>Для КОМПАС доступно через A3D-модель сборки (сопряжения,
     * позиционирование деталей).
     */
    public CompletableFuture<List<AssemblyConnection>> extractConnections(String storedUri) {
        log.info("KompasAdapter.extractConnections start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                List<AssemblyConnection> connections =
                        cadCamClient.extractConnections(localFile);

                log.info("KompasAdapter.extractConnections done: uri={}, connections={}",
                        storedUri, connections.size());
                return connections;
            } catch (Exception e) {
                log.error("KompasAdapter.extractConnections failed: uri={}", storedUri, e);
                throw new KompasIntegrationException(
                        "Ошибка извлечения сопряжений из КОМПАС: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение спецификации (ГОСТ 2.106)
    // ------------------------------------------------------------------

    /**
     * Извлечение спецификации по ГОСТ 2.106 из КОМПАС / ЛОЦМАН:PLM.
     *
     * <p>Возвращает структурированные разделы спецификации
     * (документация, комплексы, сборочные единицы, детали, стандартные
     * изделия, прочие изделия, материалы, комплекты) с позициями
     * и обозначениями.
     */
    public CompletableFuture<Specification> extractSpecification(String storedUri) {
        log.info("KompasAdapter.extractSpecification start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                Specification spec = cadCamClient.extractSpecification(localFile);

                if (spec == null || spec.getSections().isEmpty()) {
                    log.warn("KompasAdapter.extractSpecification: спецификация пуста, uri={}",
                            storedUri);
                }

                log.info("KompasAdapter.extractSpecification done: uri={}, sections={}",
                        storedUri, spec == null ? 0 : spec.getSections().size());
                return spec;

            } catch (Exception e) {
                log.error("KompasAdapter.extractSpecification failed: uri={}", storedUri, e);
                throw new KompasIntegrationException(
                        "Ошибка извлечения спецификации из КОМПАС: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Извлечение чертежа (ГОСТ)
    // ------------------------------------------------------------------

    /**
     * Извлечение данных чертежа (CDW): виды, разрезы, размеры, допуски.
     *
     * <p>Используется для анализа 2D-документации и сверки с 3D-моделью.
     */
    public CompletableFuture<DrawingData> extractDrawing(String storedUri) {
        log.info("KompasAdapter.extractDrawing start: uri={}", storedUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path localFile = fileExchangeService.download(storedUri);
                DrawingData drawing = cadCamClient.extractDrawing(localFile);

                log.info("KompasAdapter.extractDrawing done: uri={}, views={}",
                        storedUri, drawing == null ? 0 : drawing.getViews().size());
                return drawing;

            } catch (Exception e) {
                log.error("KompasAdapter.extractDrawing failed: uri={}", storedUri, e);
                throw new KompasIntegrationException(
                        "Ошибка извлечения чертежа из КОМПАС: " + storedUri, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Health check
    // ------------------------------------------------------------------

    /**
     * Проверка доступности КОМПАС-эндпоинта (CAD и ЛОЦМАН:PLM).
     */
    public boolean healthCheck() {
        try {
            boolean cadOk = cadCamClient.ping(endpoint, timeoutMs);
            boolean locmanOk = locmanEndpoint == null || locmanEndpoint.isBlank()
                    || cadCamClient.ping(locmanEndpoint, timeoutMs);

            log.debug("KompasAdapter.healthCheck: cadOk={}, locmanOk={}", cadOk, locmanOk);
            return cadOk && locmanOk;
        } catch (Exception e) {
            log.warn("KompasAdapter.healthCheck failed: endpoint={}, locmanEndpoint={}",
                    endpoint, locmanEndpoint, e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Внутренние методы
    // ------------------------------------------------------------------

    private Path fetchViaApi(String externalId) {
        log.debug("KompasAdapter: fetch via КОМПАС-3D API, externalId={}", externalId);
        return cadCamClient.exportFromApi(endpoint, externalId, timeoutMs);
    }

    private Path fetchViaFileExchange(String externalId) {
        log.debug("KompasAdapter: fetch via file exchange (M3D/A3D/STEP), externalId={}",
                externalId);
        return fileExchangeService.resolveIncoming(externalId);
    }

    private void validate(Path file) {
        if (file == null) {
            throw new KompasIntegrationException("КОМПАС вернул пустой файл");
        }
        if (!file.toFile().exists() || file.toFile().length() == 0) {
            throw new KompasIntegrationException(
                    "Файл КОМПАС отсутствует или пуст: " + file);
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

    /** Спецификация по ГОСТ 2.106. */
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

    /** Данные чертежа (CDW). */
    public static class DrawingData {
        private final List<DrawingView> views;
        private final Map<String, Object> titleBlock;

        public DrawingData(List<DrawingView> views, Map<String, Object> titleBlock) {
            this.views = views == null ? List.of() : views;
            this.titleBlock = titleBlock == null ? Map.of() : titleBlock;
        }

        public List<DrawingView> getViews() { return views; }
        public Map<String, Object> getTitleBlock() { return titleBlock; }
    }

    /** Вид/разрез чертежа. */
    public static class DrawingView {
        private final String name;
        private final List<Map<String, Object>> dimensions;

        public DrawingView(String name, List<Map<String, Object>> dimensions) {
            this.name = name;
            this.dimensions = dimensions == null ? List.of() : dimensions;
        }

        public String getName() { return name; }
        public List<Map<String, Object>> getDimensions() { return dimensions; }
    }

    /** Исключение интеграции с КОМПАС. */
    public static class KompasIntegrationException extends RuntimeException {
        public KompasIntegrationException(String message) { super(message); }
        public KompasIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}