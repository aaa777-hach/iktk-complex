package ru.iktk.modules.norming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.core.domain.TechnologyProcess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeNorm {
    private String id;
    private String projectId;
    private String processId;
    private TechnologyProcess process;
    private TimeNormSource source;
    private BigDecimal batchSize;
    @Builder.Default
    private List<StageTimeNorm> stageNorms = new ArrayList<>();
    private BigDecimal totalTpz;
    private BigDecimal totalTsh;
    private BigDecimal totalTshK;
    private Instant createdAt;
}
