package ru.iktk.modules.casting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.iktk.modules.casting.dto.CastingRequest;
import ru.iktk.modules.casting.dto.CastingResponse;
import ru.iktk.modules.casting.service.CastingProcessService;

import java.math.BigDecimal;

@RestController
@RequestMapping(path = "/api/v1/casting", produces = "application/json")
@Tag(name = "Casting", description = "Модуль литья")
public class CastingController {

    private static final Logger log = LoggerFactory.getLogger(CastingController.class);

    private final CastingProcessService castingProcessService;

    public CastingController(CastingProcessService castingProcessService) {
        this.castingProcessService = castingProcessService;
    }

    @PostMapping(path = "/design", consumes = "application/json")
    @Operation(summary = "Дизайн процесса литья (нужен integration)")
    public ResponseEntity<CastingResponse> design(@RequestBody CastingRequest request) {
        log.info("REST design: projectId={}, objectRef={}", request.getProjectId(), request.getObjectRef());
        try {
            return ResponseEntity.ok(castingProcessService.designProcess(request));
        } catch (Exception e) {
            log.error("REST design failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Error", "INTEGRATION_UNAVAILABLE")
                    .build();
        }
    }

    @PostMapping(path = "/design-test", consumes = "application/json")
    @Operation(summary = "Тестовый режим: геометрия в теле запроса")
    public ResponseEntity<CastingResponse> designTest(@RequestBody TestDesignRequest request) {
        log.info("REST design-test: projectId={}, objectRef={}", request.getProjectId(), request.getObjectRef());

        CastingRequest castingRequest = new CastingRequest();
        castingRequest.setProjectId(request.getProjectId());
        castingRequest.setObjectRef(request.getObjectRef());
        castingRequest.setPreferredMethod(request.getPreferredMethod());

        CastingProcessService.GeometryData geometry = new CastingProcessService.GeometryData(
                request.getObjectRef(),
                request.getMaterial(),
                request.getVolume(),
                request.getMass(),
                request.getMinWallThickness(),
                request.getMinFilletRadius(),
                request.getMinDraftAngle(),
                request.getNominalDimensions(),
                request.getComplexityScore(),
                request.isUndercuts(),
                request.isHotSpots()
        );

        try {
            return ResponseEntity.ok(
                    castingProcessService.designProcessWithGeometry(castingRequest, geometry));
        } catch (Exception e) {
            log.error("REST design-test failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    public static class TestDesignRequest {
        private String projectId;
        private String objectRef;
        private ru.iktk.modules.casting.model.CastingMethod preferredMethod;
        private String material = "Сталь 45";
        private BigDecimal volume = BigDecimal.valueOf(1000);
        private BigDecimal mass = BigDecimal.valueOf(7.8);
        private BigDecimal minWallThickness = BigDecimal.valueOf(5);
        private BigDecimal minFilletRadius = BigDecimal.valueOf(3);
        private BigDecimal minDraftAngle = BigDecimal.valueOf(1.5);
        private BigDecimal nominalDimensions = BigDecimal.valueOf(100);
        private int complexityScore = 3;
        private boolean undercuts = false;
        private boolean hotSpots = false;

        public String getProjectId() { return projectId; }
        public void setProjectId(String v) { this.projectId = v; }
        public String getObjectRef() { return objectRef; }
        public void setObjectRef(String v) { this.objectRef = v; }
        public ru.iktk.modules.casting.model.CastingMethod getPreferredMethod() { return preferredMethod; }
        public void setPreferredMethod(ru.iktk.modules.casting.model.CastingMethod v) { this.preferredMethod = v; }
        public String getMaterial() { return material; }
        public void setMaterial(String v) { this.material = v; }
        public BigDecimal getVolume() { return volume; }
        public void setVolume(BigDecimal v) { this.volume = v; }
        public BigDecimal getMass() { return mass; }
        public void setMass(BigDecimal v) { this.mass = v; }
        public BigDecimal getMinWallThickness() { return minWallThickness; }
        public void setMinWallThickness(BigDecimal v) { this.minWallThickness = v; }
        public BigDecimal getMinFilletRadius() { return minFilletRadius; }
        public void setMinFilletRadius(BigDecimal v) { this.minFilletRadius = v; }
        public BigDecimal getMinDraftAngle() { return minDraftAngle; }
        public void setMinDraftAngle(BigDecimal v) { this.minDraftAngle = v; }
        public BigDecimal getNominalDimensions() { return nominalDimensions; }
        public void setNominalDimensions(BigDecimal v) { this.nominalDimensions = v; }
        public int getComplexityScore() { return complexityScore; }
        public void setComplexityScore(int v) { this.complexityScore = v; }
        public boolean isUndercuts() { return undercuts; }
        public void setUndercuts(boolean v) { this.undercuts = v; }
        public boolean isHotSpots() { return hotSpots; }
        public void setHotSpots(boolean v) { this.hotSpots = v; }
    }
}