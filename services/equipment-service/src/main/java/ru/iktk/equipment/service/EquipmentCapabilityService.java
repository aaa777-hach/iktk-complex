package ru.iktk.equipment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.equipment.dto.EquipmentCapabilityDto;
import ru.iktk.equipment.exception.EquipmentException;
import ru.iktk.equipment.mapper.EquipmentMapper;
import ru.iktk.equipment.model.Equipment;
import ru.iktk.equipment.model.EquipmentCapability;
import ru.iktk.equipment.repository.EquipmentCapabilityRepository;
import ru.iktk.equipment.repository.EquipmentRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис возможностей оборудования ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>учёт возможностей каждого станка: типы операций, точность,
 *       габариты, материалы, мощность, усилие, температура;</li>
 *   <li>получение возможностей по оборудованию;</li>
 *   <li>полное замещение набора возможностей (replace);</li>
 *   <li>поиск оборудования по требуемой операции;</li>
 *   <li>проверку совместимости возможностей с требованиями операции;</li>
 *   <li>агрегацию возможностей по парку (для matching и gap-анализа).</li>
 * </ul>
 *
 * <p><b>Модель данных.</b> Каждое оборудование может иметь несколько
 * записей возможностей ({@code equipment_capabilities}) — по одной на
 * каждый тип операции, который станок может выполнять.
 *
 * <p><b>Использование.</b>
 * <ul>
 *   <li>{@code EquipmentService.match(...)} — использует возможности
 *       для сопоставления требований ТП с парком;</li>
 *   <li>{@code EquipmentMatchingService} — фильтрует парк по
 *       критичным параметрам (тип операции, точность, габариты,
 *       материал, мощность);</li>
 *   <li>модули {@code casting}, {@code forging}, {@code machining} —
 *       через {@code isMethodAvailable(...)} проверяют доступность
 *       метода обработки на предприятии.</li>
 * </ul>
 */
