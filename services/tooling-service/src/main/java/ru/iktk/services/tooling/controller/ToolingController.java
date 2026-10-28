package ru.iktk.services.tooling.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.services.tooling.dto.CuttingToolDto;
import ru.iktk.services.tooling.service.ToolingMethod;
import ru.iktk.services.tooling.service.ToolingService;

/**
 * REST-контроллер справочника режущего инструмента.
 *
 * <p>{@code GET /api/v1/tooling/select?method=MILLING&material=steel-45}
 * <ul>
 *   <li>200 + JSON — инструмент подобран;</li>
 *   <li>204 No Content — инструмент не требуется (CONTROL);</li>
 *   <li>400 Bad Request — неизвестный метод.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tooling")
public class ToolingController {

    private static final Logger log = LoggerFactory.getLogger(ToolingController.class);

    private final ToolingService toolingService;

    public ToolingController(ToolingService toolingService) {
        this.toolingService = toolingService;
    }

    @GetMapping("/select")
    public ResponseEntity<CuttingToolDto> select(
            @RequestParam("method") String method,
            @RequestParam(value = "material", required = false) String material) {

        ToolingMethod m;
        try {
            m = ToolingMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("ToolingController: unknown method='{}'", method);
            return ResponseEntity.badRequest().build();
        }

        CuttingToolDto tool = toolingService.select(m, material);
        if (tool == null) {
            log.info("ToolingController: tool not required (method={})", m);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(tool);
    }
}