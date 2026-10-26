package ru.iktk.modules.machining.dto;

import java.math.BigDecimal;

    /** Данные геометрии, полученные из integration. */
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

        public GeometryData(String objectRef, String material, BigDecimal volume,
                            BigDecimal mass, BigDecimal minInternalRadius,
                            BigDecimal minWallThickness, BigDecimal maxHoleDepthRatio,
                            BigDecimal requiredAccuracy, BigDecimal machiningAllowance,
                            int surfaceCount, int complexityScore, boolean undercuts,
                            boolean holes, boolean precisionHoles, boolean thinRibs) {
            this.objectRef = objectRef;
            this.material = material;
            this.volume = volume;
            this.mass = mass;
            this.minInternalRadius = minInternalRadius;
            this.minWallThickness = minWallThickness;
            this.maxHoleDepthRatio = maxHoleDepthRatio;
            this.requiredAccuracy = requiredAccuracy;
            this.machiningAllowance = machiningAllowance;
            this.surfaceCount = surfaceCount;
            this.complexityScore = complexityScore;
            this.undercuts = undercuts;
            this.holes = holes;
            this.precisionHoles = precisionHoles;
            this.thinRibs = thinRibs;
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
