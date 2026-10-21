package ru.iktk.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "iktk.recommendation")
public class RecommendationProperties {

    private String defaultStrategy = "HYBRID";
    private String gapStrategy = "HYBRID";
    private int maxAlternatives = 10;
    private int maxRecommendations = 10;
    private int maxOutsourcingOptions = 5;
    private double minimumScore = 0.5;
    private boolean vendorEnabled = true;
    private boolean outsourcingEnabled = true;
    private boolean autoGenerateRecommendations = true;
    private Scoring scoring = new Scoring();

    public static class Scoring {
        private double accuracyWeight = 0.30;
        private double powerWeight = 0.20;
        private double forceWeight = 0.20;
        private double cncWeight = 0.15;
        private double leadTimeWeight = 0.15;

        public double getAccuracyWeight() { return accuracyWeight; }
        public void setAccuracyWeight(double v) { this.accuracyWeight = v; }
        public double getPowerWeight() { return powerWeight; }
        public void setPowerWeight(double v) { this.powerWeight = v; }
        public double getForceWeight() { return forceWeight; }
        public void setForceWeight(double v) { this.forceWeight = v; }
        public double getCncWeight() { return cncWeight; }
        public void setCncWeight(double v) { this.cncWeight = v; }
        public double getLeadTimeWeight() { return leadTimeWeight; }
        public void setLeadTimeWeight(double v) { this.leadTimeWeight = v; }
    }

    public String getDefaultStrategy() { return defaultStrategy; }
    public void setDefaultStrategy(String v) { this.defaultStrategy = v; }
    public String getGapStrategy() { return gapStrategy; }
    public void setGapStrategy(String v) { this.gapStrategy = v; }
    public int getMaxAlternatives() { return maxAlternatives; }
    public void setMaxAlternatives(int v) { this.maxAlternatives = v; }
    public int getMaxRecommendations() { return maxRecommendations; }
    public void setMaxRecommendations(int v) { this.maxRecommendations = v; }
    public int getMaxOutsourcingOptions() { return maxOutsourcingOptions; }
    public void setMaxOutsourcingOptions(int v) { this.maxOutsourcingOptions = v; }
    public double getMinimumScore() { return minimumScore; }
    public void setMinimumScore(double v) { this.minimumScore = v; }
    public boolean isVendorEnabled() { return vendorEnabled; }
    public void setVendorEnabled(boolean v) { this.vendorEnabled = v; }
    public boolean isOutsourcingEnabled() { return outsourcingEnabled; }
    public void setOutsourcingEnabled(boolean v) { this.outsourcingEnabled = v; }
    public boolean isAutoGenerateRecommendations() { return autoGenerateRecommendations; }
    public void setAutoGenerateRecommendations(boolean v) { this.autoGenerateRecommendations = v; }
    public Scoring getScoring() { return scoring; }
    public void setScoring(Scoring v) { this.scoring = v; }

    public String getScoringWeights() {
        return String.format("accuracy=%s,power=%s,force=%s,cnc=%s,leadTime=%s",
                scoring.getAccuracyWeight(), scoring.getPowerWeight(),
                scoring.getForceWeight(), scoring.getCncWeight(),
                scoring.getLeadTimeWeight());
    }
}