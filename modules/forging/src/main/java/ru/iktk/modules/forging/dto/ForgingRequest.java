package ru.iktk.modules.forging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.forging.model.ForgingMethod;

/** Запрос на проектирование штамповочного ТП. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgingRequest {
    private String projectId;
    private String objectRef;
    private TechnologyProcess.ProductionType productionType;
    private ForgingMethod preferredMethod;
}
