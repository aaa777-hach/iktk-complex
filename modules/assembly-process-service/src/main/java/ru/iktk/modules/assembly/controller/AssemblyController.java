package ru.iktk.modules.assembly.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.model.AssemblyProcess;
import ru.iktk.modules.assembly.service.AssemblyProcessService;

import java.util.List;
import java.util.Map;

/**
 * REST API проектирования ТП сборки.
 *
 * <p>Базовый путь: {@code /api/v1/assembly}.
 */
@RestController
@RequestMapping("/api/v1/assembly")
public class AssemblyController {

    private static final Logger log = LoggerFactory.getLogger(AssemblyController.class);

    private final AssemblyProcessService assemblyProcessService;

    public AssemblyController(AssemblyProcessService assemblyProcessService) {
        this.assemblyProcessService = assemblyProcessService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "assembly-process-service"
        ));
    }

    @PostMapping("/processes")
    public ResponseEntity<AssemblyResponse> designProcess(@RequestBody AssemblyRequest request) {
        log.info("designProcess: projectId={}, objectRef={}, strategy={}",
                request.getProjectId(), request.getObjectRef(), request.getStrategy());
        try {
            AssemblyResponse response = assemblyProcessService.designProcess(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("designProcess: bad request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/processes/{processId}")
    public ResponseEntity<AssemblyProcess> getProcess(@PathVariable String processId) {
        try {
            return ResponseEntity.ok(assemblyProcessService.getById(processId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/processes")
    public ResponseEntity<List<AssemblyProcess>> listProcesses(@RequestParam("projectId") String projectId) {
        return ResponseEntity.ok(assemblyProcessService.listByProject(projectId));
    }
}