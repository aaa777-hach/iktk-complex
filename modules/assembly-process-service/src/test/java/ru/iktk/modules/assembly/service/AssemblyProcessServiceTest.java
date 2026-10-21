package ru.iktk.modules.assembly.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.model.AssemblyComponent;
import ru.iktk.modules.assembly.model.AssemblyProcess;
import ru.iktk.modules.assembly.model.AssemblyStage;
import ru.iktk.modules.assembly.model.AssemblyTooling;
import ru.iktk.modules.assembly.repository.AssemblyComponentRepository;
import ru.iktk.modules.assembly.repository.AssemblyProcessRepository;
import ru.iktk.modules.assembly.repository.AssemblyStageRepository;
import ru.iktk.modules.assembly.repository.AssemblyToolingRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssemblyProcessService — проектирование ТП сборки")
class AssemblyProcessServiceTest {

    @Mock private AssemblyProcessRepository processRepository;
    @Mock private AssemblyStageRepository stageRepository;
    @Mock private AssemblyComponentRepository componentRepository;
    @Mock private AssemblyToolingRepository toolingRepository;

    private AssemblyProcessService service;

    @BeforeEach
    void setUp() {
        service = new AssemblyProcessService(processRepository, stageRepository,
                componentRepository, toolingRepository);
        ReflectionTestUtils.setField(service, "demoFallback", true);
        ReflectionTestUtils.setField(service, "defaultStrategy", "SEQUENTIAL");

        when(processRepository.save(any(AssemblyProcess.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(componentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(toolingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ===== designProcess =====

    @Test
    @DisplayName("designProcess: demo-fallback -> 5 этапов, 3 компонента, 2 оснастки")
    void designProcess_demoFallback() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");

        AssemblyResponse response = service.designProcess(req);

        assertThat(response).isNotNull();
        assertThat(response.getProcessId()).isNotBlank();
        assertThat(response.getProjectId()).isEqualTo("proj1");
        assertThat(response.getObjectRef()).isEqualTo("obj-1");
        assertThat(response.getStrategy()).isEqualTo("SEQUENTIAL");
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getStageCount()).isEqualTo(5);
        assertThat(response.getComponentCount()).isEqualTo(3);
        assertThat(response.getTotalTimeMin()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("designProcess: сохраняет process, stages, components, toolings")
    void designProcess_persistsAll() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        service.designProcess(req);

        verify(processRepository).save(any(AssemblyProcess.class));
        verify(stageRepository).saveAll(anyList());
        verify(componentRepository).saveAll(anyList());
        verify(toolingRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("designProcess: custom strategy используется")
    void designProcess_customStrategy() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        req.setStrategy("PARALLEL");

        AssemblyResponse response = service.designProcess(req);
        assertThat(response.getStrategy()).isEqualTo("PARALLEL");
    }

    @Test
    @DisplayName("designProcess: null strategy -> defaultStrategy")
    void designProcess_nullStrategy_usesDefault() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        req.setStrategy(null);

        AssemblyResponse response = service.designProcess(req);
        assertThat(response.getStrategy()).isEqualTo("SEQUENTIAL");
    }

    @Test
    @DisplayName("designProcess: blank strategy -> defaultStrategy")
    void designProcess_blankStrategy_usesDefault() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        req.setStrategy("   ");

        AssemblyResponse response = service.designProcess(req);
        assertThat(response.getStrategy()).isEqualTo("SEQUENTIAL");
    }

    @Test
    @DisplayName("designProcess: null request -> IllegalArgumentException")
    void designProcess_nullRequest_throws() {
        assertThatThrownBy(() -> service.designProcess(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
        verifyNoInteractions(processRepository);
    }

    @Test
    @DisplayName("designProcess: null projectId -> IllegalArgumentException")
    void designProcess_nullProjectId_throws() {
        AssemblyRequest req = new AssemblyRequest();
        assertThatThrownBy(() -> service.designProcess(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
        verifyNoInteractions(processRepository);
    }

    @Test
    @DisplayName("designProcess: blank projectId -> IllegalArgumentException")
    void designProcess_blankProjectId_throws() {
        AssemblyRequest req = new AssemblyRequest();
        req.setProjectId("   ");
        assertThatThrownBy(() -> service.designProcess(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("designProcess: сохраняемый process содержит правильные метрики")
    void designProcess_savedProcessMetrics() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        service.designProcess(req);

        ArgumentCaptor<AssemblyProcess> captor = ArgumentCaptor.forClass(AssemblyProcess.class);
        verify(processRepository).save(captor.capture());
        AssemblyProcess saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo("proj1");
        assertThat(saved.getStageCount()).isEqualTo(5);
        assertThat(saved.getComponentCount()).isEqualTo(3);
        assertThat(saved.getTotalTimeMin()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("designProcess: каждый вызов даёт новый processId")
    void designProcess_uniqueProcessId() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        AssemblyResponse a = service.designProcess(req);
        AssemblyResponse b = service.designProcess(req);
        assertThat(a.getProcessId()).isNotEqualTo(b.getProcessId());
    }

    @Test
    @DisplayName("designProcess: totalTimeMin = сумма всех stages")
    void designProcess_totalTimeIsSum() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        service.designProcess(req);

        ArgumentCaptor<List<AssemblyStage>> stagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(stageRepository).saveAll(stagesCaptor.capture());
        BigDecimal expected = stagesCaptor.getValue().stream()
                .map(AssemblyStage::totalTimeMin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ArgumentCaptor<AssemblyProcess> processCaptor = ArgumentCaptor.forClass(AssemblyProcess.class);
        verify(processRepository).save(processCaptor.capture());
        assertThat(processCaptor.getValue().getTotalTimeMin()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("designProcess: bomRef не пустой -> другой набор компонентов (TODO ветка)")
    void designProcess_withBomRef_usesBomBranch() {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        req.setBomRef("bom-123");

        AssemblyResponse response = service.designProcess(req);
        // В demoFallback=true побеждает demo-ветка, поэтому 3 компонента.
        // Проверяем, что код не падает и возвращает результат.
        assertThat(response).isNotNull();
        assertThat(response.getComponentCount()).isGreaterThan(0);
    }

    // ===== getById =====

    @Test
    @DisplayName("getById: найден -> возвращает process")
    void getById_found() {
        AssemblyProcess p = new AssemblyProcess("p1", "proj1");
        when(processRepository.findById("p1")).thenReturn(Optional.of(p));

        assertThat(service.getById("p1")).isSameAs(p);
    }

    @Test
    @DisplayName("getById: не найден -> IllegalArgumentException")
    void getById_notFound_throws() {
        when(processRepository.findById("p1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("p1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    // ===== listByProject =====

    @Test
    @DisplayName("listByProject: делегирует в репозиторий")
    void listByProject_delegates() {
        List<AssemblyProcess> list = List.of(new AssemblyProcess("p1", "proj1"));
        when(processRepository.findByProjectIdOrderByCreatedAtDesc("proj1")).thenReturn(list);

        assertThat(service.listByProject("proj1")).isEqualTo(list);
    }

    @Test
    @DisplayName("listByProject: пусто -> пустой список")
    void listByProject_empty() {
        when(processRepository.findByProjectIdOrderByCreatedAtDesc("proj1")).thenReturn(List.of());
        assertThat(service.listByProject("proj1")).isEmpty();
    }
}