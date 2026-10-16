package ru.iktk.modules.casting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class GatingSystem {

    @Column(name = "sprue_diameter_mm", precision = 10, scale = 4)
    private BigDecimal sprueDiameterMm;

    @Column(name = "runner_area_mm2", precision = 12, scale = 4)
    private BigDecimal runnerAreaMm2;

    @Column(name = "gate_area_mm2", precision = 12, scale = 4)
    private BigDecimal gateAreaMm2;

    @Column(name = "gates_count")
    private int gatesCount;

    @Column(name = "risers_count")
    private int risersCount;

    @Column(name = "pouring_time_sec", precision = 10, scale = 4)
    private BigDecimal pouringTimeSec;

    @Column(name = "metal_volume_mm3", precision = 18, scale = 4)
    private BigDecimal metalVolumeMm3;

    public GatingSystem() {}

    public BigDecimal getSprueDiameterMm() { return sprueDiameterMm; }
    public void setSprueDiameterMm(BigDecimal v) { this.sprueDiameterMm = v; }
    public BigDecimal getRunnerAreaMm2() { return runnerAreaMm2; }
    public void setRunnerAreaMm2(BigDecimal v) { this.runnerAreaMm2 = v; }
    public BigDecimal getGateAreaMm2() { return gateAreaMm2; }
    public void setGateAreaMm2(BigDecimal v) { this.gateAreaMm2 = v; }
    public int getGatesCount() { return gatesCount; }
    public void setGatesCount(int v) { this.gatesCount = v; }
    public int getRisersCount() { return risersCount; }
    public void setRisersCount(int v) { this.risersCount = v; }
    public BigDecimal getPouringTimeSec() { return pouringTimeSec; }
    public void setPouringTimeSec(BigDecimal v) { this.pouringTimeSec = v; }
    public BigDecimal getMetalVolumeMm3() { return metalVolumeMm3; }
    public void setMetalVolumeMm3(BigDecimal v) { this.metalVolumeMm3 = v; }

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