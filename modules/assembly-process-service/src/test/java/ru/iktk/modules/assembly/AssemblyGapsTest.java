package ru.iktk.modules.assembly;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.messaging.AssemblyCommandConsumer;
import ru.iktk.modules.assembly.model.AssemblyComponent;
import ru.iktk.modules.assembly.model.AssemblyProcess;
import ru.iktk.modules.assembly.model.AssemblyStage;
import ru.iktk.modules.assembly.repository.AssemblyComponentRepository;
import ru.iktk.modules.assembly.repository.AssemblyProcessRepository;
import ru.iktk.modules.assembly.repository.AssemblyStageRepository;
import ru.iktk.modules.assembly.repository.AssemblyToolingRepository;
import ru.iktk.modules.assembly.service.AssemblyProcessService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Assembly — пробелы покрытия (listStages, BOM-ветка, MessagePostProcessor)")
class AssemblyGapsTest {

    @Mock private AssemblyProcessRepository processRepository;
    @Mock private AssemblyStageRepository stageRepository;
    @Mock private AssemblyComponentRepository componentRepository;
    @Mock private AssemblyToolingRepository toolingRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private AssemblyProcessService serviceMock;

    private AssemblyProcessService serviceReal;
    private AssemblyCommandConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // реальный сервис — для тестов listStages / BOM-ветки
        serviceReal = new AssemblyProcessService(processRepository, stageRepository,
                componentRepository, toolingRepository);
        ReflectionTestUtils.setField(serviceReal, "demoFallback", true);
        ReflectionTestUtils.setField(serviceReal, "defaultStrategy", "SEQUENTIAL");
        when(processRepository.save(any(AssemblyProcess.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stageRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(componentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(toolingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // consumer — с МОКом сервиса (не с реальным!)
        objectMapper = new ObjectMapper();
        consumer = new AssemblyCommandConsumer(serviceMock, rabbitTemplate, objectMapper);
    }

    // ===== service: listStages =====

    @Test
    @DisplayName("listStages: ТП найден -> этапы из репозитория")
    void listStages_found() {
        AssemblyProcess p = new AssemblyProcess("p1", "proj1");
        when(processRepository.findById("p1")).thenReturn(Optional.of(p));
        AssemblyStage s = new AssemblyStage("s1", "p1", 1);
        when(stageRepository.findByAssemblyProcessIdOrderByStageOrderAsc("p1"))
                .thenReturn(List.of(s));

        List<AssemblyStage> out = serviceReal.listStages("p1");

        assertThat(out).hasSize(1).containsExactly(s);
        verify(stageRepository).findByAssemblyProcessIdOrderByStageOrderAsc("p1");
    }

    @Test
    @DisplayName("listStages: ТП не найден -> IllegalArgumentException (через getById)")
    void listStages_notFound_throws() {
        when(processRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceReal.listStages("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
        verify(stageRepository, never()).findByAssemblyProcessIdOrderByStageOrderAsc(anyString());
    }

    // ===== service: BOM-ветка =====

    @Test
    @DisplayName("designProcess: demoFallback=false + bomRef задан -> ровно 1 компонент C-BOM-001")
    void designProcess_demoFallbackFalse_bomRef_usesBomBranch() {
        ReflectionTestUtils.setField(serviceReal, "demoFallback", false);
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        req.setBomRef("bom-123");

        AssemblyResponse response = serviceReal.designProcess(req);

        assertThat(response.getComponentCount()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssemblyComponent>> cap = ArgumentCaptor.forClass(List.class);
        verify(componentRepository).saveAll(cap.capture());
        assertThat(cap.getValue())
                .extracting(AssemblyComponent::getDesignation)
                .containsExactly("C-BOM-001");
    }

    // ===== consumer: MessagePostProcessor =====

    @Test
    @DisplayName("onCommand: MPP ставит contentType и все headers (success=true)")
    void onCommand_mpp_setsHeaders() throws Exception {
        when(serviceMock.designProcess(any(AssemblyRequest.class))).thenReturn(okResponse());
        String json = objectMapper.writeValueAsString(Map.of(
                "projectId", "proj1",
                "payload", Map.of("objectRef", "obj-1")));

        consumer.onCommand(json, "r", "c");

        ArgumentCaptor<MessagePostProcessor> mppCap = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), mppCap.capture());

        MessageProperties props = new MessageProperties();
        mppCap.getValue().postProcessMessage(new Message(new byte[0], props));

        assertThat(props.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        Map<String, Object> headers = props.getHeaders();
        assertThat(headers)
                .containsEntry("projectId", "proj1")
                .containsKey("module")
                .containsKey("event")
                .containsEntry("success", Boolean.TRUE);
    }

    @Test
    @DisplayName("onCommand: MPP в failure-ветке ставит success=false")
    void onCommand_mpp_failureHeader() throws Exception {
        when(serviceMock.designProcess(any(AssemblyRequest.class)))
                .thenThrow(new RuntimeException("boom"));
        String json = objectMapper.writeValueAsString(Map.of("projectId", "proj1"));

        consumer.onCommand(json, "r", "c");

        ArgumentCaptor<MessagePostProcessor> mppCap = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), mppCap.capture());
        MessageProperties props = new MessageProperties();
        mppCap.getValue().postProcessMessage(new Message(new byte[0], props));
        assertThat(props.getHeaders()).containsEntry("success", Boolean.FALSE);
    }

    @Test
    @DisplayName("onCommand: convertAndSend падает -> catch, не пробрасывается")
    void onCommand_sendThrows_caught() throws Exception {
        when(serviceMock.designProcess(any(AssemblyRequest.class))).thenReturn(okResponse());
        // первый вызов (success-ветка) бросает, второй (failure-ветка в catch) — no-op
        doThrow(new RuntimeException("rabbit down"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(),
                        any(Object.class), any(MessagePostProcessor.class));
        String json = objectMapper.writeValueAsString(Map.of("projectId", "proj1"));

        assertThatCode(() -> consumer.onCommand(json, "r", "c"))
                .doesNotThrowAnyException();
    }

    private AssemblyResponse okResponse() {
        return AssemblyResponse.builder()
                .processId("proc-1")
                .strategy("SEQUENTIAL")
                .stageCount(5)
                .componentCount(3)
                .totalTimeMin(new BigDecimal("165.0"))
                .message("ok")
                .build();
    }
}