package ru.iktk.orchestrator.statemachine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectState — категории, флаги и утилиты enum")
class ProjectStateTest {

    @Test
    @DisplayName("isAutomatic: истинно только для Category.AUTOMATIC")
    void isAutomatic_matchesCategory() {
        for (ProjectState s : ProjectState.values()) {
            assertThat(s.isAutomatic())
                    .as("state=%s", s)
                    .isEqualTo(s.getCategory() == ProjectState.Category.AUTOMATIC);
        }
    }

    @Test
    @DisplayName("isPointOfControl: истинно только для Category.POINT_OF_CONTROL")
    void isPointOfControl_matchesCategory() {
        for (ProjectState s : ProjectState.values()) {
            assertThat(s.isPointOfControl())
                    .as("state=%s", s)
                    .isEqualTo(s.getCategory() == ProjectState.Category.POINT_OF_CONTROL);
        }
    }

    @Test
    @DisplayName("isTerminal: истинно только для COMPLETED")
    void isTerminal_onlyCompleted() {
        for (ProjectState s : ProjectState.values()) {
            assertThat(s.isTerminal())
                    .as("state=%s", s)
                    .isEqualTo(s == ProjectState.COMPLETED);
        }
    }

    @Test
    @DisplayName("Категории не пересекаются: каждое состояние ровно в одной категории")
    void categoriesAreMutuallyExclusive() {
        for (ProjectState s : ProjectState.values()) {
            int count = 0;
            if (s.isAutomatic()) count++;
            if (s.isPointOfControl()) count++;
            if (s.isTerminal()) count++;
            assertThat(count).as("state=%s", s).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("getCategory: известные значения")
    void getCategory_knownValues() {
        assertThat(ProjectState.PROJECT_INIT.getCategory()).isEqualTo(ProjectState.Category.AUTOMATIC);
        assertThat(ProjectState.NORMING.getCategory()).isEqualTo(ProjectState.Category.AUTOMATIC);
        assertThat(ProjectState.PART_PROCESS_PLANNING.getCategory()).isEqualTo(ProjectState.Category.POINT_OF_CONTROL);
        assertThat(ProjectState.TECHNICAL_CONTROL.getCategory()).isEqualTo(ProjectState.Category.POINT_OF_CONTROL);
        assertThat(ProjectState.COMPLETED.getCategory()).isEqualTo(ProjectState.Category.TERMINAL);
    }

    @Test
    @DisplayName("getDisplayName: не пустой и не равен name()")
    void getDisplayName_nonBlank() {
        for (ProjectState s : ProjectState.values()) {
            assertThat(s.getDisplayName()).as("state=%s", s).isNotBlank();
        }
        assertThat(ProjectState.PROJECT_INIT.getDisplayName()).isNotEqualTo("PROJECT_INIT");
        assertThat(ProjectState.COMPLETED.getDisplayName()).isEqualTo("Завершено");
    }

    @Test
    @DisplayName("pointsOfControl: ровно 5 состояний, каждое isPointOfControl()")
    void pointsOfControl_returnsOnlyPointOfControlStates() {
        Set<ProjectState> poc = ProjectState.pointsOfControl();
        assertThat(poc).hasSize(5);
        assertThat(poc).containsExactlyInAnyOrder(
                ProjectState.PART_PROCESS_PLANNING,
                ProjectState.TOOLING_DESIGN,
                ProjectState.RECOMMENDATION_REVIEW,
                ProjectState.ASSEMBLY_PROCESS_PLANNING,
                ProjectState.TECHNICAL_CONTROL);
        for (ProjectState s : poc) {
            assertThat(s.isPointOfControl()).as("state=%s", s).isTrue();
        }
    }

    @Test
    @DisplayName("automaticStates: ровно 8 состояний, каждое isAutomatic()")
    void automaticStates_returnsOnlyAutomaticStates() {
        Set<ProjectState> auto = ProjectState.automaticStates();
        assertThat(auto).hasSize(8);
        for (ProjectState s : auto) {
            assertThat(s.isAutomatic()).as("state=%s", s).isTrue();
        }
    }

    @Test
    @DisplayName("pointsOfControl + automaticStates + COMPLETED = все значения enum")
    void knownSets_coverAllValues() {
        Set<ProjectState> union = new java.util.HashSet<>();
        union.addAll(ProjectState.pointsOfControl());
        union.addAll(ProjectState.automaticStates());
        union.add(ProjectState.COMPLETED);
        assertThat(union).containsExactlyInAnyOrder(ProjectState.values());
    }

    @Test
    @DisplayName("initial() = PROJECT_INIT, terminal() = COMPLETED")
    void initialAndTerminal() {
        assertThat(ProjectState.initial()).isEqualTo(ProjectState.PROJECT_INIT);
        assertThat(ProjectState.terminal()).isEqualTo(ProjectState.COMPLETED);
        assertThat(ProjectState.initial().isAutomatic()).isTrue();
        assertThat(ProjectState.terminal().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("toString: формат 'NAME (displayName)'")
    void toString_format() {
        ProjectState s = ProjectState.PROJECT_INIT;
        assertThat(s.toString())
                .isEqualTo("PROJECT_INIT (" + s.getDisplayName() + ")");
        assertThat(ProjectState.COMPLETED.toString()).isEqualTo("COMPLETED (Завершено)");
    }

    @Test
    @DisplayName("Category: ровно три значения — AUTOMATIC, POINT_OF_CONTROL, TERMINAL")
    void category_values() {
        assertThat(ProjectState.Category.values())
                .containsExactly(
                        ProjectState.Category.AUTOMATIC,
                        ProjectState.Category.POINT_OF_CONTROL,
                        ProjectState.Category.TERMINAL);
    }
}