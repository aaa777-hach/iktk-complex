package ru.iktk.modules.norming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.norming.model.TimeNormSource;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeNormRequest {
    private String projectId;
    private TechnologyProcess process;
    private BigDecimal batchSize;
    private TimeNormSource source;
    private String enterpriseCode;
    private String industryCode;
}
