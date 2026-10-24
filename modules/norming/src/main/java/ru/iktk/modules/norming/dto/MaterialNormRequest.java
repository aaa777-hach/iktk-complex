package ru.iktk.modules.norming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.core.domain.TechnologyProcess;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialNormRequest {
    private String projectId;
    private TechnologyProcess process;
    private BigDecimal batchSize;
    private String enterpriseCode;
    private String materialCode;
    private BigDecimal blankVolumeMm3;
    private BigDecimal partVolumeMm3;
}
