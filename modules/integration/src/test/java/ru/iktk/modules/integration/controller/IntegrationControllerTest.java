package ru.iktk.modules.integration.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.modules.integration.model.Geometry;
import ru.iktk.modules.integration.model.GeometryTestFactory;
import ru.iktk.modules.integration.repository.GeometryRepository;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationController.class)
@DisplayName("IntegrationController — /cad-system, /bom, /connections")
class IntegrationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GeometryRepository geometryRepository;

    private Geometry geometry;

    @BeforeEach
    void setUp() {
        geometry = GeometryTestFactory.newGeometry();
        ReflectionTestUtils.setField(geometry, "objectRef", "geo-12345");
        ReflectionTestUtils.setField(geometry, "material", "Steel 20Kh");
    }

    @Test
    @DisplayName("/cad-system -> 200 STEP (детерминированно)")
    void cadSystem_step() throws Exception {
        mvc.perform(get("/api/v1/integration/cad-system").param("projectId", "p-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("STEP"));
    }

    @Test
    @DisplayName("/connections -> 200 [] (seed без связей)")
    void connections_empty() throws Exception {
        mvc.perform(get("/api/v1/integration/connections").param("projectId", "p-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("/bom found -> 200 + RawBomDto (1 item)")
    void bom_found_200() throws Exception {
        when(geometryRepository.findByObjectRef(eq("geo-12345"))).thenReturn(Optional.of(geometry));

        mvc.perform(get("/api/v1/integration/bom")
                        .param("projectId", "p-1")
                        .param("geometryId", "geo-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootId").value("ASSY-p-1"))
                .andExpect(jsonPath("$.rootDesignation").value("ASSY-p-1"))
                .andExpect(jsonPath("$.items[0].id").value("geo-12345"))
                .andExpect(jsonPath("$.items[0].material").value("Steel 20Kh"))
                .andExpect(jsonPath("$.items[0].type").value("PART"))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    @DisplayName("/bom not found -> 404")
    void bom_notFound_404() throws Exception {
        when(geometryRepository.findByObjectRef(eq("geo-missing"))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/integration/bom")
                        .param("projectId", "p-1")
                        .param("geometryId", "geo-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/bom без geometryId -> default 'geo-12345'")
    void bom_noGeometryId_usesDefault() throws Exception {
        when(geometryRepository.findByObjectRef(eq("geo-12345"))).thenReturn(Optional.of(geometry));

        mvc.perform(get("/api/v1/integration/bom").param("projectId", "p-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("geo-12345"));
    }

    @Test
    @DisplayName("/bom blank geometryId -> default 'geo-12345'")
    void bom_blankGeometryId_usesDefault() throws Exception {
        when(geometryRepository.findByObjectRef(eq("geo-12345"))).thenReturn(Optional.of(geometry));

        mvc.perform(get("/api/v1/integration/bom")
                        .param("projectId", "p-1")
                        .param("geometryId", "   "))
                .andExpect(status().isOk());
    }
}