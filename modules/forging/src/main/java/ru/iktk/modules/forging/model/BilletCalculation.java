package ru.iktk.modules.forging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BilletCalculation {
    private BigDecimal partVolumeMm3;
    private BigDecimal billetVolumeMm3;
    private BigDecimal billetMassKg;
    private BigDecimal diameterMm;
    private BigDecimal lengthMm;
    private BigDecimal materialUtilization;
    private ForgingMethod method;
}
