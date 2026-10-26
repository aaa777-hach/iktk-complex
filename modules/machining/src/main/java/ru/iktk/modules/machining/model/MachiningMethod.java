package ru.iktk.modules.machining.model;

/**
 * Методы механической обработки ИКТК.
 */
public enum MachiningMethod {

    TURNING(true),
    MILLING(true),
    DRILLING(true),
    BORING(true),
    REAMING(true),
    GRINDING(true),
    EDM(true),
    LASER(true),
    CONTROL(false);

    private final boolean cnc;

    MachiningMethod(boolean cnc) {
        this.cnc = cnc;
    }

    /**
     * Признак того, что метод может выполняться на станке с ЧПУ.
     */
    public boolean isCnc() {
        return cnc;
    }
}