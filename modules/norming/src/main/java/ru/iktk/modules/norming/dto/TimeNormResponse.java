package ru.iktk.modules.norming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.modules.norming.model.StageTimeNorm;
import ru.iktk.modules.norming.model.TimeNormSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeNormResponse {
    private String projectId;
    private String processId;
    private String normId;
    private TimeNormSource source;
    @Builder.Default
    private List<StageTimeNorm> stageNorms = new ArrayList<>();
    private BigDecimal totalTpz;
    private BigDecimal totalTsh;
    private BigDecimal totalTshK;
    private BigDecimal batchSize;
}
