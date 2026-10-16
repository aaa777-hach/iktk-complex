package ru.iktk.orchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.util.EnumSet;

/**
 * Конфигурация конечного автомата оркестратора ИКТК.
 *
 * <p>Определяет:
 * <ul>
 *   <li>все состояния проекта в соответствии с {@code final_TZ.md} (ред. 3);</li>
 *   <li>допустимые переходы между состояниями;</li>
 *   <li>действия на вход/выход из состояний и при переходах;</li>
 *   <li>слушателя для журналирования событий автомата.</li>
 * </ul>
 *
 * <p>Поддерживаемые состояния (см. ТЗ, п. 4.1):
 * <pre>
 *   ProjectInit → GeometryInput → GeometryAnalysis
 *   → (AssemblyAnalysis)? → PartProcessPlanning → ToolingDesign
 *   → EquipmentMatching → (EquipmentGapResolution → RecommendationReview)?
 *   → AssemblyProcessPlanning → Norming → Documentation
 *   → TechnicalControl → Completed
 * </pre>
 *
 * <p>Режим работы — смешанный: часть переходов выполняется автоматически,
 * часть — по внешнему событию (точка контроля).
 */
@Configuration
@EnableStateMachineFactory
public class StateMachineConfig extends StateMachineConfigurerAdapter<ProjectState, ProjectEvent> {

    private static final Logger log = LoggerFactory.getLogger(StateMachineConfig.class);

    // ------------------------------------------------------------------
    // Общая конфигурация автомата
    // ------------------------------------------------------------------

    @Override
    public void configure(StateMachineConfigurationConfigurer<ProjectState, ProjectEvent> config)
            throws Exception {
        config
                .withConfiguration()
                .autoStartup(true)
                .listener(stateMachineListener());
    }

    // ------------------------------------------------------------------
    // Состояния
    // ------------------------------------------------------------------

    @Override
    public void configure(StateMachineStateConfigurer<ProjectState, ProjectEvent> states)
            throws Exception {
        states
                .withStates()
                .initial(ProjectState.PROJECT_INIT)
                .end(ProjectState.COMPLETED)
                .states(EnumSet.allOf(ProjectState.class));
    }

    // ------------------------------------------------------------------
    // Переходы
    // ------------------------------------------------------------------

