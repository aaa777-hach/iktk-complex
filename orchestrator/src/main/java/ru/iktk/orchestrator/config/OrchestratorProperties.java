package ru.iktk.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iktk.orchestrator")
public class OrchestratorProperties {
    private int retryMaxAttempts = 3;
    private long retryBackoffMs = 2000;
    private boolean pointsOfControlEnabled = true;
    private String commandsExchange = "iktk.commands";
    private String resultsQueue = "iktk.orchestrator.results.queue";

    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int v) { this.retryMaxAttempts = v; }
    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long v) { this.retryBackoffMs = v; }
    public boolean isPointsOfControlEnabled() { return pointsOfControlEnabled; }
    public void setPointsOfControlEnabled(boolean v) { this.pointsOfControlEnabled = v; }
    public String getCommandsExchange() { return commandsExchange; }
    public void setCommandsExchange(String v) { this.commandsExchange = v; }
    public String getResultsQueue() { return resultsQueue; }
    public void setResultsQueue(String v) { this.resultsQueue = v; }
}