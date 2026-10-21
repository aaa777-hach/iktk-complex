package ru.iktk.bom.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.bom.service.BomExtractionService.RawBom;
import ru.iktk.bom.service.BomExtractionService.RawConnection;

import java.util.List;

/**
 * Feign-клиент к сервису integration (CAD-интеграция).
 */
@FeignClient(name = "integration", url = "${iktk.bom.integration-url:http://localhost:8095}")
public interface IntegrationClient {

    /**
     * Определить CAD-систему по проекту/геометрии.
     */
    @GetMapping("/api/v1/integration/cad-system")
    String detectCadSystem(@RequestParam("projectId") String projectId,
                           @RequestParam(value = "geometryId", required = false) String geometryId);

    /**
     * Извлечь «сырую» структуру BOM из CAD.
     */
    @GetMapping("/api/v1/integration/bom")
    RawBom extractBom(@RequestParam("projectId") String projectId,
                      @RequestParam(value = "geometryId", required = false) String geometryId,
                      @RequestParam(value = "cadSystem", required = false) String cadSystem);

    /**
     * Извлечь «сырые» связи сборки.
     */
    @GetMapping("/api/v1/integration/connections")
    List<RawConnection> extractConnections(@RequestParam("projectId") String projectId,
                                           @RequestParam(value = "geometryId", required = false) String geometryId,
                                           @RequestParam(value = "cadSystem", required = false) String cadSystem);
}