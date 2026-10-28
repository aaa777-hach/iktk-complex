package ru.iktk.modules.machining.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.machining.client.dto.ToolingDto;

/**
 * Feign-клиент к tooling-service (8099).
 *
 * <p>{@code GET /api/v1/tooling/select?method=&material=}:
 * <ul>
 *   <li>200 + JSON — инструмент;</li>
 *   <li>204 No Content — инструмент не требуется (CONTROL);</li>
 *   <li>400 — неизвестный метод (не наш случай, method — enum name).</li>
 * </ul>
 */
@FeignClient(
        name = "tooling",
        url = "${iktk.machining.tooling.url:http://localhost:8099}"
)
public interface ToolingClient {

    @GetMapping("/api/v1/tooling/select")
    ResponseEntity<ToolingDto> select(
            @RequestParam("method") String method,
            @RequestParam(value = "material", required = false) String material
    );
}