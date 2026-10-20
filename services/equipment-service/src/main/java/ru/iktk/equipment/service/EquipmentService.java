package ru.iktk.equipment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.equipment.model.EquipmentGapAnalysis;
import ru.iktk.equipment.model.EquipmentRequirement;
import ru.iktk.equipment.model.EquipmentStatus;
import ru.iktk.equipment.model.EquipmentTooling;
import ru.iktk.equipment.repository.EquipmentGapAnalysisRepository;
import ru.iktk.equipment.repository.EquipmentRepository;
import ru.iktk.equipment.repository.EquipmentRequirementRepository;
import ru.iktk.equipment.repository.EquipmentStatusRepository;
import ru.iktk.equipment.repository.EquipmentToolingRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EquipmentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentService.class);

    private final EquipmentRepository repository;
    private final EquipmentStatusRepository statusRepository;
    private final EquipmentToolingRepository toolingRepository;
    private final EquipmentRequirementRepository requirementRepository;
    private final EquipmentGapAnalysisRepository gapAnalysisRepository;

    public EquipmentService(EquipmentRepository repository,
                            EquipmentStatusRepository statusRepository,
                            EquipmentToolingRepository toolingRepository,
                            EquipmentRequirementRepository requirementRepository,
                            EquipmentGapAnalysisRepository gapAnalysisRepository) {
        this.repository = repository;
        this.statusRepository = statusRepository;
        this.toolingRepository = toolingRepository;
        this.requirementRepository = requirementRepository;
        this.gapAnalysisRepository = gapAnalysisRepository;
    }

    // ------------------------------------------------------------------
    // Существующая логика
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean isMethodAvailable(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            log.warn("isMethodAvailable: operationType пуст");
            return false;
        }
        String normalized = operationType.trim().toUpperCase();
        boolean available = repository.existsAvailableByOperation(normalized);
        log.info("isMethodAvailable: operationType={}, available={}", normalized, available);
        return available;
    }

    // ------------------------------------------------------------------
    // Статус и загрузка оборудования
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<EquipmentStatus> findStatus(String equipmentId) {
        return statusRepository.findByEquipmentId(equipmentId);
    }

    /**
     * Сохранить/обновить статус. Если записи нет — создаёт новую.
     */
    @Transactional
    public EquipmentStatus saveStatus(String equipmentId, EquipmentStatus payload) {
        if (!repository.existsById(equipmentId)) {
            throw new IllegalArgumentException("Оборудование не найдено: " + equipmentId);
        }
        EquipmentStatus target = statusRepository.findByEquipmentId(equipmentId)
                .orElseGet(() -> new EquipmentStatus(UUID.randomUUID().toString(), equipmentId));
        if (payload.getState() != null)         target.setState(payload.getState());
        if (payload.getLoadPercent() != null)   target.setLoadPercent(payload.getLoadPercent());
        if (payload.getSchedule() != null)      target.setSchedule(payload.getSchedule());
        if (payload.getLastUsedAt() != null)    target.setLastUsedAt(payload.getLastUsedAt());
        if (payload.getNextMaintenanceAt() != null) target.setNextMaintenanceAt(payload.getNextMaintenanceAt());
        if (payload.getNotes() != null)         target.setNotes(payload.getNotes());
        EquipmentStatus saved = statusRepository.save(target);
        log.info("saveStatus: equipmentId={}, state={}, load={}", equipmentId, saved.getState(), saved.getLoadPercent());
        return saved;
    }

    // ------------------------------------------------------------------
    // Совместимость с оснасткой
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EquipmentTooling> listToolingByEquipment(String equipmentId) {
        return toolingRepository.findByEquipmentId(equipmentId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentTooling> listEquipmentByTooling(String toolingId) {
        return toolingRepository.findByToolingId(toolingId);
    }

    @Transactional
    public EquipmentTooling addTooling(String equipmentId, String toolingId, String compatibility, String notes) {
        if (!repository.existsById(equipmentId)) {
            throw new IllegalArgumentException("Оборудование не найдено: " + equipmentId);
        }
        EquipmentTooling et = new EquipmentTooling(equipmentId, toolingId);
        if (compatibility != null) et.setCompatibility(compatibility);
        if (notes != null) et.setNotes(notes);
        EquipmentTooling saved = toolingRepository.save(et);
        log.info("addTooling: equipmentId={}, toolingId={}, compatibility={}", equipmentId, toolingId, saved.getCompatibility());
        return saved;
    }

    @Transactional
    public boolean removeTooling(String equipmentId, String toolingId) {
        if (!toolingRepository.existsById(new ru.iktk.equipment.model.EquipmentToolingId(equipmentId, toolingId))) {
            return false;
        }
        toolingRepository.deleteByEquipmentIdAndToolingId(equipmentId, toolingId);
        log.info("removeTooling: equipmentId={}, toolingId={}", equipmentId, toolingId);
        return true;
    }

    // ------------------------------------------------------------------
    // Требования к оборудованию
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EquipmentRequirement> listRequirements(String projectId) {
        return requirementRepository.findByProjectIdOrderByPriorityDesc(projectId);
    }

    @Transactional
    public EquipmentRequirement saveRequirement(EquipmentRequirement payload) {
        if (payload.getId() == null || payload.getId().isBlank()) {
            payload.setId(UUID.randomUUID().toString());
        }
        EquipmentRequirement saved = requirementRepository.save(payload);
        log.info("saveRequirement: id={}, projectId={}, operationType={}", saved.getId(), saved.getProjectId(), saved.getOperationType());
        return saved;
    }

    @Transactional
    public boolean deleteRequirement(String id) {
        if (!requirementRepository.existsById(id)) {
            return false;
        }
        requirementRepository.deleteById(id);
        log.info("deleteRequirement: id={}", id);
        return true;
    }

    // ------------------------------------------------------------------
    // Gap-анализ
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EquipmentGapAnalysis> listGapAnalyses(String projectId) {
        return gapAnalysisRepository.findByProjectIdOrderByStartedAtDesc(projectId);
    }

    @Transactional
    public EquipmentGapAnalysis startGapAnalysis(String projectId, String strategy, String requestedBy) {
        EquipmentGapAnalysis ga = new EquipmentGapAnalysis(UUID.randomUUID().toString(), projectId);
        if (strategy != null) ga.setStrategy(strategy);
        if (requestedBy != null) ga.setRequestedBy(requestedBy);
        EquipmentGapAnalysis saved = gapAnalysisRepository.save(ga);
        log.info("startGapAnalysis: id={}, projectId={}, strategy={}", saved.getId(), projectId, saved.getStrategy());
        return saved;
    }

    @Transactional
    public Optional<EquipmentGapAnalysis> completeGapAnalysis(String id, String status, String error) {
        return gapAnalysisRepository.findById(id).map(ga -> {
            ga.setStatus(status != null ? status : "COMPLETED");
            ga.setCompletedAt(Instant.now());
            if (error != null) ga.setError(error);
            EquipmentGapAnalysis saved = gapAnalysisRepository.save(ga);
            log.info("completeGapAnalysis: id={}, status={}", id, saved.getStatus());
            return saved;
        });
    }
}