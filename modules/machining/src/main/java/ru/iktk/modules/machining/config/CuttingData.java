package ru.iktk.modules.machining.config;

import java.math.BigDecimal;

/**
 * Справочные данные режимов резания для пары (материал заготовки, метод обработки).
 *
 * @param cuttingSpeedMPerMin скорость резания V, м/мин
 * @param feedMmPerRev        подача S, мм/об
 * @param depthOfCutMm        глубина резания t, мм
 * @param toolLifeMin         стойкость инструмента T, мин
 */
public record CuttingData(
        BigDecimal cuttingSpeedMPerMin,
        BigDecimal feedMmPerRev,
        BigDecimal depthOfCutMm,
        BigDecimal toolLifeMin) {
}