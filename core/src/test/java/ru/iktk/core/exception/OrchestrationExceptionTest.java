package ru.iktk.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestrationException — исключение оркестратора")
class OrchestrationExceptionTest {

    @Test
    @DisplayName("Конструктор (message): код DEFAULT_CODE, контекст пустой")
    void constructor_message() {
        OrchestrationException e = new OrchestrationException("ошибка");
        assertThat(e.getMessage()).isEqualTo("ошибка");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.DEFAULT_CODE);
        assertThat(e.getContext()).isEmpty();
        assertThat(e.hasContext()).isFalse();
        assertThat(e.getCause()).isNull();
    }

    @Test
    @DisplayName("Конструктор (message, cause): cause сохраняется")
    void constructor_messageCause() {
        RuntimeException cause = new RuntimeException("root");
        OrchestrationException e = new OrchestrationException("ошибка", cause);
        assertThat(e.getCode()).isEqualTo(OrchestrationException.DEFAULT_CODE);
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Конструктор (code, message, context): контекст копируется и иммутабелен")
    void constructor_codeMessageContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("k", "v");

        OrchestrationException e = new OrchestrationException("CODE_1", "msg", ctx);
        assertThat(e.getCode()).isEqualTo("CODE_1");
        assertThat(e.getContext()).containsEntry("k", "v");

        ctx.put("new", "value");
        assertThat(e.getContext()).doesNotContainKey("new");
    }

    @Test
    @DisplayName("Конструктор (code, message, context, cause): все поля")
    void constructor_full() {
        RuntimeException cause = new RuntimeException("root");
        OrchestrationException e = new OrchestrationException(
                "CODE_2", "msg", Map.of("a", 1), cause);
        assertThat(e.getCode()).isEqualTo("CODE_2");
        assertThat(e.getMessage()).isEqualTo("msg");
        assertThat(e.getContext()).containsEntry("a", 1);
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Пустой/blank/null code -> DEFAULT_CODE")
    void constructor_invalidCode_defaults() {
        assertThat(new OrchestrationException(null, "m", null, null).getCode())
                .isEqualTo(OrchestrationException.DEFAULT_CODE);
        assertThat(new OrchestrationException("", "m", null, null).getCode())
                .isEqualTo(OrchestrationException.DEFAULT_CODE);
        assertThat(new OrchestrationException("   ", "m", null, null).getCode())
                .isEqualTo(OrchestrationException.DEFAULT_CODE);
    }

    @Test
    @DisplayName("null context -> пустая иммутабельная карта")
    void constructor_nullContext_emptyMap() {
        OrchestrationException e = new OrchestrationException("C", "m", null, null);
        assertThat(e.getContext()).isEmpty();
        assertThat(e.hasContext()).isFalse();
    }

    @Test
    @DisplayName("projectNotFound: код PROJECT_NOT_FOUND, контекст projectId")
    void factory_projectNotFound() {
        OrchestrationException e = OrchestrationException.projectNotFound("p1");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.PROJECT_NOT_FOUND);
        assertThat(e.getMessage()).contains("p1");
        assertThat(e.getContext()).containsEntry("projectId", "p1");
    }

    @Test
    @DisplayName("invalidTransition: код INVALID_TRANSITION, контекст state/event")
    void factory_invalidTransition() {
        OrchestrationException e = OrchestrationException.invalidTransition("NORMING", "NORMS_READY");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.INVALID_TRANSITION);
        assertThat(e.getContext()).containsEntry("state", "NORMING").containsEntry("event", "NORMS_READY");
    }

    @Test
    @DisplayName("eventNotAccepted: код EVENT_NOT_ACCEPTED")
    void factory_eventNotAccepted() {
        OrchestrationException e = OrchestrationException.eventNotAccepted("NORMING", "EVT");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.EVENT_NOT_ACCEPTED);
        assertThat(e.getContext()).containsEntry("state", "NORMING").containsEntry("event", "EVT");
    }

    @Test
    @DisplayName("routeMissing: код ROUTE_MISSING")
    void factory_routeMissing() {
        OrchestrationException e = OrchestrationException.routeMissing("UNKNOWN");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.ROUTE_MISSING);
        assertThat(e.getContext()).containsEntry("state", "UNKNOWN");
    }

    @Test
    @DisplayName("moduleSendFailed: код MODULE_SEND_FAILED, cause")
    void factory_moduleSendFailed() {
        RuntimeException cause = new RuntimeException("boom");
        OrchestrationException e = OrchestrationException.moduleSendFailed("norming", "p1", cause);
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.MODULE_SEND_FAILED);
        assertThat(e.getContext())
                .containsEntry("module", "norming")
                .containsEntry("projectId", "p1");
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("invalidModuleResult: код INVALID_MODULE_RESULT")
    void factory_invalidModuleResult() {
        OrchestrationException e = OrchestrationException.invalidModuleResult("p1", "bad payload");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.INVALID_MODULE_RESULT);
        assertThat(e.getContext())
                .containsEntry("projectId", "p1")
                .containsEntry("reason", "bad payload");
    }

    @Test
    @DisplayName("moduleRetriesExhausted: код MODULE_RETRIES_EXHAUSTED")
    void factory_moduleRetriesExhausted() {
        OrchestrationException e = OrchestrationException.moduleRetriesExhausted("norming", "NORMS_READY", 3);
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.MODULE_RETRIES_EXHAUSTED);
        assertThat(e.getContext())
                .containsEntry("module", "norming")
                .containsEntry("event", "NORMS_READY")
                .containsEntry("attempts", 3);
    }

    @Test
    @DisplayName("validationFailed: код VALIDATION_FAILED")
    void factory_validationFailed() {
        OrchestrationException e = OrchestrationException.validationFailed("projectName", "blank");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.VALIDATION_FAILED);
        assertThat(e.getContext())
                .containsEntry("field", "projectName")
                .containsEntry("reason", "blank");
    }

    @Test
    @DisplayName("versionNotFound: код VERSION_NOT_FOUND")
    void factory_versionNotFound() {
        OrchestrationException e = OrchestrationException.versionNotFound("p1", "v3");
        assertThat(e.getCode()).isEqualTo(OrchestrationException.ErrorCode.VERSION_NOT_FOUND);
        assertThat(e.getContext())
                .containsEntry("projectId", "p1")
                .containsEntry("version", "v3");
    }

    @Test
    @DisplayName("ErrorCode: константы имеют ожидаемые значения")
    void errorCodeConstants() {
        assertThat(OrchestrationException.ErrorCode.ORCHESTRATION_ERROR).isEqualTo("ORCHESTRATION_ERROR");
        assertThat(OrchestrationException.ErrorCode.PROJECT_NOT_FOUND).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(OrchestrationException.ErrorCode.INVALID_TRANSITION).isEqualTo("INVALID_TRANSITION");
        assertThat(OrchestrationException.ErrorCode.EVENT_NOT_ACCEPTED).isEqualTo("EVENT_NOT_ACCEPTED");
        assertThat(OrchestrationException.ErrorCode.ROUTE_MISSING).isEqualTo("ROUTE_MISSING");
        assertThat(OrchestrationException.ErrorCode.MODULE_SEND_FAILED).isEqualTo("MODULE_SEND_FAILED");
        assertThat(OrchestrationException.ErrorCode.INVALID_MODULE_RESULT).isEqualTo("INVALID_MODULE_RESULT");
        assertThat(OrchestrationException.ErrorCode.MODULE_RETRIES_EXHAUSTED).isEqualTo("MODULE_RETRIES_EXHAUSTED");
        assertThat(OrchestrationException.ErrorCode.VALIDATION_FAILED).isEqualTo("VALIDATION_FAILED");
        assertThat(OrchestrationException.ErrorCode.VERSION_NOT_FOUND).isEqualTo("VERSION_NOT_FOUND");
    }

    @Test
    @DisplayName("toString содержит код, сообщение, контекст и cause")
    void toString_containsFields() {
        RuntimeException cause = new IllegalStateException("root");
        OrchestrationException e = new OrchestrationException(
                "CODE", "msg", Map.of("k", "v"), cause);
        assertThat(e.toString()).contains("CODE", "msg", "k", "v", "IllegalStateException");
    }

    @Test
    @DisplayName("toString без контекста и без cause")
    void toString_minimal() {
        OrchestrationException e = new OrchestrationException("msg");
        assertThat(e.toString()).contains("ORCHESTRATION_ERROR", "msg");
        assertThat(e.toString()).doesNotContain("context");
    }
}