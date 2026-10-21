package ru.iktk.recommendation.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendationProperties — геттеры/сеттеры/Scoring")
class RecommendationPropertiesTest {

    @Test
    @DisplayName("дефолты: BALANCED/HYBRID/10/5/0.5/enabled")
    void defaults() {
        RecommendationProperties p = new RecommendationProperties();
        assertThat(p.getDefaultStrategy()).isEqualTo("HYBRID");
        assertThat(p.getGapStrategy()).isEqualTo("HYBRID");
        assertThat(p.getMaxAlternatives()).isEqualTo(10);
        assertThat(p.getMaxRecommendations()).isEqualTo(10);
        assertThat(p.getMaxOutsourcingOptions()).isEqualTo(5);
        assertThat(p.getMinimumScore()).isEqualTo(0.5);
        assertThat(p.isVendorEnabled()).isTrue();
        assertThat(p.isOutsourcingEnabled()).isTrue();
        assertThat(p.isAutoGenerateRecommendations()).isTrue();
    }

    @Test
    @DisplayName("сеттеры меняют значения")
    void setters() {
        RecommendationProperties p = new RecommendationProperties();
        p.setDefaultStrategy("STRICT");
        p.setGapStrategy("FLEXIBLE");
        p.setMaxAlternatives(5);
        p.setMaxRecommendations(7);
        p.setMaxOutsourcingOptions(3);
        p.setMinimumScore(0.8);
        p.setVendorEnabled(false);
        p.setOutsourcingEnabled(false);
        p.setAutoGenerateRecommendations(false);
        assertThat(p.getDefaultStrategy()).isEqualTo("STRICT");
        assertThat(p.getGapStrategy()).isEqualTo("FLEXIBLE");
        assertThat(p.getMaxAlternatives()).isEqualTo(5);
        assertThat(p.getMaxRecommendations()).isEqualTo(7);
        assertThat(p.getMaxOutsourcingOptions()).isEqualTo(3);
        assertThat(p.getMinimumScore()).isEqualTo(0.8);
        assertThat(p.isVendorEnabled()).isFalse();
        assertThat(p.isOutsourcingEnabled()).isFalse();
        assertThat(p.isAutoGenerateRecommendations()).isFalse();
    }

    @Test
    @DisplayName("Scoring: дефолты + сеттеры")
    void scoring() {
        RecommendationProperties.Scoring s = new RecommendationProperties.Scoring();
        assertThat(s.getAccuracyWeight()).isEqualTo(0.30);
        assertThat(s.getPowerWeight()).isEqualTo(0.20);
        assertThat(s.getForceWeight()).isEqualTo(0.20);
        assertThat(s.getCncWeight()).isEqualTo(0.15);
        assertThat(s.getLeadTimeWeight()).isEqualTo(0.15);
        s.setAccuracyWeight(0.5);
        s.setPowerWeight(0.1);
        s.setForceWeight(0.1);
        s.setCncWeight(0.1);
        s.setLeadTimeWeight(0.2);
        assertThat(s.getAccuracyWeight()).isEqualTo(0.5);
        assertThat(s.getPowerWeight()).isEqualTo(0.1);
        assertThat(s.getForceWeight()).isEqualTo(0.1);
        assertThat(s.getCncWeight()).isEqualTo(0.1);
        assertThat(s.getLeadTimeWeight()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("getScoringWeights возвращает строку с 5 весами")
    void getScoringWeights() {
        RecommendationProperties p = new RecommendationProperties();
        String s = p.getScoringWeights();
        assertThat(s).contains("accuracy=", "power=", "force=", "cnc=", "leadTime=");
    }

    @Test
    @DisplayName("setScoring заменяет вложенный объект")
    void setScoring() {
        RecommendationProperties p = new RecommendationProperties();
        RecommendationProperties.Scoring s = new RecommendationProperties.Scoring();
        s.setAccuracyWeight(0.99);
        p.setScoring(s);
        assertThat(p.getScoring().getAccuracyWeight()).isEqualTo(0.99);
    }
}