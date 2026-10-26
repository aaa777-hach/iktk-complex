package ru.iktk.modules.forging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.core.domain.Stage;
import ru.iktk.modules.forging.model.BilletCalculation;
import ru.iktk.modules.forging.model.ForgingMethod;
import ru.iktk.modules.forging.model.ForgingTooling;
import ru.iktk.modules.forging.model.ForgingTransition;
import ru.iktk.modules.forging.service.ForgingProcessService;

import java.util.ArrayList;
import java.util.List;

/** Результат проектирования штамповочного ТП. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgingResponse {
    private String projectId;
    private String processId;
    private ForgingMethod method;
    @Builder.Default
    private List<Stage> stages = new ArrayList<>();
    private BilletCalculation billet;
    @Builder.Default
    private List<ForgingTransition> transitions = new ArrayList<>();
    @Builder.Default
    private List<ForgingProcessService.ForceCalculation> forces = new ArrayList<>();
    private ForgingTooling tooling;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
