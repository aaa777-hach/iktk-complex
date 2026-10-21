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
import ru.iktk.bom.dto.BomAnalyzeRequest;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.dto.BomResponse;
import ru.iktk.bom.dto.BomTree;
import ru.iktk.bom.dto.BomUpdateRequest;
import ru.iktk.bom.dto.SpecificationDto;
import ru.iktk.bom.exception.BomException;
import ru.iktk.bom.mapper.BomMapper;
import ru.iktk.bom.model.AssemblyConnection;
import ru.iktk.bom.model.AssemblyUnit;
import ru.iktk.bom.model.BomItem;
import ru.iktk.bom.repository.AssemblyConnectionRepository;
import ru.iktk.bom.repository.AssemblyRepository;
import ru.iktk.bom.repository.BomItemRepository;
import ru.iktk.shared.storage.StorageClient;
import ru.iktk.shared.versioning.VersioningService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BomService — анализ, дерево, обновление, спецификация")
class BomServiceTest {

    @Mock private AssemblyRepository assemblyRepository;
    @Mock private BomItemRepository itemRepository;
    @Mock private AssemblyConnectionRepository connectionRepository;
    @Mock private BomMapper mapper;
    @Mock private BomExtractionService extractionService;
    @Mock private IntegrationClient integrationClient;
    @Mock private StorageClient storageClient;
    @Mock private VersioningService versioningService;
    @Mock private BomProperties properties;

    private BomService service;