    @Override
    public void configure(StateMachineTransitionConfigurer<ProjectState, ProjectEvent> transitions)
            throws Exception {
        transitions
                // 1. Инициализация → ввод геометрии
                .withExternal()
                    .source(ProjectState.PROJECT_INIT)
                    .target(ProjectState.GEOMETRY_INPUT)
                    .event(ProjectEvent.START_GEOMETRY_INPUT)
                .and()
                // 2. Ввод геометрии → анализ геометрии
                .withExternal()
                    .source(ProjectState.GEOMETRY_INPUT)
                    .target(ProjectState.GEOMETRY_ANALYSIS)
                    .event(ProjectEvent.GEOMETRY_UPLOADED)
                .and()
                // 3. Анализ геометрии: деталь → проектирование ТП детали
                .withExternal()
                    .source(ProjectState.GEOMETRY_ANALYSIS)
                    .target(ProjectState.PART_PROCESS_PLANNING)
                    .event(ProjectEvent.IS_PART)
                .and()
                // 4. Анализ геометрии: сборка → анализ сборочной единицы
                .withExternal()
                    .source(ProjectState.GEOMETRY_ANALYSIS)
                    .target(ProjectState.ASSEMBLY_ANALYSIS)
                    .event(ProjectEvent.IS_ASSEMBLY)
                .and()
                // 5. Анализ сборки → проектирование ТП деталей
                .withExternal()
                    .source(ProjectState.ASSEMBLY_ANALYSIS)
                    .target(ProjectState.PART_PROCESS_PLANNING)
                    .event(ProjectEvent.BOM_EXTRACTED)
                .and()
                // 6. Проектирование ТП детали → проектирование оснастки
                .withExternal()
                    .source(ProjectState.PART_PROCESS_PLANNING)
                    .target(ProjectState.TOOLING_DESIGN)
                    .event(ProjectEvent.PART_PROCESS_READY)
                .and()
                // 7. Оснастка → сопоставление с оборудованием
                .withExternal()
                    .source(ProjectState.TOOLING_DESIGN)
                    .target(ProjectState.EQUIPMENT_MATCHING)
                    .event(ProjectEvent.TOOLING_READY)
                .and()
                // 8a. Оборудования достаточно → проектирование ТП сборки
                .withExternal()
                    .source(ProjectState.EQUIPMENT_MATCHING)
                    .target(ProjectState.ASSEMBLY_PROCESS_PLANNING)
                    .event(ProjectEvent.EQUIPMENT_MATCHED)
                .and()
                // 8b. Дефицит оборудования → разрешение дефицита
                .withExternal()
                    .source(ProjectState.EQUIPMENT_MATCHING)
                    .target(ProjectState.EQUIPMENT_GAP_RESOLUTION)
                    .event(ProjectEvent.EQUIPMENT_GAP)
                .and()
                // 9. Разрешение дефицита → просмотр рекомендаций (точка контроля)
                .withExternal()
                    .source(ProjectState.EQUIPMENT_GAP_RESOLUTION)
                    .target(ProjectState.RECOMMENDATION_REVIEW)
                    .event(ProjectEvent.RECOMMENDATIONS_READY)
                .and()
                // 10a. Рекомендация принята → проектирование ТП сборки
                .withExternal()
                    .source(ProjectState.RECOMMENDATION_REVIEW)
                    .target(ProjectState.ASSEMBLY_PROCESS_PLANNING)
                    .event(ProjectEvent.RECOMMENDATION_ACCEPTED)
                .and()
                // 10b. ТП скорректирован → повторное сопоставление с оборудованием
                .withExternal()
                    .source(ProjectState.RECOMMENDATION_REVIEW)
                    .target(ProjectState.EQUIPMENT_MATCHING)
                    .event(ProjectEvent.PROCESS_ADJUSTED)
                .and()
                // 10c. Аутсорс выбран → проектирование ТП сборки
                .withExternal()
                    .source(ProjectState.RECOMMENDATION_REVIEW)
                    .target(ProjectState.ASSEMBLY_PROCESS_PLANNING)
                    .event(ProjectEvent.OUTSOURCING_SELECTED)
                .and()
                // 11. ТП сборки → нормирование
                .withExternal()
                    .source(ProjectState.ASSEMBLY_PROCESS_PLANNING)
                    .target(ProjectState.NORMING)
                    .event(ProjectEvent.ASSEMBLY_PROCESS_READY)
                .and()
                // 12. Нормирование → формирование документации
                .withExternal()
                    .source(ProjectState.NORMING)
                    .target(ProjectState.DOCUMENTATION)
                    .event(ProjectEvent.NORMS_READY)
                .and()
                // 13. Документация → технический контроль
                .withExternal()
                    .source(ProjectState.DOCUMENTATION)
                    .target(ProjectState.TECHNICAL_CONTROL)
                    .event(ProjectEvent.DOCUMENTATION_READY)
                .and()
                // 14. Технический контроль → завершение
                .withExternal()
                    .source(ProjectState.TECHNICAL_CONTROL)
                    .target(ProjectState.COMPLETED)
                    .event(ProjectEvent.CONTROL_PASSED)
                .and()
                // 15. Технический контроль → возврат на доработку
                .withExternal()
                    .source(ProjectState.TECHNICAL_CONTROL)
                    .target(ProjectState.PART_PROCESS_PLANNING)
                    .event(ProjectEvent.CONTROL_REJECTED);
    }

    // ------------------------------------------------------------------
    // Слушатель для журналирования
    // ------------------------------------------------------------------

    @Bean
    public StateMachineListener<ProjectState, ProjectEvent> stateMachineListener() {
        return new StateMachineListenerAdapter<>() {

            @Override
            public void stateChanged(State<ProjectState, ProjectEvent> from,
                                     State<ProjectState, ProjectEvent> to) {
                String fromId = from == null ? "—" : from.getId().name();
                String toId = to == null ? "—" : to.getId().name();
                log.info("Оркестратор: переход состояния {} → {}", fromId, toId);
            }

            @Override
            public void eventNotAccepted(org.springframework.messaging.Message<ProjectEvent> event) {
                log.warn("Оркестратор: событие не принято: {}",
                        event.getPayload() == null ? "null" : event.getPayload().name());
            }

            @Override
            public void transition(Transition<ProjectState, ProjectEvent> transition) {
                if (transition.getSource() != null && transition.getTarget() != null) {
                    log.debug("Оркестратор: переход {} --[{}]--> {}",
                            transition.getSource().getId(),
                            transition.getTrigger() == null
                                    ? "auto"
                                    : transition.getTrigger().getEvent(),
                            transition.getTarget().getId());
                }
            }
        };
    }
}