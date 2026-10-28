package ru.iktk.modules.machining.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import ru.iktk.modules.machining.client.dto.ToolingDto;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolingClientRouter — Feign + fallback")
class ToolingClientRouterTest {

    @Mock
    private ToolingClient feign;

    @Mock
    private GeometryData geometry;

    private ToolingClientRouter router;

    @BeforeEach
    void setUp() {
        router = new ToolingClientRouter(feign);
    }

    @Test
    @DisplayName("Feign 200 -> маппинг ToolingDto -> CuttingTool")
    void feignOk_mapsToCuttingTool() {
        ToolingDto dto = new ToolingDto();
        dto.setType("DRILL");
        dto.setDiameter(BigDecimal.valueOf(5));
        dto.setMaterial("HSS");
        dto.setCoating("TiN");
        when(geometry.getMaterial()).thenReturn("steel-45");
        when(feign.select(eq("DRILLING"), any())).thenReturn(ResponseEntity.ok(dto));

        CuttingTool tool = router.selectTool(MachiningMethod.DRILLING, geometry);

        assertThat(tool).isNotNull();
        assertThat(tool.getType()).isEqualTo("DRILL");
        assertThat(tool.getDiameter()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(tool.getMaterial()).isEqualTo("HSS");
        assertThat(tool.getCoating()).isEqualTo("TiN");
        verify(feign).select(eq("DRILLING"), any());
    }

    @Test
    @DisplayName("Feign 204 (CONTROL) -> null")
    void feign204_returnsNull() {
        when(geometry.getMaterial()).thenReturn(null);
        when(feign.select(eq("CONTROL"), any())).thenReturn(ResponseEntity.noContent().build());

        CuttingTool tool = router.selectTool(MachiningMethod.CONTROL, geometry);

        assertThat(tool).isNull();
    }

    @Test
    @DisplayName("Feign throw -> fallback (MILLING -> END_MILL)")
    void feignThrows_fallsBack() {
        when(geometry.getMaterial()).thenReturn("steel-45");
        when(feign.select(eq("MILLING"), any())).thenThrow(new RuntimeException("tooling down"));

        CuttingTool tool = router.selectTool(MachiningMethod.MILLING, geometry);

        assertThat(tool).isNotNull();
        assertThat(tool.getType()).isEqualTo("END_MILL");
        assertThat(tool.getDiameter()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("null method -> null (без вызова Feign)")
    void nullMethod_returnsNull() {
        assertThat(router.selectTool(null, geometry)).isNull();
    }
}