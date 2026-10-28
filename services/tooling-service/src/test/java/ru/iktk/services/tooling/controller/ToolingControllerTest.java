package ru.iktk.services.tooling.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.services.tooling.dto.CuttingToolDto;
import ru.iktk.services.tooling.service.ToolingMethod;
import ru.iktk.services.tooling.service.ToolingService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ToolingController.class)
@DisplayName("ToolingController — REST-контракт")
class ToolingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ToolingService toolingService;

    @Test
    @DisplayName("MILLING -> 200 + END_MILL")
    void milling_endMill() throws Exception {
        when(toolingService.select(eq(ToolingMethod.MILLING), any()))
                .thenReturn(new CuttingToolDto("END_MILL", BigDecimal.TEN, "HSS", "TiAlN"));

        mvc.perform(get("/api/v1/tooling/select").param("method", "MILLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("END_MILL"))
                .andExpect(jsonPath("$.diameter").value(10))
                .andExpect(jsonPath("$.material").value("HSS"))
                .andExpect(jsonPath("$.coating").value("TiAlN"));
    }

    @Test
    @DisplayName("DRILLING -> 200 + DRILL")
    void drilling_drill() throws Exception {
        when(toolingService.select(eq(ToolingMethod.DRILLING), any()))
                .thenReturn(new CuttingToolDto("DRILL", BigDecimal.valueOf(5), "HSS", "TiN"));

        mvc.perform(get("/api/v1/tooling/select").param("method", "DRILLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DRILL"))
                .andExpect(jsonPath("$.diameter").value(5));
    }

    @Test
    @DisplayName("GRINDING -> 200 + GRINDING_WHEEL")
    void grinding_wheel() throws Exception {
        when(toolingService.select(eq(ToolingMethod.GRINDING), any()))
                .thenReturn(new CuttingToolDto("GRINDING_WHEEL", BigDecimal.valueOf(200), "Al2O3", "-"));

        mvc.perform(get("/api/v1/tooling/select").param("method", "GRINDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("GRINDING_WHEEL"))
                .andExpect(jsonPath("$.diameter").value(200));
    }

    @Test
    @DisplayName("CONTROL -> 204 No Content")
    void control_noContent() throws Exception {
        when(toolingService.select(eq(ToolingMethod.CONTROL), any())).thenReturn(null);

        mvc.perform(get("/api/v1/tooling/select").param("method", "CONTROL"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Невалидный method -> 400")
    void unknownMethod_badRequest() throws Exception {
        mvc.perform(get("/api/v1/tooling/select").param("method", "FOO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("material опционален, не влияет на выбор (контракт)")
    void material_optional() throws Exception {
        when(toolingService.select(eq(ToolingMethod.MILLING), eq("steel-45")))
                .thenReturn(new CuttingToolDto("END_MILL", BigDecimal.TEN, "HSS", "TiAlN"));

        mvc.perform(get("/api/v1/tooling/select")
                        .param("method", "MILLING")
                        .param("material", "steel-45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("END_MILL"));
    }

    @Test
    @DisplayName("method в нижнем регистре -> 200 (case-insensitive)")
    void lowercaseMethod_ok() throws Exception {
        when(toolingService.select(eq(ToolingMethod.MILLING), any()))
                .thenReturn(new CuttingToolDto("END_MILL", BigDecimal.TEN, "HSS", "TiAlN"));

        mvc.perform(get("/api/v1/tooling/select").param("method", "milling"))
                .andExpect(status().isOk());
    }
}