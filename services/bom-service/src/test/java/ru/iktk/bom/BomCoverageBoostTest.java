package ru.iktk.bom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.bom.dto.AssemblyConnectionDto;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.dto.BomTree;
import ru.iktk.bom.dto.BomUpdateRequest;
import ru.iktk.bom.dto.SpecificationDto;
import ru.iktk.bom.exception.BomException;
import ru.iktk.bom.mapper.BomMapper;
import ru.iktk.bom.model.AssemblyConnection;
import ru.iktk.bom.model.AssemblyUnit;
import ru.iktk.bom.model.BomItem;
import ru.iktk.bom.service.BomExtractionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Покрытие Builders, DTO, Raw*, BomMapper")
class BomCoverageBoostTest {

    private BomMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BomMapper(new ObjectMapper());
    }

    // ===== Builders моделей =====

    @Test
    @DisplayName("AssemblyUnit.Builder: все поля")
    void assemblyUnit_builder() {
        AssemblyUnit u = AssemblyUnit.builder()
                .id("u1").projectId("proj1").parentId("p0")
                .designation("СБ-001").name("Корпус").description("desc")
                .geometryId("geo-1").cadSystem("SIEMENS")
                .itemCount(5).maxDepth(2)
                .totalMassKg(new BigDecimal("12.5"))
                .version(3).status("ACTIVE").updatedBy("ivanov")
                .parameters(Map.of("k", "v"))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        assertThat(u.getId()).isEqualTo("u1");
        assertThat(u.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("BomItem.Builder: все поля")
    void bomItem_builder() {
        BomItem i = BomItem.builder()
                .id("i1").assemblyId("u1").parentId("p0")
                .order(1).level(2).position("01")
                .designation("Д-001").name("Вал")
                .quantity(3).unit("шт").material("Сталь")
                .type("PART").geometryId("geo-1").cadSystem("KOMPAS")
                .massKg(new BigDecimal("1.5")).note("note")
                .parameters(Map.of("k", "v"))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        assertThat(i.getId()).isEqualTo("i1");
        assertThat(i.getType()).isEqualTo("PART");
    }

    @Test
    @DisplayName("AssemblyConnection.Builder: все поля")
    void assemblyConnection_builder() {
        AssemblyConnection c = AssemblyConnection.builder()
                .id("c1").assemblyId("u1")
                .type("BOLT").name("Болтовое").description("desc")
                .part1Id("i1").part2Id("i2")
                .part1Designation("Д-001").part2Designation("Д-002")
                .order(1).needsTool(true).needsFixture(true).needsControl(false)
                .toolType("KEY").fixtureType("JIG")
                .diameterMm(new BigDecimal("10.0"))
                .clearanceMm(new BigDecimal("0.1"))
                .interferenceMm(new BigDecimal("0.05"))
                .torqueNm(new BigDecimal("25.0"))
                .pressForceKn(new BigDecimal("5.0"))
                .weldLengthMm(new BigDecimal("50.0"))
                .weldLegMm(new BigDecimal("3.0"))
                .parameters(Map.of("k", "v"))
                .note("note")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.isNeedsTool()).isTrue();
    }

    // ===== Builders DTO =====

    @Test
    @DisplayName("AssemblyConnectionDto.Builder + setter'ы")
    void assemblyConnectionDto_builder() {
        AssemblyConnectionDto dto = AssemblyConnectionDto.builder()
                .id("c1").type("BOLT").part1Id("i1").part2Id("i2")
                .parameters(Map.of("k", "v"))
                .build();
        assertThat(dto.getId()).isEqualTo("c1");
        assertThat(dto.getType()).isEqualTo("BOLT");

        AssemblyConnectionDto dto2 = new AssemblyConnectionDto();
        dto2.setId("c2");
        dto2.setType("WELD");
        dto2.setPart1Id("i3");
        dto2.setPart2Id("i4");
        dto2.setParameters(Map.of("k2", "v2"));
        assertThat(dto2.getId()).isEqualTo("c2");
    }

    @Test
    @DisplayName("BomItemDto.Builder: все поля + setter'ы")
    void bomItemDto_builder() {
        BomItemDto dto = BomItemDto.builder()
                .id("i1").parentId("p0").position("01")
                .designation("Д-001").name("Вал")
                .quantity(3).unit("шт").material("Сталь")
                .type("PART").order(1).level(2)
                .build();
        assertThat(dto.getId()).isEqualTo("i1");
        assertThat(dto.getOrder()).isEqualTo(1);

        BomItemDto dto2 = new BomItemDto();
        dto2.setId("i2");
        dto2.setParentId("p1");
        dto2.setPosition("02");
        dto2.setDesignation("Д-002");
        dto2.setName("Ось");
        dto2.setQuantity(2);
        dto2.setUnit("шт");
        dto2.setMaterial("Сталь 45");
        dto2.setType("PART");
        dto2.setOrder(2);
        dto2.setLevel(1);
        dto2.setChildren(List.of());
        assertThat(dto2.getId()).isEqualTo("i2");
        assertThat(dto2.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("BomTree.Builder: все поля")
    void bomTree_builder() {
        BomTree tree = BomTree.builder()
                .id("u1").projectId("proj1").rootAssemblyId("u1")
                .designation("СБ-001").name("Корпус")
                .items(List.of()).totalPositions(0).maxDepth(0)
                .build();
        assertThat(tree.getId()).isEqualTo("u1");

        BomTree tree2 = new BomTree();
        tree2.setId("u2");
        tree2.setProjectId("proj2");
        tree2.setRootAssemblyId("u2");
        tree2.setDesignation("СБ-002");
        tree2.setName("Опора");
        tree2.setItems(List.of());
        tree2.setTotalPositions(0);
        tree2.setMaxDepth(0);
        assertThat(tree2.getId()).isEqualTo("u2");
    }

    @Test
    @DisplayName("BomUpdateRequest: конструктор + setter'ы")
    void bomUpdateRequest() {
        BomUpdateRequest r = new BomUpdateRequest();
        r.setItems(List.of());
        r.setComment("note");
        assertThat(r.getItems()).isEmpty();
        assertThat(r.getComment()).isEqualTo("note");

        BomUpdateRequest r2 = new BomUpdateRequest(List.of());
        assertThat(r2.getItems()).isEmpty();
    }

    @Test
    @DisplayName("SpecificationDto: конструктор + Row")
    void specificationDto() {
        SpecificationDto spec = new SpecificationDto("СБ-001", "Корпус");
        spec.setTotalPositions(2);
        spec.setRows(List.of(
                new SpecificationDto.Row("PART", "01", "Д-001", "Вал", 1, "шт", "note"),
                new SpecificationDto.Row()
        ));
        assertThat(spec.getRows()).hasSize(2);

        SpecificationDto.Row row = new SpecificationDto.Row();
        row.setSection("PART");
        row.setPosition("02");
        row.setDesignation("Д-002");
        row.setName("Ось");
        row.setQuantity(2);
        row.setUnit("шт");
        row.setNote("n");
        assertThat(row.getDesignation()).isEqualTo("Д-002");
    }

    // ===== BomExtractionService.Raw* =====

    @Test
    @DisplayName("RawBom: конструктор + геттеры")
    void rawBom() {
        BomExtractionService.RawItem item = new BomExtractionService.RawItem();
        item.setId("i1");
        item.setParentId("p0");
        item.setPosition("01");
        item.setDesignation("Д-001");
        item.setName("Вал");
        item.setQuantity(1);
        item.setUnit("шт");
        item.setMaterial("Сталь");
        item.setType("PART");
        item.setHasChildren(false);

        assertThat(item.getId()).isEqualTo("i1");
        assertThat(item.getParentId()).isEqualTo("p0");
        assertThat(item.getPosition()).isEqualTo("01");
        assertThat(item.getDesignation()).isEqualTo("Д-001");
        assertThat(item.getName()).isEqualTo("Вал");
        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(item.getUnit()).isEqualTo("шт");
        assertThat(item.getMaterial()).isEqualTo("Сталь");
        assertThat(item.getType()).isEqualTo("PART");
        assertThat(item.hasChildren()).isFalse();

        BomExtractionService.RawBom bom = new BomExtractionService.RawBom(
                "root", "СБ-001", "Корпус", List.of(item));
        assertThat(bom.getRootId()).isEqualTo("root");
        assertThat(bom.getRootDesignation()).isEqualTo("СБ-001");
        assertThat(bom.getRootName()).isEqualTo("Корпус");
        assertThat(bom.getItems()).hasSize(1);

        BomExtractionService.RawBom empty = new BomExtractionService.RawBom("r", "d", "n", null);
        assertThat(empty.getItems()).isEmpty();
    }

    @Test
    @DisplayName("RawConnection: конструктор + геттеры")
    void rawConnection() {
        BomExtractionService.RawConnection c = new BomExtractionService.RawConnection();
        c.setId("c1");
        c.setType("BOLT");
        c.setPart1Id("i1");
        c.setPart2Id("i2");
        c.setParameters(Map.of("k", "v"));

        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.getType()).isEqualTo("BOLT");
        assertThat(c.getPart1Id()).isEqualTo("i1");
        assertThat(c.getPart2Id()).isEqualTo("i2");
        assertThat(c.getParameters()).containsEntry("k", "v");
    }

    // ===== BomMapper =====

    @Test
    @DisplayName("BomMapper.toItemDto: null + полный")
    void mapper_toItemDto() {
        assertThat(mapper.toItemDto(null)).isNull();

        BomItem item = BomItem.builder()
                .id("i1").parentId("p0").position("01")
                .designation("Д-001").name("Вал")
                .quantity(3).unit("шт").material("Сталь")
                .type("PART").order(1).level(2)
                .build();
        BomItemDto dto = mapper.toItemDto(item);
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("i1");
        assertThat(dto.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("BomMapper.toItemEntity: null + полный")
    void mapper_toItemEntity() {
        assertThat(mapper.toItemEntity(null)).isNull();

        BomItemDto dto = BomItemDto.builder()
                .id("i1").parentId("p0").position("01")
                .designation("Д-001").name("Вал")
                .quantity(3).unit("шт").material("Сталь")
                .type("PART").order(1).level(2)
                .build();
        BomItem item = mapper.toItemEntity(dto);
        assertThat(item).isNotNull();
        assertThat(item.getId()).isEqualTo("i1");
        assertThat(item.getDesignation()).isEqualTo("Д-001");
    }

    @Test
    @DisplayName("BomMapper.toConnectionEntity / toConnectionDto")
    void mapper_connection() {
        assertThat(mapper.toConnectionEntity(null)).isNull();
        assertThat(mapper.toConnectionDto(null)).isNull();

        AssemblyConnectionDto dto = AssemblyConnectionDto.builder()
                .id("c1").type("BOLT").part1Id("i1").part2Id("i2")
                .parameters(Map.of("k", "v"))
                .build();
        AssemblyConnection entity = mapper.toConnectionEntity(dto);
        assertThat(entity.getId()).isEqualTo("c1");

        AssemblyConnectionDto back = mapper.toConnectionDto(entity);
        assertThat(back.getId()).isEqualTo("c1");
    }

    @Test
    @DisplayName("BomMapper.toJsonNode: с items/connections и без")
    void mapper_toJsonNode() {
        AssemblyUnit root = AssemblyUnit.builder()
                .id("u1").projectId("proj1").designation("СБ-001")
                .name("Корпус").version(1).status("DRAFT").build();

        BomItem item = BomItem.builder().id("i1").parentId(null)
                .designation("Д-001").name("Вал").quantity(1).type("PART").build();
        AssemblyConnection conn = AssemblyConnection.builder()
                .id("c1").type("BOLT").part1Id("i1").part2Id("i2").build();

        JsonNode node = mapper.toJsonNode(root, List.of(item), List.of(conn));
        assertThat(node.get("id").asText()).isEqualTo("u1");
        assertThat(node.get("items").size()).isEqualTo(1);
        assertThat(node.get("connections").size()).isEqualTo(1);

        JsonNode empty = mapper.toJsonNode(null, null, null);
        assertThat(empty.get("items").size()).isZero();
        assertThat(empty.get("connections").size()).isZero();
    }

    @Test
    @DisplayName("BomMapper.toSpecification: null + список")
    void mapper_toSpecification() {
        assertThat(mapper.toSpecification(null)).isNotNull();

        BomItem item = BomItem.builder().id("i1").position("01")
                .designation("Д-001").name("Вал").quantity(1).unit("шт")
                .type("PART").note("n").build();
        SpecificationDto spec = mapper.toSpecification(List.of(item));
        assertThat(spec.getTotalPositions()).isEqualTo(1);
        assertThat(spec.getRows()).hasSize(1);
        assertThat(spec.getRows().get(0).getDesignation()).isEqualTo("Д-001");
    }

    // ===== Enum'ы и Exception =====

    @Test
    @DisplayName("AssemblyUnit.AssemblyStatus: значения")
    void assemblyStatus_values() {
        AssemblyUnit.AssemblyStatus[] values = AssemblyUnit.AssemblyStatus.values();
        assertThat(values).isNotEmpty();
    }

    @Test
    @DisplayName("BomException.Code: все значения + фабричные методы + getHttpStatus")
    void bomException_allCodes() {
        assertThat(BomException.Code.values()).hasSize(5);
        assertThat(BomException.validation("v").getHttpStatus()).isNotNull();
        assertThat(BomException.notFound("n").getHttpStatus()).isNotNull();
        assertThat(BomException.emptyStructure("p1").getHttpStatus()).isNotNull();
        assertThat(BomException.extractionFailed("p1", "cad", null).getHttpStatus()).isNotNull();
        assertThat(BomException.internal("i", null).getHttpStatus()).isNotNull();
        assertThat(BomException.validation("v").getContext()).containsKey("code");
    }
}