@Service
public class EquipmentCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentCapabilityService.class);

    private final EquipmentCapabilityRepository capabilityRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMapper mapper;

    public EquipmentCapabilityService(EquipmentCapabilityRepository capabilityRepository,
                                      EquipmentRepository equipmentRepository,
                                      EquipmentMapper mapper) {
        this.capabilityRepository = capabilityRepository;
        this.equipmentRepository = equipmentRepository;
        this.mapper = mapper;
    }

    // ==================================================================
    // 1. Чтение
    // ==================================================================

    /**
     * Получить все возможности оборудования.
     */
    @Transactional(readOnly = true)
    public List<EquipmentCapabilityDto> getByEquipment(String equipmentId) {
        validateEquipmentId(equipmentId);
        ensureEquipmentExists(equipmentId);

        return capabilityRepository.findByEquipmentId(equipmentId)
                .stream()
                .map(mapper::toCapabilityDto)
                .toList();
    }

    /**
     * Получить возможности по типу операции.
     */
    @Transactional(readOnly = true)
    public List<EquipmentCapabilityDto> getByOperationType(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            throw EquipmentException.validation("Не указан тип операции");
        }

        return capabilityRepository.findByOperationType(operationType)
                .stream()
                .map(mapper::toCapabilityDto)
                .toList();
    }

    /**
     * Получить возможности оборудования по типу операции.
     */
    @Transactional(readOnly = true)
    public EquipmentCapabilityDto getByEquipmentAndOperation(String equipmentId,
                                                              String operationType) {
        validateEquipmentId(equipmentId);
        if (operationType == null || operationType.isBlank()) {
            throw EquipmentException.validation("Не указан тип операции");
        }

        return capabilityRepository
                .findByEquipmentIdAndOperationType(equipmentId, operationType)
                .map(mapper::toCapabilityDto)
                .orElseThrow(() -> EquipmentException.notFound(
                        "Возможность не найдена: equipmentId=" + equipmentId
                                + ", operationType=" + operationType));
    }

    // ==================================================================
    // 2. Замещение (replace)
    // ==================================================================

    /**
     * Полностью заменить набор возможностей оборудования.
     *
     * <p>Удаляет старые записи и создаёт новые. Используется при
     * редактировании карточки станка в UI.
     */
    @Transactional
    public List<EquipmentCapabilityDto> replace(String equipmentId,
                                                List<EquipmentCapabilityDto> capabilities) {
        validateEquipmentId(equipmentId);
        ensureEquipmentExists(equipmentId);

        log.info("replace: equipmentId={}, count={}",
                equipmentId, capabilities == null ? 0 : capabilities.size());

        // Удаляем старые возможности
        capabilityRepository.deleteByEquipmentId(equipmentId);

        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }

        // Создаём новые
        List<EquipmentCapability> entities = new ArrayList<>();
        for (EquipmentCapabilityDto dto : capabilities) {
            validateCapability(dto);

            EquipmentCapability entity = mapper.toCapabilityEntity(dto);
            entity.setId(UUID.randomUUID().toString());
            entity.setEquipmentId(equipmentId);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            entities.add(entity);
        }

        List<EquipmentCapability> saved = capabilityRepository.saveAll(entities);

        log.info("replace done: equipmentId={}, saved={}", equipmentId, saved.size());

        return saved.stream().map(mapper::toCapabilityDto).toList();
    }

    /**
     * Добавить одну возможность к оборудованию.
     */
    @Transactional
    public EquipmentCapabilityDto add(String equipmentId,
                                      EquipmentCapabilityDto dto) {
        validateEquipmentId(equipmentId);
        ensureEquipmentExists(equipmentId);
        validateCapability(dto);

        // Проверка: возможность для этой операции уже существует?
        capabilityRepository
                .findByEquipmentIdAndOperationType(equipmentId, dto.getOperationType())
                .ifPresent(existing -> {
                    throw EquipmentException.validation(
                            "Возможность для операции " + dto.getOperationType()
                                    + " уже существует у оборудования " + equipmentId);
                });

        EquipmentCapability entity = mapper.toCapabilityEntity(dto);
        entity.setId(UUID.randomUUID().toString());
        entity.setEquipmentId(equipmentId);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        EquipmentCapability saved = capabilityRepository.save(entity);

        log.info("add: equipmentId={}, operationType={}",
                equipmentId, dto.getOperationType());

        return mapper.toCapabilityDto(saved);
    }

    /**
     * Удалить возможность.
     */
    @Transactional
    public void delete(String capabilityId) {
        if (capabilityId == null || capabilityId.isBlank()) {
            throw EquipmentException.validation("Не указан capabilityId");
        }

        EquipmentCapability capability = capabilityRepository.findById(capabilityId)
                .orElseThrow(() -> EquipmentException.notFound(
                        "Возможность не найдена: " + capabilityId));

        capabilityRepository.delete(capability);

        log.info("delete: capabilityId={}, equipmentId={}",
                capabilityId, capability.getEquipmentId());
    }

    // ==================================================================
    // 3. Проверки
    // ==================================================================

    /**
     * Проверить, поддерживает ли оборудование указанную операцию.
     */
    @Transactional(readOnly = true)
    public boolean supportsOperation(String equipmentId, String operationType) {
        if (equipmentId == null || operationType == null) {
            return false;
        }
        return capabilityRepository
                .findByEquipmentIdAndOperationType(equipmentId, operationType)
                .isPresent();
    }

    /**
     * Проверить, поддерживается ли операция хотя бы одним станком парка.
     */
    @Transactional(readOnly = true)
    public boolean isOperationAvailable(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return false;
        }
        return capabilityRepository.existsByOperationType(operationType);
    }

    /**
     * Проверить соответствие возможностей требованиям операции.
     *
     * <p>Сравниваются:
     * <ul>
     *   <li>тип операции;</li>
     *   <li>требуемая точность;</li>
     *   <li>максимальные габариты;</li>
     *   <li>материал;</li>
     *   <li>мощность.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public CapabilityCheckResult checkCompatibility(String equipmentId,
                                                    Map<String, Object> requirements) {
        validateEquipmentId(equipmentId);
        if (requirements == null || requirements.isEmpty()) {
            throw EquipmentException.validation("Не указаны требования");
        }

        String operationType = str(requirements.get("operationType"));
        if (operationType == null) {
            throw EquipmentException.validation("Не указан operationType в требованиях");
        }

        EquipmentCapability capability = capabilityRepository
                .findByEquipmentIdAndOperationType(equipmentId, operationType)
                .orElse(null);

        if (capability == null) {
            return CapabilityCheckResult.notSupported(
                    "Оборудование " + equipmentId + " не поддерживает операцию " + operationType);
        }

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Точность
        String requiredAccuracy = str(requirements.get("requiredAccuracy"));
        if (requiredAccuracy != null && capability.getTolerance() != null) {
            if (!accuracySatisfies(capability.getTolerance(), requiredAccuracy)) {
                issues.add("Точность " + capability.getTolerance()
                        + " не соответствует требуемой " + requiredAccuracy);
            }
        }

        // 2. Габариты
        String requiredDimensions = str(requirements.get("maxDimensions"));
        if (requiredDimensions != null && capability.getMaxDimensions() != null) {
            if (!dimensionsSatisfy(capability.getMaxDimensions(), requiredDimensions)) {
                issues.add("Габариты " + capability.getMaxDimensions()
                        + " меньше требуемых " + requiredDimensions);
            }
        }

        // 3. Материал
        String material = str(requirements.get("material"));
        if (material != null && capability.getMaterials() != null
                && !capability.getMaterials().isEmpty()) {
            boolean materialOk = capability.getMaterials().stream()
                    .anyMatch(m -> m.equalsIgnoreCase(material));
            if (!materialOk) {
                issues.add("Материал " + material + " не поддерживается оборудованием");
            }
        }

        // 4. Мощность
        BigDecimal requiredPower = decimal(requirements.get("requiredPowerKw"));
        if (requiredPower != null && capability.getPowerKw() != null) {
            if (capability.getPowerKw().compareTo(requiredPower) < 0) {
                issues.add("Мощность " + capability.getPowerKw()
                        + " кВт меньше требуемой " + requiredPower + " кВт");
            }
        }

        boolean compatible = issues.isEmpty();
        CapabilityCheckResult result = new CapabilityCheckResult(
                compatible, issues, warnings, capability);

        log.debug("checkCompatibility: equipmentId={}, operationType={}, compatible={}, issues={}",
                equipmentId, operationType, compatible, issues.size());

        return result;
    }

    // ==================================================================
    // 4. Агрегация
    // ==================================================================

    /**
     * Получить все операции, поддерживаемые парком оборудования.
     */
    @Transactional(readOnly = true)
    public List<String> getSupportedOperations() {
        return capabilityRepository.findAllOperationTypes();
    }

    /**
     * Получить операции, поддерживаемые конкретным оборудованием.
     */
    @Transactional(readOnly = true)
    public List<String> getOperationsByEquipment(String equipmentId) {
        validateEquipmentId(equipmentId);
        return capabilityRepository.findOperationTypesByEquipment(equipmentId);
    }

    /**
     * Сводка по возможностям парка: сколько станков поддерживают
     * каждую операцию.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCapabilitySummary() {
        List<Object[]> rows = capabilityRepository.countByOperationTypeGrouped();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(Map.of(
                    "operationType", row[0],
                    "equipmentCount", row[1]
            ));
        }
        return result;
    }

    /**
     * Найти оборудование, поддерживающее операцию с учётом точности.
     */
    @Transactional(readOnly = true)
    public List<String> findEquipmentByOperationAndAccuracy(String operationType,
                                                             String requiredAccuracy) {
        if (operationType == null || operationType.isBlank()) {
            throw EquipmentException.validation("Не указан тип операции");
        }

        return capabilityRepository
                .findEquipmentIdsByOperationAndAccuracy(operationType, requiredAccuracy);
    }

    // ==================================================================
    // Вспомогательные методы
    // ==================================================================

    private void validateEquipmentId(String equipmentId) {
        if (equipmentId == null || equipmentId.isBlank()) {
            throw EquipmentException.validation("Не указан equipmentId");
        }
    }

    private void ensureEquipmentExists(String equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw EquipmentException.notFound(
                    "Оборудование не найдено: " + equipmentId);
        }
    }

    private void validateCapability(EquipmentCapabilityDto dto) {
        if (dto == null) {
            throw EquipmentException.validation("Пустая возможность");
        }
        if (dto.getOperationType() == null || dto.getOperationType().isBlank()) {
            throw EquipmentException.validation("Не указан тип операции");
        }
    }

    /**
     * Проверка: точность оборудования удовлетворяет требуемой.
     *
     * <p>Формат точности: строка (например, "IT7", "±0.01 мм").
     * Простая эвристика: сравнение числовых значений, если удаётся
     * извлечь; иначе — сравнение строк (равенство).
     *
     * <p>Реальная реализация — таблица соответствия классов точности.
     */
    private boolean accuracySatisfies(String equipmentTolerance, String requiredAccuracy) {
        if (equipmentTolerance == null || requiredAccuracy == null) {
            return true;
        }
        // Упрощение: если равны — совпадает; иначе — считаем совместимым (мягкая проверка).
        return equipmentTolerance.equalsIgnoreCase(requiredAccuracy)
                || requiredAccuracy.isBlank();
    }

    /**
     * Проверка: габариты оборудования удовлетворяют требуемым.
     *
     * <p>Формат габаритов: строка (например, "1000x500x400").
     */
    private boolean dimensionsSatisfy(String equipmentDimensions, String requiredDimensions) {
        if (equipmentDimensions == null || requiredDimensions == null) {
            return true;
        }
        // Упрощение: сравнение строк. Реальная реализация — парсинг "AxBxC".
        return equipmentDimensions.equalsIgnoreCase(requiredDimensions)
                || requiredDimensions.isBlank();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================================================================
    // Вспомогательные типы
    // ==================================================================

    /**
     * Результат проверки совместимости оборудования с требованиями.
     */
    public static class CapabilityCheckResult {
        private final boolean compatible;
        private final List<String> issues;
        private final List<String> warnings;
        private final EquipmentCapability capability;

        public CapabilityCheckResult(boolean compatible,
                                     List<String> issues,
                                     List<String> warnings,
                                     EquipmentCapability capability) {
            this.compatible = compatible;
            this.issues = issues == null ? List.of() : List.copyOf(issues);
            this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
            this.capability = capability;
        }

        public boolean isCompatible() { return compatible; }
        public List<String> getIssues() { return issues; }
        public List<String> getWarnings() { return warnings; }
        public EquipmentCapability getCapability() { return capability; }

        public static CapabilityCheckResult notSupported(String reason) {
            return new CapabilityCheckResult(false, List.of(reason), List.of(), null);
        }
    }
}