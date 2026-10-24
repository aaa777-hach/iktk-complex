package ru.iktk.modules.norming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.core.domain.Stage.OperationType;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageTimeNorm {
    private String stageId;
    private String stageDesignation;
    private String stageName;
    private OperationType operationType;
    private BigDecimal to;
    private BigDecimal tv;
    private BigDecimal top;
    private BigDecimal tob;
    private BigDecimal totl;
    private BigDecimal tsh;
    private BigDecimal tshT;
    private BigDecimal tpz;
    private BigDecimal tshK;
    private TimeNormSource source;
}
