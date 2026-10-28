package ru.iktk.modules.integration.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.modules.integration.service.GeometryService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GeometryController.class)
@DisplayName("GeometryController — GET /api/v1/geometry/{projectId}")
class GeometryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GeometryService geometryService;

    private Map<String, Object> sampleBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectRef", "geo-12345");
        m.put("material", "Steel 20Kh");
        m.put("volume", BigDecimal.valueOf(1000));
        m.put("complexityScore", 2);
        return m;
    }

    @Test
    @DisplayName("found -> 200 + JSON")
    void found_200() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), eq("geo-12345"))).thenReturn(sampleBody());

        mvc.perform(get("/api/v1/geometry/p-1").param("objectRef", "geo-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectRef").value("geo-12345"))
                .andExpect(jsonPath("$.material").value("Steel 20Kh"))
                .andExpect(jsonPath("$.complexityScore").value(2));
    }

    @Test
    @DisplayName("not found -> 404")
    void notFound_404() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), eq("geo-missing"))).thenReturn(null);

        mvc.perform(get("/api/v1/geometry/p-1").param("objectRef", "geo-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("без objectRef -> service получает null, 200 если найден default")
    void noObjectRef_passesNull() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), isNull())).thenReturn(sampleBody());

        mvc.perform(get("/api/v1/geometry/p-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectRef").value("geo-12345"));
    }

    @Test
    @DisplayName("без objectRef + not found -> 404")
    void noObjectRef_notFound_404() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), isNull())).thenReturn(null);

        mvc.perform(get("/api/v1/geometry/p-1"))
                .andExpect(status().isNotFound());
    }
}