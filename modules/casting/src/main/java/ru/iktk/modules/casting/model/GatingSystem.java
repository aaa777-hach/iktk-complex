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