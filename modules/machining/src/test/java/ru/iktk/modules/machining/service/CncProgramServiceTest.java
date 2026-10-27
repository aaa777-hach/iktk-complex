package ru.iktk.modules.machining.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.machining.client.EquipmentClient;
import ru.iktk.modules.machining.client.StorageClient;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.CncProgramRequest;
import ru.iktk.modules.machining.dto.CncProgramResponse;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.CncProgram;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;
import ru.iktk.modules.machining.model.MachiningOperation;
import ru.iktk.modules.machining.postprocessor.Iso6983Postprocessor;
import ru.iktk.modules.machining.postprocessor.PostprocessorRegistry;
import ru.iktk.modules.machining.repository.CncProgramRepository;
import ru.iktk.modules.machining.toolpath.Toolpath;
import ru.iktk.modules.machining.toolpath.ToolpathGenerator;
import ru.iktk.modules.machining.toolpath.ToolpathPoint;
import ru.iktk.modules.machining.validator.GCodeValidator;
import ru.iktk.modules.machining.validator.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CncProgramServiceTest {

    @Mock private CncProgramRepository repository;
    @Mock private EquipmentClient equipmentClient;
    @Mock private StorageClient storageClient;
    @Mock private PostprocessorRegistry postprocessorRegistry;
    @Mock private ToolpathGenerator toolpathGenerator;
    @Mock private GCodeValidator validator;

    private MachiningProperties properties;
    private Iso6983Postprocessor isoPostprocessor;
    private CncProgramService service;
    private EquipmentInfo equipment;

    @BeforeEach
    void setUp() {
        properties = new MachiningProperties();
        isoPostprocessor = new Iso6983Postprocessor();
        service = new CncProgramService(
                repository, equipmentClient, storageClient,
                postprocessorRegistry, toolpathGenerator, validator, properties
        );
        equipment = new EquipmentInfo("EQ-1", "CNC-DEFAULT", "ISO6983", 3, Map.of());
    }

    private MachiningOperation op(boolean cnc, String name) {
        CuttingTool tool = CuttingTool.builder()
                .type("END_MILL").diameter(BigDecimal.TEN).build();
        CuttingMode mode = CuttingMode.builder()
                .method(MachiningMethod.MILLING)
                .depthOfCutMm(BigDecimal.valueOf(2))
                .feedMmPerRev(BigDecimal.valueOf(0.2))
                .build();
        return MachiningOperation.builder()
                .order(1).name(name).method(MachiningMethod.MILLING)
                .cnc(cnc).tool(tool).cuttingMode(mode)
                .build();
    }

    private Toolpath sampleToolpath() {
        return new Toolpath(List.of(
                new ToolpathPoint(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "RAPID", null),
                new ToolpathPoint(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.valueOf(-2), "FEED", BigDecimal.valueOf(0.2))
        ));
    }

    private CncProgramRequest request(List<MachiningOperation> ops) {
        return CncProgramRequest.builder()
                .projectId("p1").processId("pr1").equipmentId("EQ-1")
                .operations(ops)
                .build();
    }

    private void stubHappyPath() {
        when(equipmentClient.getEquipment("EQ-1")).thenReturn(equipment);
        when(postprocessorRegistry.find("CNC-DEFAULT")).thenReturn(null);
        when(postprocessorRegistry.findByName("ISO6983")).thenReturn(isoPostprocessor);
        when(toolpathGenerator.generate(any(), any())).thenReturn(sampleToolpath());
        when(validator.validateFragment(anyString(), any())).thenReturn(ValidationResult.ok());
        when(validator.validate(anyString(), any())).thenReturn(ValidationResult.ok());
        when(storageClient.uploadText(anyString(), eq("p1"), eq("cnc"), anyString()))
                .thenReturn("s3://iktk/p1/cnc/prog.nc");
        when(repository.save(any(CncProgram.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------
    // generate
    // ------------------------------------------------------------------

    @Test
    void generate_happyPath() {
        stubHappyPath();
        CncProgramResponse response = service.generate(request(List.of(
                op(true, "Op1"), op(true, "Op2")
        )));

        assertThat(response).isNotNull();
        assertThat(response.getProgramId()).isNotBlank();
        assertThat(response.getProgramUri()).isEqualTo("s3://iktk/p1/cnc/prog.nc");
        assertThat(response.getEquipmentModel()).isEqualTo("CNC-DEFAULT");
        assertThat(response.getPostprocessor()).isEqualTo("ISO6983");
        assertThat(response.getOperations()).hasSize(2);
        assertThat(response.getValidation().isValid()).isTrue();

        ArgumentCaptor<CncProgram> captor = ArgumentCaptor.forClass(CncProgram.class);
        verify(repository).save(captor.capture());
        CncProgram saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo("p1");
        assertThat(saved.getProcessId()).isEqualTo("pr1");
        assertThat(saved.getOperations()).hasSize(2);
        assertThat(saved.getLineCount()).isGreaterThan(0);
        assertThat(saved.getByteSize()).isGreaterThan(0);
    }

    @Test
    void generate_emptyOperations_throws() {
        assertThatThrownBy(() -> service.generate(request(List.of())))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Нет операций");
    }

    @Test
    void generate_noCncOperations_throws() {
        assertThatThrownBy(() -> service.generate(request(List.of(
                op(false, "Op1"), op(false, "Op2")
        ))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Нет операций ЧПУ");
        verify(repository, never()).save(any());
    }

    @Test
    void generate_equipmentNull_throws() {
        when(equipmentClient.getEquipment("EQ-1")).thenReturn(null);
        assertThatThrownBy(() -> service.generate(request(List.of(op(true, "Op1")))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Станок не найден");
    }

    @Test
    void generate_nullRequest_throws() {
        assertThatThrownBy(() -> service.generate(null))
                .isInstanceOf(OrchestrationException.class);
    }

    @Test
    void generate_missingProjectId_throws() {
        CncProgramRequest req = CncProgramRequest.builder()
                .processId("pr1").equipmentId("EQ-1")
                .operations(List.of(op(true, "Op1")))
                .build();
        assertThatThrownBy(() -> service.generate(req))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");
    }

    // ------------------------------------------------------------------
    // regenerate / delete
    // ------------------------------------------------------------------

    @Test
    void regenerate_notFound_throws() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.regenerate("missing"))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("УП не найдена");
    }

    @Test
    void delete_callsStorageAndRepo() {
        CncProgram program = CncProgram.builder()
                .id("pid-1").projectId("p1").processId("pr1")
                .programUri("s3://iktk/p1/cnc/x.nc")
                .build();
        when(repository.findById("pid-1")).thenReturn(Optional.of(program));

        service.delete("pid-1");

        verify(storageClient).delete("s3://iktk/p1/cnc/x.nc");
        verify(repository).delete(program);
    }

    @Test
    void delete_storageFailure_doesNotThrow() {
        CncProgram program = CncProgram.builder()
                .id("pid-2").projectId("p1").projectId("p1").processId("pr1")
                .programUri("s3://iktk/p1/cnc/y.nc")
                .build();
        when(repository.findById("pid-2")).thenReturn(Optional.of(program));
        doThrow(new RuntimeException("minio down")).when(storageClient).delete(anyString());

        service.delete("pid-2");

        // storageClient.delete упал, но repository.delete всё равно вызван
        verify(repository).delete(program);
    }
}