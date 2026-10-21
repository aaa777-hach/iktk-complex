package ru.iktk.bom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Типизированные настройки сервиса BOM.
 */
@ConfigurationProperties(prefix = "iktk.bom")
public class BomProperties {

    /** Максимальная глубина иерархии BOM. */
    private int maxDepth = 20;

    /** Количество повторных попыток при недоступности CAD. */
    private int retryCount = 3;

    /** Поддерживаемые CAD-системы. */
    private List<String> cadSystems = List.of("SIEMENS", "DASSAULT", "T_FLEX", "KOMPAS");

    /** URL сервиса integration. */
    private String integrationUrl = "http://localhost:8095";

    /** Использовать demo-fallback при недоступности CAD. */
    private boolean demoFallback = false;

    /** Стратегия по умолчанию. */
    private String defaultStrategy = "SEQUENTIAL";

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public List<String> getCadSystems() { return cadSystems; }
    public void setCadSystems(List<String> cadSystems) { this.cadSystems = cadSystems; }
    public String getIntegrationUrl() { return integrationUrl; }
    public void setIntegrationUrl(String integrationUrl) { this.integrationUrl = integrationUrl; }
    public boolean isDemoFallback() { return demoFallback; }
    public void setDemoFallback(boolean demoFallback) { this.demoFallback = demoFallback; }
    public int getExtractionRetry() { return retryCount; }
    public int getExtractionRetryDelayMs() { return 1000; }

    public String getDefaultStrategy() { return defaultStrategy; }
    public void setDefaultStrategy(String defaultStrategy) { this.defaultStrategy = defaultStrategy; }
}