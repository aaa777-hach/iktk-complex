package ru.iktk.modules.integration.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.modules.integration.parser.StepAssemblyParser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StepBomController.class)
@Import(StepAssemblyParser.class)
@TestPropertySource(properties = "integration.cad.directory=src/test/resources/step")
class StepBomControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void singlePart_200() throws Exception {
        mvc.perform(get("/api/v1/integration/bom/step")
                        .param("projectId", "p1")
                        .param("objectRef", "single-part"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootId").value("PART-ONLY"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void twoLevelAssembly_200() throws Exception {
        mvc.perform(get("/api/v1/integration/bom/step")
                        .param("projectId", "p1")
                        .param("objectRef", "assembly-2level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootId").value("ASM-ROOT"))
                .andExpect(jsonPath("$.items[0].id").value("PART-CHILD"))
                .andExpect(jsonPath("$.items[0].level").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void qty27_200() throws Exception {
        mvc.perform(get("/api/v1/integration/bom/step")
                        .param("projectId", "p1")
                        .param("objectRef", "assembly-qty27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("PART-27X"))
                .andExpect(jsonPath("$.items[0].quantity").value(27));
    }

    @Test
    void missingFile_404() throws Exception {
        mvc.perform(get("/api/v1/integration/bom/step")
                        .param("projectId", "p1")
                        .param("objectRef", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STEP_FILE_NOT_FOUND"));
    }

    @Test
    void corrupted_400() throws Exception {
        mvc.perform(get("/api/v1/integration/bom/step")
                        .param("projectId", "p1")
                        .param("objectRef", "corrupted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STEP"));
    }

    @Test
    void pathTraversal_400() throws Exception {
        mvc.perform(get("/api/v1/integration/bom/step")
                        .param("projectId", "p1")
                        .param("objectRef", "../etc/passwd"))
                .andExpect(status().isBadRequest());
    }
}