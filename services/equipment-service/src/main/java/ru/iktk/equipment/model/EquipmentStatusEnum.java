package ru.iktk.equipment.model;

/**
 * Статус единицы оборудования ИКТК.
 *
 * <p>В БД хранится как {@code VARCHAR(32)} (колонка {@code equipment.status}).
 * Маппинг: {@code Equipment.status} (String) ← {@code EquipmentStatusEnum.name()}.
 *
 * <p>Значения:
 * <ul>
 *   <li>{@link #AVAILABLE} — доступно для использования (единственное, при котором
 *       {@code isMethodAvailable} возвращает {@code true});</li>
 *   <li>{@link #BUSY} — занято (выполняется операция);</li>
 *   <li>{@link #MAINTENANCE} — на обслуживании / ремонте;</li>
 *   <li>{@link #DECOMMISSIONED} — списано (выведено из эксплуатации).</li>
 * </ul>
 */
public enum EquipmentStatusEnum {
    AVAILABLE,
    BUSY,
    MAINTENANCE,
    DECOMMISSIONED
}