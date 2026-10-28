package ru.iktk.modules.integration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.modules.integration.model.Geometry;
import ru.iktk.modules.integration.model.GeometryTestFactory;
import ru.iktk.modules.integration.repository.GeometryRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeometryService — buildGeometry (DB lookup + Map-контракт)")
class GeometryServiceTest {

    @Mock
    private GeometryRepository repository;

    private GeometryService service;

    @BeforeEach
    void setUp() {
        service = new GeometryService(repository);
    }

    private Geometry sample() {
        Geometry g = GeometryTestFactory.newGeometry();
        ReflectionTestUtils.setField(g, "objectRef", "geo-12345");
        ReflectionTestUtils.setField(g, "projectId", "p-1");
        ReflectionTestUtils.setField(g, "material", "Steel 20Kh");
        ReflectionTestUtils.setField(g, "volume", BigDecimal.valueOf(1000));
        ReflectionTestUtils.setField(g, "mass", BigDecimal.valueOf(7850));
        ReflectionTestUtils.setField(g, "minWallThickness", BigDecimal.valueOf(3));
        ReflectionTestUtils.setField(g, "minFilletRadius", BigDecimal.valueOf(2));
        ReflectionTestUtils.setField(g, "minDraftAngle", BigDecimal.valueOf(1));
        ReflectionTestUtils.setField(g, "nominalDimensions", BigDecimal.valueOf(100));
        ReflectionTestUtils.setField(g, "complexityScore", 2);
        ReflectionTestUtils.setField(g, "undercuts", false);
        ReflectionTestUtils.setField(g, "hotSpots", false);
        return g;
    }

    @Test
    @DisplayName("objectRef null -> поиск по 'default-object' -> not found -> null")
    void nullObjectRef_usesDefault_notFound_null() {
        when(repository.findByObjectRef("default-object")).thenReturn(Optional.empty());
        assertThat(service.buildGeometry("p-1", null)).isNull();
        verify(repository).findByObjectRef("default-object");
    }

    @Test
    @DisplayName("objectRef blank -> 'default-object' -> not found -> null")
    void blankObjectRef_usesDefault() {
        when(repository.findByObjectRef("default-object")).thenReturn(Optional.empty());
        assertThat(service.buildGeometry("p-1", "   ")).isNull();
        verify(repository).findByObjectRef("default-object");
    }

    @Test
    @DisplayName("found -> Map с 11 полями (без projectId), значения из entity")
    void found_returnsMapOf11Fields() {
        when(repository.findByObjectRef("geo-12345")).thenReturn(Optional.of(sample()));

        Map<String, Object> body = service.buildGeometry("p-1", "geo-12345");

        assertThat(body).isNotNull().hasSize(11);
        assertThat(body.get("objectRef")).isEqualTo("geo-12345");
        assertThat(body.get("material")).isEqualTo("Steel 20Kh");
        assertThat(body.get("volume")).isEqualTo(BigDecimal.valueOf(1000));
        assertThat(body.get("mass")).isEqualTo(BigDecimal.valueOf(7850));
        assertThat(body.get("complexityScore")).isEqualTo(2);
        assertThat(body.get("undercuts")).isEqualTo(false);
        assertThat(body.get("hotSpots")).isEqualTo(false);
        assertThat(body).doesNotContainKey("projectId");
    }

    @Test
    @DisplayName("found -> порядок ключей: objectRef первый, hotSpots последний (LinkedHashMap)")
    void found_linkedHashMapOrder() {
        when(repository.findByObjectRef("geo-12345")).thenReturn(Optional.of(sample()));

        Map<String, Object> body = service.buildGeometry("p-1", "geo-12345");

        var keys = body.keySet().toArray(new String[0]);
        assertThat(keys[0]).isEqualTo("objectRef");
        assertThat(keys[keys.length - 1]).isEqualTo("hotSpots");
    }

    @Test
    @DisplayName("not found для заданного objectRef -> null, без fallback")
    void notFound_returnsNull() {
        when(repository.findByObjectRef("geo-missing")).thenReturn(Optional.empty());

        assertThat(service.buildGeometry("p-1", "geo-missing")).isNull();
        verify(repository).findByObjectRef("geo-missing");
    }

    @Test
    @DisplayName("found -> repository.findByObjectRef(objectRef) ровно 1 раз")
    void found_repositoryCalledOnce() {
        when(repository.findByObjectRef(anyString())).thenReturn(Optional.of(sample()));
        service.buildGeometry("p-1", "geo-12345");
        verify(repository).findByObjectRef("geo-12345");
        verify(repository, never()).findByObjectRef("default-object");
    }
}