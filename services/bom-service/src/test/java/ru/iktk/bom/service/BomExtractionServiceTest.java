package ru.iktk.bom.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.bom.client.IntegrationClient;
import ru.iktk.bom.config.BomProperties;
import ru.iktk.bom.dto.AssemblyConnectionDto;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.exception.BomException;
import ru.iktk.bom.service.BomExtractionService.RawBom;
import ru.iktk.bom.service.BomExtractionService.RawConnection;
import ru.iktk.bom.service.BomExtractionService.RawItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BomExtractionService — извлечение BOM из CAD")
class BomExtractionServiceTest {

    @Mock private IntegrationClient integrationClient;
    @Mock private BomProperties properties;

    private BomExtractionService service;

    @BeforeEach
    void setUp() {
        service = new BomExtractionService(integrationClient, properties);
        when(properties.getMaxDepth()).thenReturn(20);
        when(properties.getExtractionRetry()).thenReturn(1);
        when(properties.getExtractionRetryDelayMs()).thenReturn(0);
    }

    private RawItem rawItem(String id, String parentId, String designation,
                            Integer quantity, String type, boolean hasChildren) {
        RawItem i = new RawItem();
        i.setId(id);
        i.setParentId(parentId);
        i.setPosition("01");
        i.setDesignation(designation);
        i.setName("Деталь");
        i.setQuantity(quantity);
        i.setUnit("шт");
        i.setMaterial("Сталь");
        i.setType(type);
        i.setHasChildren(hasChildren);
        return i;
    }

    // ===== extract: валидация =====

    @Test
    @DisplayName("extract: null projectId -> BomException")
    void extract_nullProjectId_throws() {
        assertThatThrownBy(() -> service.extract(null, "geo-1", "SIEMENS"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("extract: blank projectId -> BomException")
    void extract_blankProjectId_throws() {
        assertThatThrownBy(() -> service.extract("   ", "geo-1", "SIEMENS"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("extract: пустой RawBom -> BomException.emptyStructure")
    void extract_emptyRawBom_throws() {
        RawBom empty = new RawBom("root", "СБ-001", "Корпус", List.of());
        when(integrationClient.detectCadSystem(eq("proj1"), eq("geo-1"))).thenReturn("SIEMENS");
        when(integrationClient.extractBom(eq("proj1"), eq("geo-1"), eq("SIEMENS"))).thenReturn(empty);
        when(integrationClient.extractConnections(eq("proj1"), eq("geo-1"), eq("SIEMENS"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.extract("proj1", "geo-1", "SIEMENS"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("extract: успех с SIEMENS -> результат с items")
    void extract_siemens_ok() {
        RawItem item = rawItem("i1", null, "Д-001", 1, "PART", false);
        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(item));
        when(integrationClient.detectCadSystem(eq("proj1"), eq("geo-1"))).thenReturn("SIEMENS");
        when(integrationClient.extractBom(eq("proj1"), eq("geo-1"), eq("SIEMENS"))).thenReturn(bom);
        when(integrationClient.extractConnections(eq("proj1"), eq("geo-1"), eq("SIEMENS"))).thenReturn(List.of());

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "SIEMENS");

        assertThat(result.getRootDesignation()).isEqualTo("СБ-001");
        assertThat(result.getRootName()).isEqualTo("Корпус");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getDesignation()).isEqualTo("Д-001");
    }

    @Test
    @DisplayName("extract: явный cadSystem передан")
    void extract_explicitCad() {
        RawItem item = rawItem("i1", null, "Д-001", 1, "PART", false);
        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(item));
        when(integrationClient.extractBom(eq("proj1"), eq("geo-1"), eq("KOMPAS"))).thenReturn(bom);
        when(integrationClient.extractConnections(eq("proj1"), eq("geo-1"), eq("KOMPAS"))).thenReturn(List.of());

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "KOMPAS");
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("extract: невалидная CAD-система -> BomException.validation")
    void extract_invalidCad_throws() {
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("INVALID_CAD");

        assertThatThrownBy(() -> service.extract("proj1", "geo-1", "INVALID_CAD"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("extract: connections передаются в результат")
    void extract_withConnections() {
        RawItem i1 = rawItem("i1", null, "Д-001", 1, "PART", false);
        RawItem i2 = rawItem("i2", null, "Д-002", 1, "PART", false);
        RawConnection c = new RawConnection();
        c.setId("c1");
        c.setType("BOLT");
        c.setPart1Id("i1");
        c.setPart2Id("i2");
        c.setParameters(Map.of("k", "v"));

        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(i1, i2));
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS"))).thenReturn(bom);
        when(integrationClient.extractConnections(anyString(), anyString(), eq("SIEMENS"))).thenReturn(List.of(c));

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "SIEMENS");
        assertThat(result.getConnections()).hasSize(1);
        assertThat(result.getConnections().get(0).getType()).isEqualTo("BOLT");
    }

    @Test
    @DisplayName("extract: normalizeItems обнуляет невалидный parentId")
    void extract_normalizeItems_invalidParent() {
        RawItem item = rawItem("i1", "nonexistent", "Д-001", 1, "PART", false);
        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(item));
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS"))).thenReturn(bom);
        when(integrationClient.extractConnections(anyString(), anyString(), eq("SIEMENS"))).thenReturn(List.of());

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "SIEMENS");
        assertThat(result.getItems().get(0).getParentId()).isNull();
    }

    @Test
    @DisplayName("extract: normalizeItems корректный parentId остаётся")
    void extract_normalizeItems_validParent() {
        RawItem parent = rawItem("p1", null, "СБ-002", 1, "ASSEMBLY", true);
        RawItem child = rawItem("c1", "p1", "Д-001", 1, "PART", false);
        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(parent, child));
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS"))).thenReturn(bom);
        when(integrationClient.extractConnections(anyString(), anyString(), eq("SIEMENS"))).thenReturn(List.of());

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "SIEMENS");
        BomItemDto childDto = result.getItems().stream()
                .filter(i -> "c1".equals(i.getId()))
                .findFirst().orElseThrow();
        assertThat(childDto.getParentId()).isEqualTo("p1");
    }

    @Test
    @DisplayName("extract: connections с null part1Id/part2Id пропускаются")
    void extract_connections_invalidSkipped() {
        RawItem i1 = rawItem("i1", null, "Д-001", 1, "PART", false);
        RawConnection c1 = new RawConnection();
        c1.setId("c1");
        c1.setType("BOLT");
        c1.setPart1Id(null);
        c1.setPart2Id("i1");

        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(i1));
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS"))).thenReturn(bom);
        when(integrationClient.extractConnections(anyString(), anyString(), eq("SIEMENS"))).thenReturn(List.of(c1));

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "SIEMENS");
        assertThat(result.getConnections()).isEmpty();
    }

    @Test
    @DisplayName("extract: normalizeType — невалидный type -> ASSEMBLY для hasChildren")
    void extract_normalizeType_assembly() {
        RawItem parent = rawItem("p1", null, "СБ-002", 1, null, true);
        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(parent));
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS"))).thenReturn(bom);
        when(integrationClient.extractConnections(anyString(), anyString(), eq("SIEMENS"))).thenReturn(List.of());

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "SIEMENS");
        assertThat(result.getItems().get(0).getType()).isEqualTo("ASSEMBLY");
    }

