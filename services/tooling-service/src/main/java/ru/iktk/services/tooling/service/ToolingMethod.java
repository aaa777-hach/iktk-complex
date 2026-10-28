package ru.iktk.services.tooling.service;

/**
 * Метод обработки, для которого подбирается режущий инструмент.
 *
 * <p>Значения совпадают с {@code ru.iktk.modules.machining.model.MachiningMethod}
 * (machining использует их при вызове tooling-service).
 */
public enum ToolingMethod {
    MILLING,
    BORING,
    DRILLING,
    REAMING,
    TURNING,
    GRINDING,
    EDM,
    LASER,
    CONTROL
}