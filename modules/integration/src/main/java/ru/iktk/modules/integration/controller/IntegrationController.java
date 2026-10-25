package ru.iktk.modules.integration.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.dto.RawConnectionDto;
import ru.iktk.modules.integration.dto.RawItemDto;
import ru.iktk.modules.integration.model.Geometry;
import ru.iktk.modules.integration.repository.GeometryRepository;

import java.util.Collections;
import java.util.List;

/**
 * REST API интеграции с CAD: эндпоинты, которые ждёт bom-service
 * (IntegrationClient в bom-service).
 *
 * <p>Сейчас — маппинг одной {@link Geometry} из БД в структуру BOM:
 * 1 геометрия = 1 деталь, сборка = 1 деталь (для smoke).
 */
@RestController
@RequestMapping("/api/v1/integration")
public class IntegrationController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationController.class);

    private final GeometryRepository geometryRepository;

    public IntegrationController(GeometryRepository geometryRepository) {
        this.geometryRepository = geometryRepository;
    }

    /**
     * Определить CAD-систему (для smoke — фиксированно "STEP").
     */
    @GetMapping("/cad-system")
    public ResponseEntity<String> detectCadSystem(
            @RequestParam String projectId,
            @RequestParam(required = false) String geometryId) {
        log.info("detectCadSystem: projectId={}, geometryId={}", projectId, geometryId);
        return ResponseEntity.ok("STEP");
    }

    /**
     * Извлечь BOM: 1 геометрия -> 1 деталь в сборке.
     */
    @GetMapping("/bom")
    public ResponseEntity<RawBomDto> extractBom(
            @RequestParam String projectId,
            @RequestParam(required = false) String geometryId,
            @RequestParam(required = false) String cadSystem) {
        log.info("extractBom: projectId={}, geometryId={}, cadSystem={}", projectId, geometryId, cadSystem);
        if (geometryId == null || geometryId.isBlank()) {
            geometryId = "geo-12345"; // default из seed
        }
        Geometry g = geometryRepository.findByObjectRef(geometryId).orElse(null);
        if (g == null) {
            log.warn("extractBom: geometry not found for objectRef={}", geometryId);
            return ResponseEntity.notFound().build();
        }

        RawBomDto bom = new RawBomDto();
        bom.setRootId("ASSY-" + projectId);
        bom.setRootDesignation("ASSY-" + projectId);
        bom.setRootName("Сборка " + projectId);

        RawItemDto item = new RawItemDto();
        item.setId(g.getObjectRef());
        item.setParentId(null);
        item.setPosition("1");
        item.setDesignation(g.getObjectRef());
        item.setName(g.getObjectRef());
        item.setQuantity(1);
        item.setUnit("шт");
        item.setMaterial(g.getMaterial());
        item.setType("PART");
        item.setHasChildren(false);

        bom.setItems(List.of(item));
        log.info("extractBom: found geometry objectRef={}, material={}, items=1", geometryId, g.getMaterial());
        return ResponseEntity.ok(bom);
    }

    /**
     * Связи сборки (в seed связей нет — пустой список).
     */
    @GetMapping("/connections")
    public ResponseEntity<List<RawConnectionDto>> extractConnections(
            @RequestParam String projectId,
            @RequestParam(required = false) String geometryId,
            @RequestParam(required = false) String cadSystem) {
        log.info("extractConnections: projectId={}, geometryId={}, cadSystem={}", projectId, geometryId, cadSystem);
        return ResponseEntity.ok(Collections.emptyList());
    }
}
