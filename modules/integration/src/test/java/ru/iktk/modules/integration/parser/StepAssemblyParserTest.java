package ru.iktk.modules.integration.parser;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.dto.RawItemDto;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepAssemblyParserTest {

    private final StepAssemblyParser parser = new StepAssemblyParser();

    private static Path fixture(String name) {
        URL url = StepAssemblyParserTest.class.getClassLoader().getResource("step/" + name);
        if (url == null) throw new IllegalStateException("fixture not found: step/" + name);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("invalid fixture URL: " + url, e);
        }
    }

    @Test
    void twoLevelAssembly() throws IOException {
        RawBomDto bom = parser.parse(fixture("assembly-2level.step"));
        assertThat(bom.getRootId()).isEqualTo("ASM-ROOT");
        assertThat(bom.getRootName()).isEqualTo("ASM-ROOT");
        assertThat(bom.getItems()).hasSize(1);
        RawItemDto it = bom.getItems().get(0);
        assertThat(it.getId()).isEqualTo("PART-CHILD");
        assertThat(it.getParentId()).isEqualTo("ASM-ROOT");
        assertThat(it.getLevel()).isEqualTo(1);
        assertThat(it.getQuantity()).isEqualTo(1);
        assertThat(it.getType()).isEqualTo("PART");
        assertThat(it.isHasChildren()).isFalse();
    }

    @Test
    void threeLevelAssembly() throws IOException {
        RawBomDto bom = parser.parse(fixture("assembly-3level.step"));
        assertThat(bom.getRootId()).isEqualTo("ASM-ROOT");
        assertThat(bom.getItems()).hasSize(2);

        RawItemDto sub = bom.getItems().get(0);
        assertThat(sub.getId()).isEqualTo("SUB-ASM");
        assertThat(sub.getLevel()).isEqualTo(1);
        assertThat(sub.getType()).isEqualTo("ASSEMBLY");
        assertThat(sub.isHasChildren()).isTrue();

        RawItemDto leaf = bom.getItems().get(1);
        assertThat(leaf.getId()).isEqualTo("LEAF-PART");
        assertThat(leaf.getParentId()).isEqualTo("SUB-ASM");
        assertThat(leaf.getLevel()).isEqualTo(2);
        assertThat(leaf.getType()).isEqualTo("PART");
    }

    @Test
    void multiQuantityAggregated() throws IOException {
        RawBomDto bom = parser.parse(fixture("assembly-qty27.step"));
        assertThat(bom.getItems()).hasSize(1);
        RawItemDto it = bom.getItems().get(0);
        assertThat(it.getId()).isEqualTo("PART-27X");
        assertThat(it.getQuantity()).isEqualTo(27);
        assertThat(it.getLevel()).isEqualTo(1);
    }

    @Test
    void singlePart_noNauo() throws IOException {
        RawBomDto bom = parser.parse(fixture("single-part.step"));
        assertThat(bom.getRootId()).isEqualTo("PART-ONLY");
        assertThat(bom.getItems()).isEmpty();
    }

    @Test
    void corrupted_noProducts_throws() {
        assertThatThrownBy(() -> parser.parse(fixture("corrupted.step")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRODUCT");
    }

    @Test
    void missingFile_throwsNoSuchFile() {
        Path missing = Path.of("does-not-exist-" + System.nanoTime() + ".step");
        assertThatThrownBy(() -> parser.parse(missing))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void depthExceedsMax_throws(@TempDir Path tmp) throws IOException {
        int n = 12;
        StringBuilder sb = new StringBuilder("ISO-10303-21;\r\nDATA;\r\n");
        for (int i = 1; i <= n; i++) {
            sb.append("#").append(i).append(" = PRODUCT ( 'P").append(i).append("', 'P").append(i).append("', '', ( #1 ) ) ;\r\n");
        }
        for (int i = 1; i <= n; i++) {
            sb.append("#").append(100 + i).append(" = PRODUCT_DEFINITION_FORMATION ( '', '', #").append(i).append(" ) ;\r\n");
        }
        for (int i = 1; i <= n; i++) {
            sb.append("#").append(200 + i).append(" = PRODUCT_DEFINITION ( 'v1', '', #").append(100 + i).append(", #300 ) ;\r\n");
        }
        for (int i = 1; i < n; i++) {
            sb.append("#").append(300 + i).append(" = NEXT_ASSEMBLY_USAGE_OCCURRENCE ( 'NAUO").append(i).append("', ' ', ' ', #").append(200 + i).append(", #").append(200 + i + 1).append(", $ ) ;\r\n");
        }
        sb.append("ENDSEC;\r\nEND-ISO-10303-21;\r\n");
        Path f = tmp.resolve("deep.step");
        Files.writeString(f, sb.toString(), StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> parser.parse(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 10");
    }

    @Test
    void realStepFile_ifPresent() throws IOException {
        String dir = System.getProperty("iktk.cad.dir", "C:/Users/Antipovich/Desktop/Orchestr/Info/3D");
        Path real = Path.of(dir, "94.3701110-01.STEP");
        Assumptions.assumeTrue(Files.exists(real), "real STEP not found, skip: " + real);

        RawBomDto bom = parser.parse(real);
        assertThat(bom.getRootId()).isEqualTo("94.3701110-01");
        assertThat(bom.getItems()).hasSize(1);
        RawItemDto it = bom.getItems().get(0);
        assertThat(it.getId()).isEqualTo("94.3701110-01-00");
        assertThat(it.getQuantity()).isEqualTo(27);
        assertThat(it.getLevel()).isEqualTo(1);
        assertThat(it.getType()).isEqualTo("PART");
    }
}