package ru.iktk.modules.norming.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.norming.dto.MaterialNormRequest;
import ru.iktk.modules.norming.model.MaterialInfo;
import ru.iktk.modules.norming.model.TimeNormSource;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "iktk.norming")
@Getter
@Setter
public class NormingProperties {

    public BigDecimal getBlankPrepTime(Stage s) { return BigDecimal.valueOf(5); }
    public BigDecimal getHeatTreatmentTime(Stage s) { return BigDecimal.valueOf(60); }
    public BigDecimal getCoatingTime(Stage s) { return BigDecimal.valueOf(10); }
    public BigDecimal getSetupTime(Stage s) { return BigDecimal.valueOf(10); }
    public BigDecimal getControlTime(Stage s) { return BigDecimal.valueOf(5); }
    public BigDecimal getHandlingTime(Stage s) { return BigDecimal.valueOf(2); }
    public BigDecimal getServicePercent(Stage s) { return BigDecimal.valueOf(3); }
    public BigDecimal getRestPercent(Stage s) { return BigDecimal.valueOf(4); }
    public BigDecimal getSetupTimeTpz(Stage s) { return BigDecimal.valueOf(20); }
    public BigDecimal getToolingSetupTime(Stage s) { return BigDecimal.valueOf(5); }
    public BigDecimal getEquipmentSetupTime(Stage s) { return BigDecimal.valueOf(8); }
    public BigDecimal getReferenceMachiningTime(Stage s) { return BigDecimal.valueOf(15); }
    public BigDecimal getMachineTime(Stage s) { return BigDecimal.valueOf(10); }
    public BigDecimal getMachiningLength(Stage s) { return BigDecimal.valueOf(100); }
    public int getPasses(Stage s) { return 2; }
    public BigDecimal getFeed(Stage s) { return BigDecimal.valueOf(0.2); }
    public BigDecimal getSpindleSpeed(Stage s) { return BigDecimal.valueOf(1000); }
    public BigDecimal getCastingMass(Stage s) { return BigDecimal.valueOf(5); }
    public BigDecimal getCastingBaseTime(Stage s) { return BigDecimal.valueOf(3); }
    public BigDecimal getComplexityFactor(Stage s) { return BigDecimal.valueOf(1.2); }
    public BigDecimal getForgingMass(Stage s) { return BigDecimal.valueOf(5); }
    public BigDecimal getForgingBaseTime(Stage s) { return BigDecimal.valueOf(2); }
    public BigDecimal getForgingMassFactor(Stage s) { return BigDecimal.valueOf(0.5); }
    public int getAssemblyConnections(Stage s) { return 4; }
    public BigDecimal getAssemblyTimePerConnection(Stage s) { return BigDecimal.valueOf(2); }
    public int getControlPoints(Stage s) { return 5; }
    public BigDecimal getControlTimePerPoint(Stage s) { return BigDecimal.valueOf(0.5); }
    public BigDecimal getBurnLossCoefficient(Stage s, MaterialInfo m) { return BigDecimal.valueOf(0.02); }
    public BigDecimal getFlashLossCoefficient(Stage s, MaterialInfo m) { return BigDecimal.valueOf(0.03); }
    public BigDecimal getGatingLossCoefficient(Stage s, MaterialInfo m) { return BigDecimal.valueOf(0.05); }
    public BigDecimal getScaleLossCoefficient(Stage s, MaterialInfo m) { return BigDecimal.valueOf(0.01); }
    public BigDecimal getChipRecoveryCoefficient(Stage s) { return BigDecimal.valueOf(0.15); }
    public BigDecimal getCutLossCoefficient(Stage s) { return BigDecimal.valueOf(0.03); }
    public BigDecimal getNonMultipleLossCoefficient(Stage s) { return BigDecimal.valueOf(0.02); }
    public BigDecimal getCoatingConsumption(Stage s) { return BigDecimal.valueOf(0.1); }
    public BigDecimal getAssemblyConsumption(Stage s) { return BigDecimal.valueOf(0.05); }
    public BigDecimal getToolLife(Stage s, MaterialInfo m) { return BigDecimal.valueOf(60); }
    public int getRegrindCount(Stage s) { return 3; }
    public String getToolType(Stage s) { return "cutter"; }
    public String getToolMaterial(Stage s, MaterialInfo m) { return "HSS"; }
    public BigDecimal getDefectCoefficient(String enterpriseCode) { return BigDecimal.valueOf(0.02); }
    public String getDefaultMaterial(Object type) { return "Steel 45"; }
    public MaterialInfo getMaterialInfo(String materialCode) {
        return new MaterialInfo(materialCode, materialCode, "grade",
                BigDecimal.valueOf(7.85), "GOST", BigDecimal.valueOf(100));
    }
    public BigDecimal getBlankVolume(TechnologyProcess process, MaterialNormRequest request) {
        return request.getBlankVolumeMm3() != null ? request.getBlankVolumeMm3() : BigDecimal.valueOf(100000);
    }
    public BigDecimal getPartVolume(TechnologyProcess process, MaterialNormRequest request) {
        return request.getPartVolumeMm3() != null ? request.getPartVolumeMm3() : BigDecimal.valueOf(80000);
    }
    public BigDecimal getReferenceBlankMass(TechnologyProcess process) { return BigDecimal.valueOf(1); }
    public BigDecimal getReferencePartMass(TechnologyProcess process) { return BigDecimal.valueOf(0.8); }
    public BigDecimal getCoefficient(TimeNormSource source, Stage stage) {
        return switch (source) {
            case GENERAL_MACHINE_BUILDING -> BigDecimal.valueOf(1.0);
            case INDUSTRY -> BigDecimal.valueOf(1.05);
            case PLANT -> BigDecimal.valueOf(1.10);
            case CALCULATED -> BigDecimal.valueOf(1.15);
            case ACTUAL -> BigDecimal.valueOf(1.0);
        };
    }
    public boolean hasPlantNorms(String enterpriseCode) { return false; }
    public boolean hasIndustryNorms(String industryCode) { return false; }
}