    @BeforeEach
    void setUp() {
        service = new BomService(assemblyRepository, itemRepository, connectionRepository,
                mapper, extractionService, integrationClient, storageClient,
                versioningService, properties);

        ReflectionTestUtils.setField(service, "properties", properties);
        when(properties.getMaxDepth()).thenReturn(20);
        when(properties.getExtractionRetry()).thenReturn(1);
        when(properties.getExtractionRetryDelayMs()).thenReturn(0);
        when(properties.isDemoFallback()).thenReturn(false);

        when(assemblyRepository.save(any(AssemblyUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assemblyRepository.findById(anyString())).thenReturn(Optional.empty());
        when(itemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(connectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByAssemblyIdOrderByPosition(anyString())).thenReturn(List.of());
    }

    private AssemblyUnit unit(String id, String projectId) {
        AssemblyUnit u = new AssemblyUnit();
        u.setId(id);
        u.setProjectId(projectId);
        u.setDesignation("СБ-001");
        u.setName("Корпус");
        u.setItemCount(0);
        u.setMaxDepth(0);
        u.setVersion(1);
        u.setStatus("DRAFT");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        return u;
    }

    private BomItemDto dto(String id) {
        return BomItemDto.builder()
                .id(id)
                .designation("Д-001")
                .name("Вал")
                .quantity(1)
                .type("PART")
                .build();
    }

    // ===== analyze =====

    @Test
    @DisplayName("analyze: null request -> BomException.validation")
    void analyze_nullRequest_throws() {
        assertThatThrownBy(() -> service.analyze(null))
                .isInstanceOf(BomException.class);
        verify(extractionService, never()).extract(anyString(), any(), any());
    }

    @Test
    @DisplayName("analyze: blank projectId -> BomException.validation")
    void analyze_blankProjectId_throws() {
        BomAnalyzeRequest req = new BomAnalyzeRequest();
        req.setProjectId("   ");
        assertThatThrownBy(() -> service.analyze(req))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("analyze: BOM уже существует, force=false -> BomResponse.alreadyExists")
    void analyze_alreadyExists() {
        BomAnalyzeRequest req = BomAnalyzeRequest.forProject("proj1");
        AssemblyUnit existing = unit("u1", "proj1");
        when(assemblyRepository.findRootByProject("proj1")).thenReturn(Optional.of(existing));
        when(itemRepository.countByAssemblyId("u1")).thenReturn(5L);

        BomResponse r = service.analyze(req);

        assertThat(r.getStatus()).isEqualTo("ALREADY_EXISTS");
        assertThat(r.getPositionsCount()).isEqualTo(5);
        verify(extractionService, never()).extract(anyString(), any(), any());
    }

    @Test
    @DisplayName("analyze: extraction пустая -> BomException.emptyStructure")
    void analyze_emptyExtraction_throws() {
        BomAnalyzeRequest req = BomAnalyzeRequest.forProject("proj1");
        when(assemblyRepository.findRootByProject("proj1")).thenReturn(Optional.empty());
        BomService.BomExtractionResult empty = new BomService.BomExtractionResult(
                "СБ-001", "Корпус", List.of(), List.of());
        when(extractionService.extract(eq("proj1"), any(), any())).thenReturn(empty);

        assertThatThrownBy(() -> service.analyze(req))
                .isInstanceOf(BomException.class);
    }

    // ===== getAnalysis =====

    @Test
    @DisplayName("getAnalysis: найден -> BomResponse")
    void getAnalysis_found() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));

        BomResponse r = service.getAnalysis("u1");
        assertThat(r).isNotNull();
        assertThat(r.getProjectId()).isEqualTo("proj1");
    }

    @Test
    @DisplayName("getAnalysis: не найден -> BomException")
    void getAnalysis_notFound_throws() {
        when(assemblyRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAnalysis("missing"))
                .isInstanceOf(BomException.class);
    }

    // ===== getBomTreeByProject / getBomTree =====

    @Test
    @DisplayName("getBomTreeByProject: нет BOM -> BomException")
    void getBomTreeByProject_notFound() {
        when(assemblyRepository.findRootByProject("proj1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBomTreeByProject("proj1"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("getBomTreeByProject: найден -> BomTree")
    void getBomTreeByProject_found() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findRootByProject("proj1")).thenReturn(Optional.of(u));

        BomTree tree = service.getBomTreeByProject("proj1");
        assertThat(tree).isNotNull();
        assertThat(tree.getId()).isEqualTo("u1");
        assertThat(tree.getProjectId()).isEqualTo("proj1");
    }

    @Test
    @DisplayName("getBomTree: bomId пустой -> BomException")
    void getBomTree_blankId_throws() {
        assertThatThrownBy(() -> service.getBomTree("   "))
                .isInstanceOf(BomException.class);
    }

    // ===== getItems =====

    @Test
    @DisplayName("getItems: делегирует в репозиторий")
    void getItems_ok() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(itemRepository.findByAssemblyIdOrderByPosition("u1")).thenReturn(List.of());
        when(mapper.toItemDto(any(BomItem.class))).thenReturn(dto("i1"));

        List<BomItemDto> items = service.getItems("u1", null);
        assertThat(items).isEmpty();
    }

    // ===== updateItems =====

    @Test
    @DisplayName("updateItems: null request -> BomException")
    void updateItems_nullRequest_throws() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.updateItems("u1", null))
                .isInstanceOf(BomException.class);
    }

    // ===== addItem =====

    @Test
    @DisplayName("addItem: null dto -> BomException")
    void addItem_nullDto_throws() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.addItem("u1", null, null))
                .isInstanceOf(BomException.class);
    }

    // ===== getConnections =====

    @Test
    @DisplayName("getConnections: делегирует в репозиторий")
    void getConnections_ok() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(connectionRepository.findByAssemblyId("u1")).thenReturn(List.of());
        when(mapper.toConnectionDto(any(AssemblyConnection.class))).thenReturn(null);

        List<AssemblyConnectionDto> conns = service.getConnections("u1");
        assertThat(conns).isEmpty();
    }

    // ===== getAssemblySequence =====

    @Test
    @DisplayName("getAssemblySequence: пустой BOM -> пустой список")
    void getAssemblySequence_empty() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(itemRepository.findByAssemblyIdOrderByPosition("u1")).thenReturn(List.of());

        assertThat(service.getAssemblySequence("u1")).isEmpty();
    }

    // ===== getSpecification =====

    @Test
    @DisplayName("getSpecification: делегирует в BomMapper")
    void getSpecification_ok() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        SpecificationDto spec = new SpecificationDto();
        when(mapper.toSpecification(anyList())).thenReturn(spec);

        assertThat(service.getSpecification("u1")).isSameAs(spec);
    }

    // ===== export =====

    @Test
    @DisplayName("export: невалидный format -> BomException")
    void export_invalidFormat_throws() {
        AssemblyUnit u = unit("u1", "proj1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.export("u1", "XYZ"))
                .isInstanceOf(BomException.class);
    }
}