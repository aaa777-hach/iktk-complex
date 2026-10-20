package ru.iktk.core.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.core.domain.Project;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestrationRequest — DTO запроса")
class OrchestrationRequestTest {

    @Test
    @DisplayName("Конструктор на 4 аргумента заполняет ключевые поля")
    void constructor4_setsFields() {
        OrchestrationRequest r = new OrchestrationRequest("Корпус", "КР-001",
                Project.ObjectType.ASSEMBLY, "ivanov");
        assertThat(r.getProjectName()).isEqualTo("Корпус");
        assertThat(r.getDesignation()).isEqualTo("КР-001");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(r.getRequestedBy()).isEqualTo("ivanov");
        assertThat(r.getDescription()).isNull();
    }

    @Test
    @DisplayName("Пустой конструктор — все поля null")
    void defaultConstructor_allNull() {
        OrchestrationRequest r = new OrchestrationRequest();
        assertThat(r.getProjectName()).isNull();
        assertThat(r.getObjectType()).isNull();
        assertThat(r.getRequestedBy()).isNull();
        assertThat(r.getPayload()).isNull();
    }

    @Test
    @DisplayName("Builder заполняет все 11 полей")
    void builder_setsAllFields() {
        List<String> planned = List.of("CASTING", "MACHINING");
        Map<String, Object> payload = Map.of("k", "v");

        OrchestrationRequest r = OrchestrationRequest.builder()
                .projectName("Корпус")
                .designation("КР-001")
                .description("desc")
                .objectType(Project.ObjectType.ASSEMBLY)
                .enterpriseCode("PLANT-01")
                .requestedBy("ivanov")
                .responsibleEngineer("petrov")
                .geometryId("geo-1")
                .assemblyId("asm-1")
                .plannedProcessTypes(planned)
                .payload(payload)
                .build();

        assertThat(r.getProjectName()).isEqualTo("Корпус");
        assertThat(r.getDesignation()).isEqualTo("КР-001");
        assertThat(r.getDescription()).isEqualTo("desc");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(r.getEnterpriseCode()).isEqualTo("PLANT-01");
        assertThat(r.getRequestedBy()).isEqualTo("ivanov");
        assertThat(r.getResponsibleEngineer()).isEqualTo("petrov");
        assertThat(r.getGeometryId()).isEqualTo("geo-1");
        assertThat(r.getAssemblyId()).isEqualTo("asm-1");
        assertThat(r.getPlannedProcessTypes()).isEqualTo(planned);
        assertThat(r.getPayload()).isEqualTo(payload);
    }

    @Test
    @DisplayName("isAssembly/isPart по objectType")
    void isAssemblyAndIsPart() {
        OrchestrationRequest asm = OrchestrationRequest.builder()
                .objectType(Project.ObjectType.ASSEMBLY).build();
        assertThat(asm.isAssembly()).isTrue();
        assertThat(asm.isPart()).isFalse();

        OrchestrationRequest part = OrchestrationRequest.builder()
                .objectType(Project.ObjectType.PART).build();
        assertThat(part.isPart()).isTrue();
        assertThat(part.isAssembly()).isFalse();

        OrchestrationRequest nullType = new OrchestrationRequest();
        assertThat(nullType.isAssembly()).isFalse();
        assertThat(nullType.isPart()).isFalse();
    }

    @Test
    @DisplayName("hasGeometry: null/blank -> false, значение -> true")
    void hasGeometry() {
        assertThat(new OrchestrationRequest().hasGeometry()).isFalse();
        assertThat(OrchestrationRequest.builder().geometryId("   ").build().hasGeometry()).isFalse();
        assertThat(OrchestrationRequest.builder().geometryId("geo").build().hasGeometry()).isTrue();
    }

    @Test
    @DisplayName("hasAssembly: null/blank -> false, значение -> true")
    void hasAssembly() {
        assertThat(new OrchestrationRequest().hasAssembly()).isFalse();
        assertThat(OrchestrationRequest.builder().assemblyId("").build().hasAssembly()).isFalse();
        assertThat(OrchestrationRequest.builder().assemblyId("asm").build().hasAssembly()).isTrue();
    }

    @Test
    @DisplayName("equals/hashCode только по projectName, designation, objectType, requestedBy")
    void equals_hashCode_byKeyFields() {
        OrchestrationRequest a = OrchestrationRequest.builder()
                .projectName("P").designation("D")
                .objectType(Project.ObjectType.PART).requestedBy("u")
                .description("a").build();
        OrchestrationRequest b = OrchestrationRequest.builder()
                .projectName("P").designation("D")
                .objectType(Project.ObjectType.PART).requestedBy("u")
                .description("b").geometryId("geo").build();

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("equals: разница в projectName -> не равны")
    void equals_diffProjectName() {
        OrchestrationRequest a = OrchestrationRequest.builder()
                .projectName("A").designation("D")
                .objectType(Project.ObjectType.PART).requestedBy("u").build();
        OrchestrationRequest b = OrchestrationRequest.builder()
                .projectName("B").designation("D")
                .objectType(Project.ObjectType.PART).requestedBy("u").build();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals: разница в objectType -> не равны")
    void equals_diffObjectType() {
        OrchestrationRequest a = OrchestrationRequest.builder()
                .projectName("P").designation("D")
                .objectType(Project.ObjectType.PART).requestedBy("u").build();
        OrchestrationRequest b = OrchestrationRequest.builder()
                .projectName("P").designation("D")
                .objectType(Project.ObjectType.ASSEMBLY).requestedBy("u").build();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals: сравнение с null и с самим собой")
    void equals_nullAndSelf() {
        OrchestrationRequest a = OrchestrationRequest.builder().projectName("P").build();
        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    @DisplayName("toString содержит ключевые поля")
    void toString_containsFields() {
        OrchestrationRequest r = OrchestrationRequest.builder()
                .projectName("Корпус").designation("КР-001")
                .objectType(Project.ObjectType.ASSEMBLY).requestedBy("ivanov")
                .geometryId("geo-1").assemblyId("asm-1").build();
        assertThat(r.toString()).contains("Корпус", "КР-001", "ASSEMBLY", "ivanov", "geo-1", "asm-1");
    }

    @Test
    @DisplayName("Сеттеры меняют значения")
    void setters_work() {
        OrchestrationRequest r = new OrchestrationRequest();
        r.setProjectName("P");
        r.setDesignation("D");
        r.setObjectType(Project.ObjectType.ASSEMBLY);
        r.setRequestedBy("u");
        r.setGeometryId("geo");

        assertThat(r.getProjectName()).isEqualTo("P");
        assertThat(r.getDesignation()).isEqualTo("D");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(r.getRequestedBy()).isEqualTo("u");
        assertThat(r.getGeometryId()).isEqualTo("geo");
    }
}