package ru.iktk.orchestrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.iktk.core.dto.OrchestrationRequest;
import ru.iktk.core.dto.OrchestrationResponse;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.service.DecisionLogService;
import ru.iktk.orchestrator.service.OrchestrationService;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.util.List;
import java.util.Map;

/**
 * REST API оркестратора ИКТК.
 *
 * <p>Предоставляет управление жизненным циклом проекта ТПП:
 * <ul>
 *   <li>создание и инициализация проекта;</li>
 *   <li>запуск оркестрации (анализ состояния и выбор следующего этапа);</li>
 *   <li>получение текущего состояния и истории переходов;</li>
 *   <li>точки контроля: подтверждение, корректировка, аутсорс, откат;</li>
 *   <li>журнал решений оркестратора.</li>
 * </ul>
 *
 * <p>Базовый путь: {@code /api/v1/orchestrator}.
 *
 * <p>Доступ ограничен ролями:
 * <ul>
 *   <li>чтение — {@code ROLE_ENGINEER}, {@code ROLE_MANAGER}, {@code ROLE_ADMIN};</li>
 *   <li>изменение — {@code ROLE_ENGINEER}, {@code ROLE_ADMIN};</li>
 *   <li>управление точками контроля — {@code ROLE_ENGINEER}, {@code ROLE_MANAGER}, {@code ROLE_ADMIN}.</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/v1/orchestrator",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Orchestrator", description = "Управление жизненным циклом проекта ТПП")
public class OrchestratorController {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorController.class);

    private final OrchestrationService orchestrationService;
    private final DecisionLogService decisionLogService;

    public OrchestratorController(OrchestrationService orchestrationService,
                                  DecisionLogService decisionLogService) {
        this.orchestrationService = orchestrationService;
        this.decisionLogService = decisionLogService;
    }

    // ------------------------------------------------------------------
    // Инициализация и запуск
    // ------------------------------------------------------------------

    /**
     * Создание нового проекта и запуск оркестрации.
     */
    @PostMapping(path = "/projects",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Создать проект и запустить оркестрацию",
            description = "Регистрирует проект в состоянии PROJECT_INIT и запускает конечный автомат.")
    public OrchestrationResponse createProject(
            @Valid @RequestBody OrchestrationRequest request) {
        log.info("createProject: projectName={}, requestedBy={}",
                request.getProjectName(), request.getRequestedBy());

        OrchestrationResponse response = orchestrationService.startProject(request);
        log.info("createProject done: projectId={}, state={}",
                response.getProjectId(), response.getState());
        return response;
    }

    /**
     * Запуск или продолжение оркестрации по проекту.
     */
    @PostMapping(path = "/projects/{projectId}/run")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Запустить/продолжить оркестрацию проекта")
    public OrchestrationResponse run(
            @Parameter(description = "Идентификатор проекта", required = true)
            @PathVariable String projectId) {
        log.info("run: projectId={}", projectId);
        return orchestrationService.run(projectId);
    }

    // ------------------------------------------------------------------
    // Состояние и история
    // ------------------------------------------------------------------

    /**
     * Текущее состояние проекта.
     */
    @GetMapping(path = "/projects/{projectId}/state")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Получить текущее состояние проекта")
    public ProjectState getState(
            @PathVariable String projectId) {
        log.debug("getState: projectId={}", projectId);
        return orchestrationService.getState(projectId);
    }

    /**
     * Сводка по проекту: состояние, текущий этап, последние события.
     */
    @GetMapping(path = "/projects/{projectId}/summary")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Сводка по проекту")
    public OrchestrationResponse getSummary(
            @PathVariable String projectId) {
        log.debug("getSummary: projectId={}", projectId);
        return orchestrationService.getSummary(projectId);
    }

    /**
     * История переходов и решений оркестратора.
     */
    @GetMapping(path = "/projects/{projectId}/history")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "История переходов и решений")
    public List<Map<String, Object>> getHistory(
            @PathVariable String projectId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        log.debug("getHistory: projectId={}, limit={}", projectId, limit);
        return decisionLogService.getHistory(projectId, limit);
    }

    // ------------------------------------------------------------------
    // Точки контроля
    // ------------------------------------------------------------------

    /**
     * Подтверждение текущего этапа (переход к следующему).
     */
    @PostMapping(path = "/projects/{projectId}/approve")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Подтвердить текущий этап (точка контроля)")
    public OrchestrationResponse approve(
            @PathVariable String projectId,
            @RequestBody(required = false) Map<String, String> payload) {
        String comment = payload == null ? null : payload.get("comment");
        log.info("approve: projectId={}, comment={}", projectId, comment);
        return orchestrationService.approve(projectId, comment);
    }

    /**
     * Отправка внешнего события в конечный автомат (точка контроля).
     */
    @PostMapping(path = "/projects/{projectId}/events")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Отправить событие в конечный автомат проекта")
    public OrchestrationResponse sendEvent(
            @PathVariable String projectId,
            @Parameter(description = "Событие конечного автомата", required = true)
            @RequestParam ProjectEvent event,
            @RequestBody(required = false) Map<String, String> payload) {
        log.info("sendEvent: projectId={}, event={}", projectId, event);
        return orchestrationService.sendEvent(projectId, event, payload);
    }

    /**
     * Принятие рекомендации при дефиците оборудования.
     */
    @PostMapping(path = "/projects/{projectId}/recommendations/{recommendationId}/accept")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Принять рекомендацию по оборудованию")
    public OrchestrationResponse acceptRecommendation(
            @PathVariable String projectId,
            @PathVariable String recommendationId,
            @RequestBody(required = false) Map<String, String> payload) {
        log.info("acceptRecommendation: projectId={}, recommendationId={}",
                projectId, recommendationId);
        return orchestrationService.acceptRecommendation(projectId, recommendationId, payload);
    }

    /**
     * Корректировка ТП под имеющееся оборудование.
     */
    @PostMapping(path = "/projects/{projectId}/recommendations/{recommendationId}/adjust")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Скорректировать ТП под имеющееся оборудование")
    public OrchestrationResponse adjustProcess(
            @PathVariable String projectId,
            @PathVariable String recommendationId,
            @RequestBody Map<String, String> payload) {
        log.info("adjustProcess: projectId={}, recommendationId={}",
                projectId, recommendationId);
        return orchestrationService.adjustProcess(projectId, recommendationId, payload);
    }

    /**
     * Выбор аутсорса при невозможности закрыть дефицит.
     */
    @PostMapping(path = "/projects/{projectId}/recommendations/{recommendationId}/outsource")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Выбрать аутсорс для операции")
    public OrchestrationResponse selectOutsourcing(
            @PathVariable String projectId,
            @PathVariable String recommendationId,
            @RequestBody Map<String, String> payload) {
        log.info("selectOutsourcing: projectId={}, recommendationId={}",
                projectId, recommendationId);
        return orchestrationService.selectOutsourcing(projectId, recommendationId, payload);
    }

    // ------------------------------------------------------------------
    // Откат и обработка ошибок
    // ------------------------------------------------------------------

    /**
     * Откат проекта к указанной версии/состоянию.
     */
    @PostMapping(path = "/projects/{projectId}/rollback")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Откатить проект к указанной версии")
    public OrchestrationResponse rollback(
            @PathVariable String projectId,
            @RequestParam(name = "version") String version) {
        log.warn("rollback: projectId={}, version={}", projectId, version);
        return orchestrationService.rollback(projectId, version);
    }

    /**
     * Обработка ошибок оркестрации.
     */
    @ExceptionHandler(OrchestrationException.class)
    public ResponseEntity<Map<String, Object>> handleOrchestrationException(
            OrchestrationException ex) {
        log.error("OrchestrationException: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "error", "ORCHESTRATION_ERROR",
                        "message", ex.getMessage()
                ));
    }
}