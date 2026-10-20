package ru.iktk.equipment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.iktk.equipment.model.EquipmentGapAnalysis;
import ru.iktk.equipment.model.EquipmentRequirement;
import ru.iktk.equipment.model.EquipmentStatus;
import ru.iktk.equipment.model.EquipmentTooling;
import ru.iktk.equipment.model.EquipmentToolingId;
import ru.iktk.equipment.repository.EquipmentGapAnalysisRepository;
import ru.iktk.equipment.repository.EquipmentRepository;
import ru.iktk.equipment.repository.EquipmentRequirementRepository;
import ru.iktk.equipment.repository.EquipmentStatusRepository;
import ru.iktk.equipment.repository.EquipmentToolingRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EquipmentService — реестр оборудования")
class EquipmentServiceTest {

    @Mock private EquipmentRepository repository;
    @Mock private EquipmentStatusRepository statusRepository;
    @Mock private EquipmentToolingRepository toolingRepository;
    @Mock private EquipmentRequirementRepository requirementRepository;
    @Mock private EquipmentGapAnalysisRepository gapAnalysisRepository;

    private EquipmentService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentService(repository, statusRepository, toolingRepository,
                requirementRepository, gapAnalysisRepository);
    }

    // ===== isMethodAvailable =====

    @Test
    @DisplayName("isMethodAvailable: null -> false, репозиторий не трогаем")
    void isMethodAvailable_null_returnsFalse() {
        assertThat(service.isMethodAvailable(null)).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("isMethodAvailable: пустая строка -> false")
    void isMethodAvailable_blank_returnsFalse() {
        assertThat(service.isMethodAvailable("   ")).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("isMethodAvailable: нормализация в upper-case и вызов репозитория")
    void isMethodAvailable_normalizesAndQueries() {
        when(repository.existsAvailableByOperation("TURNING")).thenReturn(true);

        assertThat(service.isMethodAvailable("  turning  ")).isTrue();
        verify(repository).existsAvailableByOperation("TURNING");
    }

    // ===== findStatus =====

    @Test
    @DisplayName("findStatus: возвращает Optional от репозитория")
    void findStatus_returnsOptional() {
        EquipmentStatus s = new EquipmentStatus("s1", "eq1");
        when(statusRepository.findByEquipmentId("eq1")).thenReturn(Optional.of(s));

        assertThat(service.findStatus("eq1")).containsSame(s);
    }

    @Test
    @DisplayName("findStatus: пусто -> Optional.empty")
    void findStatus_empty() {
        when(statusRepository.findByEquipmentId("eq1")).thenReturn(Optional.empty());
        assertThat(service.findStatus("eq1")).isEmpty();
    }

    // ===== saveStatus =====

    @Test
    @DisplayName("saveStatus: equipment не найден -> IllegalArgumentException")
    void saveStatus_equipmentNotFound_throws() {
        when(repository.existsById("eq1")).thenReturn(false);

        assertThatThrownBy(() -> service.saveStatus("eq1", new EquipmentStatus("x", "eq1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eq1");
        verifyNoInteractions(statusRepository);
    }

    @Test
    @DisplayName("saveStatus: статуса нет -> создаётся новый и сохраняется")
    void saveStatus_noExisting_createsNew() {
        when(repository.existsById("eq1")).thenReturn(true);
        when(statusRepository.findByEquipmentId("eq1")).thenReturn(Optional.empty());
        when(statusRepository.save(any(EquipmentStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentStatus payload = new EquipmentStatus("p", "eq1");
        payload.setState("BUSY");
        payload.setLoadPercent(50);

        EquipmentStatus saved = service.saveStatus("eq1", payload);

        ArgumentCaptor<EquipmentStatus> captor = ArgumentCaptor.forClass(EquipmentStatus.class);
        verify(statusRepository).save(captor.capture());
        EquipmentStatus captured = captor.getValue();
        assertThat(captured.getEquipmentId()).isEqualTo("eq1");
        assertThat(captured.getState()).isEqualTo("BUSY");
        assertThat(captured.getLoadPercent()).isEqualTo(50);
        assertThat(saved).isSameAs(captured);
    }

    @Test
    @DisplayName("saveStatus: статус есть -> обновляется существующий")
    void saveStatus_existing_updates() {
        EquipmentStatus existing = new EquipmentStatus("s1", "eq1");
        existing.setState("MAINTENANCE");
        existing.setLoadPercent(80);

        when(repository.existsById("eq1")).thenReturn(true);
        when(statusRepository.findByEquipmentId("eq1")).thenReturn(Optional.of(existing));
        when(statusRepository.save(any(EquipmentStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentStatus payload = mock(EquipmentStatus.class);
        when(payload.getState()).thenReturn(null);       // не переопределять
        when(payload.getLoadPercent()).thenReturn(null); // не переопределять

        service.saveStatus("eq1", payload);

        assertThat(existing.getState()).isEqualTo("MAINTENANCE");
        assertThat(existing.getLoadPercent()).isEqualTo(80);
        verify(statusRepository).save(existing);
    }

    @Test
    @DisplayName("saveStatus: payload со всеми полями перезаписывает существующий")
    void saveStatus_payloadOverridesAll() {
        EquipmentStatus existing = new EquipmentStatus("s1", "eq1");
        when(repository.existsById("eq1")).thenReturn(true);
        when(statusRepository.findByEquipmentId("eq1")).thenReturn(Optional.of(existing));
        when(statusRepository.save(any(EquipmentStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant now = Instant.now();
        EquipmentStatus payload = new EquipmentStatus("p", "eq1");
        payload.setState("BUSY");
        payload.setLoadPercent(42);
        payload.setSchedule("2/2");
        payload.setLastUsedAt(now);
        payload.setNextMaintenanceAt(now);
        payload.setNotes("note");

        service.saveStatus("eq1", payload);

        assertThat(existing.getState()).isEqualTo("BUSY");
        assertThat(existing.getLoadPercent()).isEqualTo(42);
        assertThat(existing.getSchedule()).isEqualTo("2/2");
        assertThat(existing.getLastUsedAt()).isEqualTo(now);
        assertThat(existing.getNextMaintenanceAt()).isEqualTo(now);
        assertThat(existing.getNotes()).isEqualTo("note");
    }

    // ===== tooling =====

    @Test
    @DisplayName("listToolingByEquipment: делегирует в репозиторий")
    void listToolingByEquipment_delegates() {
        List<EquipmentTooling> list = List.of(new EquipmentTooling("eq1", "t1"));
        when(toolingRepository.findByEquipmentId("eq1")).thenReturn(list);

        assertThat(service.listToolingByEquipment("eq1")).isEqualTo(list);
    }

    @Test
    @DisplayName("listEquipmentByTooling: делегирует в репозиторий")
    void listEquipmentByTooling_delegates() {
        List<EquipmentTooling> list = List.of();
        when(toolingRepository.findByToolingId("t1")).thenReturn(list);

        assertThat(service.listEquipmentByTooling("t1")).isEmpty();
    }

    @Test
    @DisplayName("addTooling: equipment не найден -> IllegalArgumentException")
    void addTooling_equipmentNotFound_throws() {
        when(repository.existsById("eq1")).thenReturn(false);

        assertThatThrownBy(() -> service.addTooling("eq1", "t1", "FULL", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(toolingRepository);
    }

    @Test
    @DisplayName("addTooling: equipment есть -> сохраняется с compatibility и notes")
    void addTooling_saves() {
        when(repository.existsById("eq1")).thenReturn(true);
        when(toolingRepository.save(any(EquipmentTooling.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentTooling saved = service.addTooling("eq1", "t1", "PARTIAL", "note");

        ArgumentCaptor<EquipmentTooling> captor = ArgumentCaptor.forClass(EquipmentTooling.class);
        verify(toolingRepository).save(captor.capture());
        EquipmentTooling captured = captor.getValue();
        assertThat(captured.getId().getEquipmentId()).isEqualTo("eq1");
        assertThat(captured.getId().getToolingId()).isEqualTo("t1");
        assertThat(captured.getCompatibility()).isEqualTo("PARTIAL");
        assertThat(captured.getNotes()).isEqualTo("note");
        assertThat(saved).isSameAs(captured);
    }

    @Test
    @DisplayName("addTooling: compatibility/notes null -> остаются дефолтные")
    void addTooling_nullDefaults() {
        when(repository.existsById("eq1")).thenReturn(true);
        when(toolingRepository.save(any(EquipmentTooling.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentTooling saved = service.addTooling("eq1", "t1", null, null);
        assertThat(saved.getCompatibility()).isEqualTo("FULL");
        assertThat(saved.getNotes()).isNull();
    }

    @Test
    @DisplayName("removeTooling: связь есть -> delete и true")
    void removeTooling_exists_deletes() {
        when(toolingRepository.existsById(new EquipmentToolingId("eq1", "t1"))).thenReturn(true);

        assertThat(service.removeTooling("eq1", "t1")).isTrue();
        verify(toolingRepository).deleteByEquipmentIdAndToolingId("eq1", "t1");
    }

    @Test
    @DisplayName("removeTooling: связи нет -> false, delete не вызывается")
    void removeTooling_missing_returnsFalse() {
        when(toolingRepository.existsById(any(EquipmentToolingId.class))).thenReturn(false);

        assertThat(service.removeTooling("eq1", "t1")).isFalse();
        verify(toolingRepository, never()).deleteByEquipmentIdAndToolingId(anyString(), anyString());
    }

    // ===== requirements =====

    @Test
    @DisplayName("listRequirements: делегирует в репозиторий")
    void listRequirements_delegates() {
        List<EquipmentRequirement> list = List.of();
        when(requirementRepository.findByProjectIdOrderByPriorityDesc("p1")).thenReturn(list);

        assertThat(service.listRequirements("p1")).isEmpty();
    }

    @Test
    @DisplayName("saveRequirement: id пустой -> генерируется UUID")
    void saveRequirement_generatesId() {
        when(requirementRepository.save(any(EquipmentRequirement.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRequirement req = new EquipmentRequirement(null, "p1", "TURNING");
        EquipmentRequirement saved = service.saveRequirement(req);

        assertThat(saved.getId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("saveRequirement: id задан -> сохраняется как есть")
    void saveRequirement_keepsId() {
        when(requirementRepository.save(any(EquipmentRequirement.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRequirement req = new EquipmentRequirement("req-1", "p1", "TURNING");
        EquipmentRequirement saved = service.saveRequirement(req);

        assertThat(saved.getId()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("deleteRequirement: есть -> deleteById и true")
    void deleteRequirement_exists() {
        when(requirementRepository.existsById("req-1")).thenReturn(true);

        assertThat(service.deleteRequirement("req-1")).isTrue();
        verify(requirementRepository).deleteById("req-1");
    }

    @Test
    @DisplayName("deleteRequirement: нет -> false")
    void deleteRequirement_missing() {
        when(requirementRepository.existsById("req-1")).thenReturn(false);

        assertThat(service.deleteRequirement("req-1")).isFalse();
        verify(requirementRepository, never()).deleteById(anyString());
    }

    // ===== gap analysis =====

    @Test
    @DisplayName("listGapAnalyses: делегирует в репозиторий")
    void listGapAnalyses_delegates() {
        when(gapAnalysisRepository.findByProjectIdOrderByStartedAtDesc("p1")).thenReturn(List.of());
        assertThat(service.listGapAnalyses("p1")).isEmpty();
    }

    @Test
    @DisplayName("startGapAnalysis: создаёт со STRICT/RUNNING по умолчанию")
    void startGapAnalysis_defaults() {
        when(gapAnalysisRepository.save(any(EquipmentGapAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentGapAnalysis saved = service.startGapAnalysis("p1", null, null);

        assertThat(saved.getProjectId()).isEqualTo("p1");
        assertThat(saved.getStrategy()).isEqualTo("STRICT");
        assertThat(saved.getStatus()).isEqualTo("RUNNING");
        assertThat(saved.getId()).isNotBlank();
    }

    @Test
    @DisplayName("startGapAnalysis: кастомная стратегия и requestedBy")
    void startGapAnalysis_custom() {
        when(gapAnalysisRepository.save(any(EquipmentGapAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentGapAnalysis saved = service.startGapAnalysis("p1", "FLEXIBLE", "ivanov");

        assertThat(saved.getStrategy()).isEqualTo("FLEXIBLE");
        assertThat(saved.getRequestedBy()).isEqualTo("ivanov");
    }

    @Test
    @DisplayName("completeGapAnalysis: найден -> статус COMPLETED, completedAt установлен")
    void completeGapAnalysis_found() {
        EquipmentGapAnalysis ga = new EquipmentGapAnalysis("g1", "p1");
        when(gapAnalysisRepository.findById("g1")).thenReturn(Optional.of(ga));
        when(gapAnalysisRepository.save(any(EquipmentGapAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<EquipmentGapAnalysis> result = service.completeGapAnalysis("g1", null, null);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(result.get().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeGapAnalysis: кастомный статус и error")
    void completeGapAnalysis_customStatus() {
        EquipmentGapAnalysis ga = new EquipmentGapAnalysis("g1", "p1");
        when(gapAnalysisRepository.findById("g1")).thenReturn(Optional.of(ga));
        when(gapAnalysisRepository.save(any(EquipmentGapAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<EquipmentGapAnalysis> result = service.completeGapAnalysis("g1", "FAILED", "boom");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("FAILED");
        assertThat(result.get().getError()).isEqualTo("boom");
    }

    @Test
    @DisplayName("completeGapAnalysis: не найден -> Optional.empty")
    void completeGapAnalysis_notFound() {
        when(gapAnalysisRepository.findById("g1")).thenReturn(Optional.empty());

        assertThat(service.completeGapAnalysis("g1", null, null)).isEmpty();
        verify(gapAnalysisRepository, never()).save(any());
    }
}