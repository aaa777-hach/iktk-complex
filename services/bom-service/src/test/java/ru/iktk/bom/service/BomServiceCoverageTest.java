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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BomService — дополнительное покрытие (items, connections, specification, sequence)")
class BomServiceCoverageTest {

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
        when(assemblyRepository.save(any(AssemblyUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.save(any(BomItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(connectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByAssemblyIdOrderByPosition(anyString())).thenReturn(List.of());
    }

    private AssemblyUnit unit(String id) {
        AssemblyUnit u = new AssemblyUnit();
        u.setId(id);
        u.setProjectId(UUID.randomUUID().toString());
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

    private BomItem item(String id, String assemblyId) {
        BomItem i = new BomItem();
        i.setId(id);
        i.setAssemblyId(assemblyId);
        i.setDesignation("Д-001");
        i.setName("Вал");
        i.setQuantity(1);
        i.setUnit("шт");
        i.setType("PART");
        i.setLevel(0);
        i.setOrder(0);
        i.setCreatedAt(Instant.now());
        i.setUpdatedAt(Instant.now());
        return i;
    }

    @Test
    @DisplayName("getItems: без фильтра и с фильтром по type")
    void getItems_ok() {
        AssemblyUnit u = unit("u1");
        BomItem i = item("i1", "u1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(itemRepository.findByAssemblyIdOrderByPosition("u1")).thenReturn(List.of(i));
        when(itemRepository.findByAssemblyId("u1")).thenReturn(List.of(i));
        when(itemRepository.findByAssemblyIdAndTypeOrderByPosition("u1", "PART")).thenReturn(List.of(i));
        BomItemDto dto = BomItemDto.builder().id("i1").build();
        when(mapper.toItemDto(i)).thenReturn(dto);

        assertThat(service.getItems("u1", null)).hasSize(1);
        assertThat(service.getItems("u1", "PART")).hasSize(1);
    }

    @Test
    @DisplayName("updateItems: null items -> BomException")
    void updateItems_nullItems_throws() {
        String bomId = UUID.randomUUID().toString();
        AssemblyUnit u = unit(bomId);
        when(assemblyRepository.findById(bomId)).thenReturn(Optional.of(u));

        BomUpdateRequest req = new BomUpdateRequest();
        req.setItems(null);
        assertThatThrownBy(() -> service.updateItems(bomId, req))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("updateItems: успешное обновление с UUID")
    void updateItems_ok() {
        String bomId = UUID.randomUUID().toString();
        AssemblyUnit u = unit(bomId);
        when(assemblyRepository.findById(bomId)).thenReturn(Optional.of(u));
        BomItemDto dto = BomItemDto.builder().id("i1").designation("Д-001").build();
        BomUpdateRequest req = new BomUpdateRequest(List.of(dto));
        when(mapper.toItemEntity(dto)).thenAnswer(inv -> item("i1", bomId));

        BomTree tree = service.updateItems(bomId, req);
        assertThat(tree).isNotNull();
    }

    @Test
    @DisplayName("addItem: null dto -> BomException")
    void addItem_nullDto_throws() {
        AssemblyUnit u = unit("u1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.addItem("u1", null, null))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("deleteItem: позиция не найдена -> BomException")
    void deleteItem_notFound_throws() {
        AssemblyUnit u = unit("u1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(itemRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteItem("u1", "missing"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("deleteItem: позиция не принадлежит сборке -> BomException")
    void deleteItem_wrongAssembly_throws() {
        AssemblyUnit u = unit("u1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        BomItem item = item("i1", "other");
        when(itemRepository.findById("i1")).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.deleteItem("u1", "i1"))
                .isInstanceOf(BomException.class);
    }

    @Test
    @DisplayName("deleteItem: успешное удаление")
    void deleteItem_ok() {
        AssemblyUnit u = unit("u1");
        BomItem item = item("i1", "u1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(itemRepository.findById("i1")).thenReturn(Optional.of(item));

        service.deleteItem("u1", "i1");
    }

    @Test
    @DisplayName("getConnections: список связей через BomMapper")
    void getConnections_ok() {
        AssemblyUnit u = unit("u1");
        AssemblyConnection conn = new AssemblyConnection();
        conn.setId("c1");
        conn.setType("BOLT");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(connectionRepository.findByAssemblyId("u1")).thenReturn(List.of(conn));
        AssemblyConnectionDto dto = AssemblyConnectionDto.builder().id("c1").build();
        when(mapper.toConnectionDto(conn)).thenReturn(dto);

        assertThat(service.getConnections("u1")).hasSize(1);
    }

    @Test
    @DisplayName("getSpecification: возвращает SpecificationDto от BomMapper")
    void getSpecification_empty_ok() {
        AssemblyUnit u = unit("u1");
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        SpecificationDto spec = new SpecificationDto();
        when(mapper.toSpecification(anyList())).thenReturn(spec);

        assertThat(service.getSpecification("u1")).isSameAs(spec);
    }

    @Test
    @DisplayName("getAssemblySequence: список позиций")
    void getAssemblySequence_ok() {
        AssemblyUnit u = unit("u1");
        BomItem i1 = item("i1", "u1");
        i1.setPosition("01");
        i1.setLevel(1);
        when(assemblyRepository.findById("u1")).thenReturn(Optional.of(u));
        when(itemRepository.findByAssemblyIdOrderByPosition("u1")).thenReturn(List.of(i1));
        when(mapper.toItemDto(i1)).thenReturn(BomItemDto.builder().id("i1").build());

        assertThat(service.getAssemblySequence("u1")).isNotEmpty();
    }
}