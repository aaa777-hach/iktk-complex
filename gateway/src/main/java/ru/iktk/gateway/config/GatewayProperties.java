package ru.iktk.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация API Gateway ИКТК.
 * Читает настройки из application.yml с префиксом iktk.gateway.
 */
@ConfigurationProperties(prefix = "iktk.gateway")
public class GatewayProperties {

    private String orchestratorUri = "http://localhost:8080";
    private String authUri = "http://localhost:8081";
    private String equipmentUri = "http://localhost:8094";
    private String bomUri = "http://localhost:8091";
    private String assemblyUri = "http://localhost:8097";
    private String recommendationUri = "http://localhost:8098";
    private String castingUri = "http://localhost:8090";

    private List<String> corsAllowedOrigins = new ArrayList<>(List.of(
            "http://localhost",
            "http://localhost:80",
            "http://localhost:5173",
            "http://localhost:3000",
            "http://localhost:8085"
    ));

    private int rateLimitReplenish = 100;
    private int rateLimitBurst = 200;

    public String getOrchestratorUri() { return orchestratorUri; }
    public void setOrchestratorUri(String orchestratorUri) { this.orchestratorUri = orchestratorUri; }

    public String getAuthUri() { return authUri; }
    public void setAuthUri(String authUri) { this.authUri = authUri; }

    public String getEquipmentUri() { return equipmentUri; }
    public void setEquipmentUri(String equipmentUri) { this.equipmentUri = equipmentUri; }

    public String getBomUri() { return bomUri; }
    public void setBomUri(String bomUri) { this.bomUri = bomUri; }

    public String getAssemblyUri() { return assemblyUri; }
    public void setAssemblyUri(String assemblyUri) { this.assemblyUri = assemblyUri; }

    public String getRecommendationUri() { return recommendationUri; }
    public void setRecommendationUri(String recommendationUri) { this.recommendationUri = recommendationUri; }

    public String getCastingUri() { return castingUri; }
    public void setCastingUri(String castingUri) { this.castingUri = castingUri; }

    public List<String> getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }

    public int getRateLimitReplenish() { return rateLimitReplenish; }
    public void setRateLimitReplenish(int rateLimitReplenish) { this.rateLimitReplenish = rateLimitReplenish; }

    public int getRateLimitBurst() { return rateLimitBurst; }
    public void setRateLimitBurst(int rateLimitBurst) { this.rateLimitBurst = rateLimitBurst; }
}