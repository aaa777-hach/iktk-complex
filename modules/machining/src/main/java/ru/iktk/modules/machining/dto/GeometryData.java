package ru.iktk.modules.machining.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Данные геометрии, полученные из integration.
 *
 * <p>Поля {@code minInternalRadius, maxHoleDepthRatio, requiredAccuracy,
 * machiningAllowance, surfaceCount, holes, precisionHoles, thinRibs}
 * в {@code integration.Geometry} отсутствуют — при десериализации
 * используются разумные дефолты. Когда integration расширит контракт —
 * убрать дефолты.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeometryData {

    private final String objectRef;
    private final String material;
    private final BigDecimal volume;
    private final BigDecimal mass;
    private final BigDecimal minInternalRadius;
    private final BigDecimal minWallThickness;
    private final BigDecimal maxHoleDepthRatio;
    private final BigDecimal requiredAccuracy;
    private final BigDecimal machiningAllowance;
    private final int surfaceCount;
    private final int complexityScore;
    private final boolean undercuts;
    private final boolean holes;
    private final boolean precisionHoles;
    private final boolean thinRibs;

    @JsonCreator
    public GeometryData(
            @JsonProperty("objectRef") String objectRef,
            @JsonProperty("material") String material,
            @JsonProperty("volume") BigDecimal volume,
            @JsonProperty("mass") BigDecimal mass,
            @JsonProperty("minInternalRadius") BigDecimal minInternalRadius,
            @JsonProperty("minWallThickness") BigDecimal minWallThickness,
            @JsonProperty("maxHoleDepthRatio") BigDecimal maxHoleDepthRatio,
            @JsonProperty("requiredAccuracy") BigDecimal requiredAccuracy,
            @JsonProperty("machiningAllowance") BigDecimal machiningAllowance,
            @JsonProperty("surfaceCount") Integer surfaceCount,
            @JsonProperty("complexityScore") Integer complexityScore,
            @JsonProperty("undercuts") Boolean undercuts,
            @JsonProperty("holes") Boolean holes,
            @JsonProperty("precisionHoles") Boolean precisionHoles,
            @JsonProperty("thinRibs") Boolean thinRibs) {
        this.objectRef = objectRef;
        this.material = material;
        this.volume = volume;
        this.mass = mass;
        this.minInternalRadius = minInternalRadius != null ? minInternalRadius : BigDecimal.valueOf(2.0);
        this.minWallThickness = minWallThickness != null ? minWallThickness : BigDecimal.valueOf(1.0);
        this.maxHoleDepthRatio = maxHoleDepthRatio != null ? maxHoleDepthRatio : BigDecimal.valueOf(10.0);
        this.requiredAccuracy = requiredAccuracy != null ? requiredAccuracy : BigDecimal.valueOf(0.1);
        this.machiningAllowance = machiningAllowance != null ? machiningAllowance : BigDecimal.valueOf(2.0);
        this.surfaceCount = surfaceCount != null && surfaceCount > 0 ? surfaceCount : 1;
        this.complexityScore = complexityScore != null && complexityScore > 0 ? complexityScore : 1;
        this.undercuts = undercuts != null && undercuts;
        this.holes = holes != null && holes;
        this.precisionHoles = precisionHoles != null && precisionHoles;
        this.thinRibs = thinRibs != null && thinRibs;
    }

    public String getObjectRef() { return objectRef; }
    public String getMaterial() { return material; }
    public BigDecimal getVolume() { return volume; }
    public BigDecimal getMass() { return mass; }
    public BigDecimal getMinInternalRadius() { return minInternalRadius; }
    public BigDecimal getMinWallThickness() { return minWallThickness; }
    public BigDecimal getMaxHoleDepthRatio() { return maxHoleDepthRatio; }
    public BigDecimal getRequiredAccuracy() { return requiredAccuracy; }
    public BigDecimal getMachiningAllowance() { return machiningAllowance; }
    public int getSurfaceCount() { return surfaceCount; }
    public int getComplexityScore() { return complexityScore; }
    public boolean hasUndercuts() { return undercuts; }
    public boolean hasHoles() { return holes; }
    public boolean hasPrecisionHoles() { return precisionHoles; }
    public boolean hasThinRibs() { return thinRibs; }
}