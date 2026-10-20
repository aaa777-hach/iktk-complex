package ru.iktk.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Project — домен проекта")
class ProjectTest {

    @Test
    @DisplayName("Конструктор ставит DRAFT и timestamps")
    void constructor_setsDefaults() {
        Project p = new Project("p1", "КР-001", "Корпус", Project.ObjectType.ASSEMBLY);
        assertThat(p.getId()).isEqualTo("p1");
        assertThat(p.getDesignation()).isEqualTo("КР-001");
        assertThat(p.getName()).isEqualTo("Корпус");
        assertThat(p.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(p.getStatus()).isEqualTo(Project.ProjectStatus.DRAFT);
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("activate/complete/archive/reject меняют статус")
    void statusTransitions() {
        Project p = new Project("p1", "КР-001", "Корпус", Project.ObjectType.PART);
        p.activate();
        assertThat(p.getStatus()).isEqualTo(Project.ProjectStatus.ACTIVE);
        p.complete();
        assertThat(p.getStatus()).isEqualTo(Project.ProjectStatus.COMPLETED);
        p.archive();
        assertThat(p.getStatus()).isEqualTo(Project.ProjectStatus.ARCHIVED);
        p.reject();
        assertThat(p.getStatus()).isEqualTo(Project.ProjectStatus.REJECTED);
    }

    @Test
    @DisplayName("isAssembly/isPart по objectType")
    void isAssemblyAndIsPart() {
        Project assembly = new Project("p1", "D", "N", Project.ObjectType.ASSEMBLY);
        assertThat(assembly.isAssembly()).isTrue();
        assertThat(assembly.isPart()).isFalse();

        Project part = new Project("p2", "D", "N", Project.ObjectType.PART);
        assertThat(part.isPart()).isTrue();
        assertThat(part.isAssembly()).isFalse();
    }

    @Test
    @DisplayName("touch при сеттерах обновляет updatedAt")
    void settersTouchUpdatedAt() throws InterruptedException {
        Project p = new Project("p1", "D", "N", Project.ObjectType.PART);
        Instant before = p.getUpdatedAt();
        Thread.sleep(5);
        p.setName("Новое имя");
        assertThat(p.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("setId не трогает updatedAt")
    void setIdDoesNotTouch() throws InterruptedException {
        Project p = new Project("p1", "D", "N", Project.ObjectType.PART);
        Instant before = p.getUpdatedAt();
        Thread.sleep(5);
        p.setId("p2");
        assertThat(p.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("equals/hashCode по id")
    void equalityById() {
        Project a = new Project("p1", "D1", "N1", Project.ObjectType.PART);
        Project b = new Project("p1", "D2", "N2", Project.ObjectType.ASSEMBLY);
        Project c = new Project("p2", "D1", "N1", Project.ObjectType.PART);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString содержит id, designation, name, objectType, status")
    void toString_containsFields() {
        Project p = new Project("p1", "КР-001", "Корпус", Project.ObjectType.ASSEMBLY);
        assertThat(p.toString())
                .contains("p1", "КР-001", "Корпус", "ASSEMBLY", "DRAFT");
    }

    @Test
    @DisplayName("ObjectType: ровно PART и ASSEMBLY")
    void objectTypeValues() {
        assertThat(Project.ObjectType.values()).containsExactly(
                Project.ObjectType.PART, Project.ObjectType.ASSEMBLY);
    }

    @Test
    @DisplayName("ProjectStatus: 5 значений")
    void projectStatusValues() {
        assertThat(Project.ProjectStatus.values()).containsExactly(
                Project.ProjectStatus.DRAFT,
                Project.ProjectStatus.ACTIVE,
                Project.ProjectStatus.COMPLETED,
                Project.ProjectStatus.REJECTED,
                Project.ProjectStatus.ARCHIVED);
    }
}