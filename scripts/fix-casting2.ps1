$root = $PSScriptRoot
$base = Join-Path $root 'modules\casting\src\main\java\ru\iktk\modules\casting'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Write-Java($relPath, $content) {
    $full = Join-Path $base $relPath
    $dir = Split-Path $full -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [IO.File]::WriteAllText($full, $content, $utf8)
    Write-Host ("  OK: " + $relPath)
}

# 1. CastingMethod (enum)
Write-Java 'model\CastingMethod.java' @'
package ru.iktk.modules.casting.model;

public enum CastingMethod {
    SAND_CASTING,
    DIE_CASTING,
    INVESTMENT_CASTING,
    CENTRIFUGAL_CASTING,
    GRAVITY_CASTING
}
'@

# 2. CastingProperties
Write-Java 'config\CastingProperties.java' @'
package ru.iktk.modules.casting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.iktk.modules.casting.model.CastingMethod;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "iktk.casting")
public class CastingProperties {

    private String defaultMethod = "SAND_CASTING";
    private BigDecimal minWallThickness = BigDecimal.valueOf(3);
    private BigDecimal minFilletRadius = BigDecimal.valueOf(2);
    private BigDecimal minDraftAngle = BigDecimal.valueOf(1);

    public String getDefaultMethod() { return defaultMethod; }
    public void setDefaultMethod(String v) { this.defaultMethod = v; }
    public BigDecimal getMinWallThickness() { return minWallThickness; }
    public void setMinWallThickness(BigDecimal v) { this.minWallThickness = v; }
    public BigDecimal getMinFilletRadius() { return minFilletRadius; }
    public void setMinFilletRadius(BigDecimal v) { this.minFilletRadius = v; }
    public BigDecimal getMinDraftAngle() { return minDraftAngle; }
    public void setMinDraftAngle(BigDecimal v) { this.minDraftAngle = v; }

    public BigDecimal getShrinkageCoefficient(String material, CastingMethod method) {
        if (material == null) return BigDecimal.valueOf(0.01);
        String m = material.toLowerCase();
        if (m.contains("алюмин")) return BigDecimal.valueOf(0.013);
        if (m.contains("чугун")) return BigDecimal.valueOf(0.010);
        if (m.contains("сталь")) return BigDecimal.valueOf(0.020);
        if (m.contains("бронз")) return BigDecimal.valueOf(0.015);
        return BigDecimal.valueOf(0.012);
    }

    public BigDecimal getMachiningAllowance(CastingMethod method, BigDecimal dimensions) {
        return BigDecimal.valueOf(3);
    }

    public String getToolingMaterial(CastingMethod method) {
        return "Сталь 45";
    }

    public BigDecimal estimateToolingCost(CastingMethod method, Object geometry) {
        return BigDecimal.valueOf(150000);
    }
}
'@

# 3. GeometryClient
Write-Java 'client\GeometryClient.java' @'
package ru.iktk.modules.casting.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.casting.service.CastingProcessService;

@FeignClient(name = "integration", url = "${iktk.integration.url:http://integration:8080}")
public interface GeometryClient {

    @GetMapping("/api/v1/geometry/{projectId}")
    CastingProcessService.GeometryData fetchGeometry(
            @PathVariable("projectId") String projectId,
            @RequestParam("objectRef") String objectRef);
}
'@

# 4. EquipmentClient
Write-Java 'client\EquipmentClient.java' @'
package ru.iktk.modules.casting.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.casting.model.CastingMethod;

@FeignClient(name = "equipment-service", url = "${iktk.equipment.url:http://equipment-service:8080}")
public interface EquipmentClient {

    @GetMapping("/api/v1/equipment/is-method-available")
    boolean isMethodAvailable(@RequestParam("projectId") String projectId,
                              @RequestParam("method") CastingMethod method);
}
'@

# 5. CastingRequest
Write-Java 'dto\CastingRequest.java' @'
package ru.iktk.modules.casting.dto;

import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.casting.model.CastingMethod;

public class CastingRequest {
    private String projectId;
    private String objectRef;
    private TechnologyProcess.ProductionType productionType;
    private CastingMethod preferredMethod;

    public String getProjectId() { return projectId; }
    public void setProjectId(String v) { this.projectId = v; }
    public String getObjectRef() { return objectRef; }
    public void setObjectRef(String v) { this.objectRef = v; }
    public TechnologyProcess.ProductionType getProductionType() { return productionType; }
    public void setProductionType(TechnologyProcess.ProductionType v) { this.productionType = v; }
    public CastingMethod getPreferredMethod() { return preferredMethod; }
    public void setPreferredMethod(CastingMethod v) { this.preferredMethod = v; }
}
'@

# 6. ShrinkageCalculation
Write-Java 'model\ShrinkageCalculation.java' @'
package ru.iktk.modules.casting.model;

import java.math.BigDecimal;

public class ShrinkageCalculation {
    private BigDecimal shrinkagePercent;
    private BigDecimal linearAllowanceMm;
    private String material;
    private CastingMethod method;

    public BigDecimal getShrinkagePercent() { return shrinkagePercent; }
    public void setShrinkagePercent(BigDecimal v) { this.shrinkagePercent = v; }
    public BigDecimal getLinearAllowanceMm() { return linearAllowanceMm; }
    public void setLinearAllowanceMm(BigDecimal v) { this.linearAllowanceMm = v; }
    public String getMaterial() { return material; }
    public void setMaterial(String v) { this.material = v; }
    public CastingMethod getMethod() { return method; }
    public void setMethod(CastingMethod v) { this.method = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ShrinkageCalculation s = new ShrinkageCalculation();
        public Builder shrinkagePercent(BigDecimal v) { s.shrinkagePercent = v; return this; }
        public Builder linearAllowanceMm(BigDecimal v) { s.linearAllowanceMm = v; return this; }
        public Builder material(String v) { s.material = v; return this; }
        public Builder method(CastingMethod v) { s.method = v; return this; }
        public ShrinkageCalculation build() { return s; }
    }
}
'@

