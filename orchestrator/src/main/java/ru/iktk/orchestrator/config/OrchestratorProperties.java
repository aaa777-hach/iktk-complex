package ru.iktk.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки оркестратора ИКТК.
 *
 * <p>Префикс: {@code iktk.orchestrator}. Все поля имеют значения по умолчанию,
 * поэтому приложение стартует без явной конфигурации в {@code application.yml}.
 *
 * <p>Секция {@code stub} — заглушка выбора ТП на первом этапе (до анализа
 * модели). Если {@code stub-enabled=true} и в запросе не передан
 * {@code plannedProcessTypes}, оркестратор выбирает ТП из
 * {@code stub-process-type} (или из поля {@code stubProcessType} запроса).
 * Коды заглушки: MACHINING → 1, CASTING → 2, FORGING → 3.
 */
@ConfigurationProperties(prefix = "iktk.orchestrator")
public class OrchestratorProperties {

    // ------------------------------------------------------------------
    // Retry
    // ------------------------------------------------------------------

    /** Максимум попыток при отказе модуля (включая первую). */
    private int retryMaxAttempts = 3;

    /** Задержка перед повтором, мс (0 = без задержки). */
    private long retryBackoffMs = 2000;

    // ------------------------------------------------------------------
    // Точки контроля
    // ------------------------------------------------------------------

    /** Включать ли точки контроля (иначе всё автоматически). */
    private boolean pointsOfControlEnabled = true;

    // ------------------------------------------------------------------
    // Messaging
    // ------------------------------------------------------------------

    /** Обменник команд. */
    private String commandsExchange = "iktk.commands";

    /** Очередь результатов. */
    private String resultsQueue = "iktk.orchestrator.results.queue";

    // ------------------------------------------------------------------
    // Заглушка выбора ТП (на первом этапе)
    // ------------------------------------------------------------------

    /** Включена ли заглушка выбора ТП (если plannedProcessTypes не задан). */
    private boolean stubEnabled = true;

    /** ТП по умолчанию для заглушки: MACHINING / CASTING / FORGING. */
    private String stubProcessType = "MACHINING";

    // ------------------------------------------------------------------
    // Геттеры / сеттеры
    // ------------------------------------------------------------------

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

    public boolean isStubEnabled() { return stubEnabled; }
    public void setStubEnabled(boolean v) { this.stubEnabled = v; }

    public String getStubProcessType() { return stubProcessType; }
    public void setStubProcessType(String v) { this.stubProcessType = v; }
}