package ru.iktk.modules.integration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Геометрия объекта (детали) для проектирования ТП.
 *
 * <p>Таблица: {@code geometry} (PostgreSQL, миграция V1).
 * Поля соответствуют {@code CastingProcessService.GeometryData} (JSON-контракт).
 */
@Entity
@Table(name = "geometry")
public class Geometry {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @Column(name = "object_ref", nullable = false, length = 64, unique = true)
    private String objectRef;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "material", nullable = false, length = 100)
    private String material;

    @Column(name = "volume", nullable = false, precision = 18, scale = 4)
    private BigDecimal volume;

    @Column(name = "mass", nullable = false, precision = 18, scale = 4)
    private BigDecimal mass;

    @Column(name = "min_wall_thickness", nullable = false, precision = 10, scale = 4)
    private BigDecimal minWallThickness;

    @Column(name = "min_fillet_radius", nullable = false, precision = 10, scale = 4)
    private BigDecimal minFilletRadius;

    @Column(name = "min_draft_angle", nullable = false, precision = 10, scale = 4)
    private BigDecimal minDraftAngle;

    @Column(name = "nominal_dimensions", nullable = false, precision = 12, scale = 4)
    private BigDecimal nominalDimensions;

    @Column(name = "complexity_score", nullable = false)
    private int complexityScore;

    @Column(name = "undercuts", nullable = false)
    private boolean undercuts;

    @Column(name = "hot_spots", nullable = false)
    private boolean hotSpots;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Geometry() {}

    public String getObjectRef() { return objectRef; }
    public String getProjectId() { return projectId; }
    public String getMaterial() { return material; }
    public BigDecimal getVolume() { return volume; }
    public BigDecimal getMass() { return mass; }
    public BigDecimal getMinWallThickness() { return minWallThickness; }
    public BigDecimal getMinFilletRadius() { return minFilletRadius; }
    public BigDecimal getMinDraftAngle() { return minDraftAngle; }
    public BigDecimal getNominalDimensions() { return nominalDimensions; }
    public int getComplexityScore() { return complexityScore; }
    public boolean isUndercuts() { return undercuts; }
    public boolean isHotSpots() { return hotSpots; }
}