# 7. GatingSystem
Write-Java 'model\GatingSystem.java' @'
package ru.iktk.modules.casting.model;

import java.math.BigDecimal;

public class GatingSystem {
    private BigDecimal sprueDiameterMm;
    private BigDecimal runnerAreaMm2;
    private BigDecimal gateAreaMm2;
    private int gatesCount;
    private int risersCount;
    private BigDecimal pouringTimeSec;
    private BigDecimal metalVolumeMm3;

    public BigDecimal getSprueDiameterMm() { return sprueDiameterMm; }
    public BigDecimal getRunnerAreaMm2() { return runnerAreaMm2; }
    public BigDecimal getGateAreaMm2() { return gateAreaMm2; }
    public int getGatesCount() { return gatesCount; }
    public int getRisersCount() { return risersCount; }
    public BigDecimal getPouringTimeSec() { return pouringTimeSec; }
    public BigDecimal getMetalVolumeMm3() { return metalVolumeMm3; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final GatingSystem g = new GatingSystem();
        public Builder sprueDiameterMm(BigDecimal v) { g.sprueDiameterMm = v; return this; }
        public Builder runnerAreaMm2(BigDecimal v) { g.runnerAreaMm2 = v; return this; }
        public Builder gateAreaMm2(BigDecimal v) { g.gateAreaMm2 = v; return this; }
        public Builder gatesCount(int v) { g.gatesCount = v; return this; }
        public Builder risersCount(int v) { g.risersCount = v; return this; }
        public Builder pouringTimeSec(BigDecimal v) { g.pouringTimeSec = v; return this; }
        public Builder metalVolumeMm3(BigDecimal v) { g.metalVolumeMm3 = v; return this; }
        public GatingSystem build() { return g; }
    }
}
'@

# 8. CastingProcess
Write-Java 'model\CastingProcess.java' @'
package ru.iktk.modules.casting.model;

import ru.iktk.core.domain.Stage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CastingProcess {
    private String id;
    private String projectId;
    private String objectRef;
    private CastingMethod method;
    private ShrinkageCalculation shrinkage;
    private GatingSystem gatingSystem;
    private Object tooling;
    private List<Stage> stages = new ArrayList<>();
    private Object manufacturabilityReport;
    private Instant createdAt;

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getObjectRef() { return objectRef; }
    public CastingMethod getMethod() { return method; }
    public ShrinkageCalculation getShrinkage() { return shrinkage; }
    public GatingSystem getGatingSystem() { return gatingSystem; }
    public List<Stage> getStages() { return stages; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CastingProcess p = new CastingProcess();
        public Builder id(String v) { p.id = v; return this; }
        public Builder projectId(String v) { p.projectId = v; return this; }
        public Builder objectRef(String v) { p.objectRef = v; return this; }
        public Builder method(CastingMethod v) { p.method = v; return this; }
        public Builder shrinkage(ShrinkageCalculation v) { p.shrinkage = v; return this; }
        public Builder gatingSystem(GatingSystem v) { p.gatingSystem = v; return this; }
        public Builder tooling(Object v) { p.tooling = v; return this; }
        public Builder stages(List<Stage> v) { p.stages = v; return this; }
        public Builder manufacturabilityReport(Object v) { p.manufacturabilityReport = v; return this; }
        public Builder createdAt(Instant v) { p.createdAt = v; return this; }
        public CastingProcess build() { return p; }
    }
}
'@

# 9. CastingResponse
Write-Java 'dto\CastingResponse.java' @'
package ru.iktk.modules.casting.dto;

import ru.iktk.core.domain.Stage;
import ru.iktk.modules.casting.model.CastingMethod;
import ru.iktk.modules.casting.model.GatingSystem;
import ru.iktk.modules.casting.model.ShrinkageCalculation;

import java.util.ArrayList;
import java.util.List;

public class CastingResponse {
    private String projectId;
    private String processId;
    private CastingMethod method;
    private List<Stage> stages = new ArrayList<>();
    private ShrinkageCalculation shrinkage;
    private GatingSystem gatingSystem;
    private Object tooling;
    private List<String> warnings = new ArrayList<>();

    public String getProjectId() { return projectId; }
    public String getProcessId() { return processId; }
    public CastingMethod getMethod() { return method; }
    public List<Stage> getStages() { return stages; }
    public List<String> getWarnings() { return warnings; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CastingResponse r = new CastingResponse();
        public Builder projectId(String v) { r.projectId = v; return this; }
        public Builder processId(String v) { r.processId = v; return this; }
        public Builder method(CastingMethod v) { r.method = v; return this; }
        public Builder stages(List<Stage> v) { r.stages = v; return this; }
        public Builder shrinkage(ShrinkageCalculation v) { r.shrinkage = v; return this; }
        public Builder gatingSystem(GatingSystem v) { r.gatingSystem = v; return this; }
        public Builder tooling(Object v) { r.tooling = v; return this; }
        public Builder warnings(List<String> v) { r.warnings = v; return this; }
        public CastingResponse build() { return r; }
    }
}
'@

# 10. CastingProcessRepository
Write-Java 'repository\CastingProcessRepository.java' @'
package ru.iktk.modules.casting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.modules.casting.model.CastingProcess;

@Repository
public interface CastingProcessRepository extends JpaRepository<CastingProcess, String> {
}
'@

Write-Host ""
Write-Host "DONE: 10 files created" -ForegroundColor Green