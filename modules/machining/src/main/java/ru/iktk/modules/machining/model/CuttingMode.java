package ru.iktk.modules.machining.model;

import java.math.BigDecimal;

/**
 * Режимы резания.
 */
public class CuttingMode {

    private BigDecimal cuttingSpeedMPerMin;
    private BigDecimal feedMmPerRev;
    private BigDecimal depthOfCutMm;
    private BigDecimal spindleSpeedRpm;
    private BigDecimal toolLifeMin;
    private MachiningMethod method;

    public CuttingMode() {
    }

    private CuttingMode(Builder b) {
        this.cuttingSpeedMPerMin = b.cuttingSpeedMPerMin;
        this.feedMmPerRev = b.feedMmPerRev;
        this.depthOfCutMm = b.depthOfCutMm;
        this.spindleSpeedRpm = b.spindleSpeedRpm;
        this.toolLifeMin = b.toolLifeMin;
        this.method = b.method;
    }

    public BigDecimal getCuttingSpeedMPerMin() { return cuttingSpeedMPerMin; }
    public BigDecimal getFeedMmPerRev() { return feedMmPerRev; }
    public BigDecimal getDepthOfCutMm() { return depthOfCutMm; }
    public BigDecimal getSpindleSpeedRpm() { return spindleSpeedRpm; }
    public BigDecimal getToolLifeMin() { return toolLifeMin; }
    public MachiningMethod getMethod() { return method; }

    /**
     * Краткое строковое представление режимов.
     */
    public String getSummary() {
        return String.format("V=%s м/мин, S=%s мм/об, t=%s мм, n=%s об/мин, T=%s мин",
                cuttingSpeedMPerMin, feedMmPerRev, depthOfCutMm,
                spindleSpeedRpm, toolLifeMin);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private BigDecimal cuttingSpeedMPerMin;
        private BigDecimal feedMmPerRev;
        private BigDecimal depthOfCutMm;
        private BigDecimal spindleSpeedRpm;
        private BigDecimal toolLifeMin;
        private MachiningMethod method;

        public Builder cuttingSpeedMPerMin(BigDecimal v) { this.cuttingSpeedMPerMin = v; return this; }
        public Builder feedMmPerRev(BigDecimal v) { this.feedMmPerRev = v; return this; }
        public Builder depthOfCutMm(BigDecimal v) { this.depthOfCutMm = v; return this; }
        public Builder spindleSpeedRpm(BigDecimal v) { this.spindleSpeedRpm = v; return this; }
        public Builder toolLifeMin(BigDecimal v) { this.toolLifeMin = v; return this; }
        public Builder method(MachiningMethod v) { this.method = v; return this; }

        public CuttingMode build() { return new CuttingMode(this); }
    }
}