    @Test
    @DisplayName("extract: retry при ошибке CAD, затем успех")
    void extract_retryOnError() {
        RawItem item = rawItem("i1", null, "Д-001", 1, "PART", false);
        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(item));
        when(properties.getExtractionRetry()).thenReturn(2);
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS")))
                .thenThrow(new RuntimeException("first fail"))
                .thenReturn(bom);
        when(integrationClient.extractConnections(anyString(), anyString(), eq("SIEMENS"))).thenReturn(List.of());

        BomService.BomExtractionResult result = service.extract("proj1", "geo-1", "SIEMENS");
        assertThat(result.getItems()).hasSize(1);
        verify(integrationClient, times(2)).extractBom(anyString(), anyString(), eq("SIEMENS"));
    }

    @Test
    @DisplayName("extract: все retry исчерпаны -> BomException.extractionFailed")
    void extract_allRetriesExhausted_throws() {
        when(properties.getExtractionRetry()).thenReturn(2);
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS")))
                .thenThrow(new RuntimeException("fail"));

        assertThatThrownBy(() -> service.extract("proj1", "geo-1", "SIEMENS"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("extract: связи через отдельный вызов integrationClient")
    void extract_connectionsFromIntegrationClient() {
        RawItem i1 = rawItem("i1", null, "Д-001", 1, "PART", false);
        RawBom bom = new RawBom("root", "СБ-001", "Корпус", List.of(i1));
        when(integrationClient.detectCadSystem(anyString(), anyString())).thenReturn("SIEMENS");
        when(integrationClient.extractBom(anyString(), anyString(), eq("SIEMENS"))).thenReturn(bom);
        when(integrationClient.extractConnections(anyString(), anyString(), eq("SIEMENS"))).thenReturn(List.of());

        service.extract("proj1", "geo-1", "SIEMENS");
        verify(integrationClient).extractConnections(anyString(), anyString(), eq("SIEMENS"));
    }
}