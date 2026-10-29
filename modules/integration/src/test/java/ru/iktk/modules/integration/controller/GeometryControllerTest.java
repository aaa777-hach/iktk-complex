package ru.iktk.modules.integration.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.model.Geometry;
import ru.iktk.modules.integration.parser.StepAssemblyParser;
import ru.iktk.modules.integration.service.GeometryEstimateService;
import ru.iktk.modules.integration.service.GeometryService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GeometryController.class)
@TestPropertySource(properties = "integration.cad.directory=target/test-cad-s33")
@DisplayName("GeometryController — GET /{projectId} + POST /from-step (сессия 33)")
class GeometryControllerTest {

    private static final Path CAD_DIR = Paths.get("target", "test-cad-s33");
    private static final String SAMPLE_STEP = "94.3701110-01.STEP";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GeometryService geometryService;

    @MockBean
    private GeometryEstimateService estimateService;

    @MockBean
    private StepAssemblyParser parser;

    @BeforeAll
    static void createCadFixture() throws IOException {
        Files.createDirectories(CAD_DIR);
        Files.writeString(CAD_DIR.resolve(SAMPLE_STEP), "ISO-10303-21;END-ISO-10303-21;");
    }

    @AfterAll
    static void cleanupCadFixture() throws IOException {
        if (!Files.exists(CAD_DIR)) return;
        try (var walk = Files.walk(CAD_DIR)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        }
    }

    private Map<String, Object> sampleBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectRef", "geo-12345");
        m.put("material", "Steel 20Kh");
        m.put("volume", BigDecimal.valueOf(1000));
        m.put("complexityScore", 2);
        return m;
    }

    // ===== старые тесты GET =====

    @Test
    @DisplayName("GET found -> 200 + JSON")
    void found_200() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), eq("geo-12345"))).thenReturn(sampleBody());

        mvc.perform(get("/api/v1/geometry/p-1").param("objectRef", "geo-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectRef").value("geo-12345"))
                .andExpect(jsonPath("$.material").value("Steel 20Kh"))
                .andExpect(jsonPath("$.complexityScore").value(2));
    }

    @Test
    @DisplayName("GET not found -> 404")
    void notFound_404() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), eq("geo-missing"))).thenReturn(null);

        mvc.perform(get("/api/v1/geometry/p-1").param("objectRef", "geo-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET без objectRef -> service получает null, 200 если найден default")
    void noObjectRef_passesNull() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), isNull())).thenReturn(sampleBody());

        mvc.perform(get("/api/v1/geometry/p-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectRef").value("geo-12345"));
    }

    @Test
    @DisplayName("GET без objectRef + not found -> 404")
    void noObjectRef_notFound_404() throws Exception {
        when(geometryService.buildGeometry(eq("p-1"), isNull())).thenReturn(null);

        mvc.perform(get("/api/v1/geometry/p-1"))
                .andExpect(status().isNotFound());
    }

    // ===== новые тесты POST /from-step (сессия 33) =====

    private Geometry savedGeometry() {
        return Geometry.builder()
                .id("g-1").objectRef("94.3701110-01").projectId("p-1")
                .material("Steel 45")
                .volume(BigDecimal.valueOf(1750)).mass(BigDecimal.valueOf(0.0137))
                .minWallThickness(BigDecimal.valueOf(4)).minFilletRadius(BigDecimal.valueOf(2))
                .minDraftAngle(BigDecimal.valueOf(1)).nominalDimensions(BigDecimal.valueOf(110))
                .complexityScore(1).undercuts(false).hotSpots(false)
                .build();
    }

    private GeometryEstimateService.EstimateResult estimateStub() {
        GeometryEstimateService.EstimateResult r = new GeometryEstimateService.EstimateResult();
        r.material = "Steel 45";
        r.volume = BigDecimal.valueOf(1750);
        r.mass = BigDecimal.valueOf(0.0137);
        r.minWallThickness = BigDecimal.valueOf(4);
        r.minFilletRadius = BigDecimal.valueOf(2);
        r.minDraftAngle = BigDecimal.valueOf(1);
        r.nominalDimensions = BigDecimal.valueOf(110);
        r.complexityScore = 1;
        r.warnings.add("volume estimated: 1000 + 250*items (1)");
        return r;
    }

    @Test
    @DisplayName("POST /from-step OK -> 200 + warnings + source=STEP_ESTIMATE")
    void fromStep_200() throws Exception {
        RawBomDto bom = new RawBomDto();
        bom.setRootId("94.3701110-01");
        bom.setItems(List.of());
        when(parser.parse(any(Path.class))).thenReturn(bom);
        when(estimateService.estimate(any(RawBomDto.class), isNull())).thenReturn(estimateStub());
        when(geometryService.upsertFromStep(eq("94.3701110-01"), isNull(), any(), eq(true)))
                .thenReturn(savedGeometry());

        mvc.perform(post("/api/v1/geometry/from-step")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectRef\":\"" + SAMPLE_STEP + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectRef").value("94.3701110-01"))
                .andExpect(jsonPath("$.material").value("Steel 45"))
                .andExpect(jsonPath("$.source").value("STEP_ESTIMATE"))
                .andExpect(jsonPath("$.warnings[0]").exists());
    }

    @Test
    @DisplayName("POST /from-step файл не найден -> 404 STEP_FILE_NOT_FOUND")
    void fromStep_fileNotFound_404() throws Exception {
        mvc.perform(post("/api/v1/geometry/from-step")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectRef\":\"missing-file-xyz\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STEP_FILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /from-step уже существует + overwrite=false -> 409 ALREADY_EXISTS")
    void fromStep_conflict_409() throws Exception {
        RawBomDto bom = new RawBomDto();
        bom.setRootId("94.3701110-01");
        bom.setItems(List.of());
        when(parser.parse(any(Path.class))).thenReturn(bom);
        when(estimateService.estimate(any(RawBomDto.class), isNull())).thenReturn(estimateStub());
        when(geometryService.upsertFromStep(eq("94.3701110-01"), isNull(), any(), eq(false)))
                .thenThrow(new GeometryService.AlreadyExistsException("94.3701110-01"));

        mvc.perform(post("/api/v1/geometry/from-step")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectRef\":\"" + SAMPLE_STEP + "\",\"overwrite\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_EXISTS"));
    }
}