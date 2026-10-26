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
public class ForgingTransition {
    private int order;
    private String name;
    private TransitionType type;
    private BigDecimal inputVolumeMm3;
    private BigDecimal temperatureC;
    private BigDecimal deformationDegree;

    public enum TransitionType {
        UPSETTING,  // осадка
        DRAWING,    // протяжка
        PIERCING,   // прошивка
        BLOCKING,   // черновой ручей
        FINISHING,  // чистовой ручей
        TRIMMING    // обрезка
    }
}
