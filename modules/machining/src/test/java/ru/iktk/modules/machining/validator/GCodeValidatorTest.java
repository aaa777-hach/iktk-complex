package ru.iktk.modules.machining.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты GCodeValidator.
 */
class GCodeValidatorTest {

    private GCodeValidator validator;
    private EquipmentInfo equipment;
    private MachiningProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MachiningProperties();
        properties.setBlankLength(java.math.BigDecimal.valueOf(100));
        properties.setBlankWidth(java.math.BigDecimal.valueOf(100));
        properties.setBlankHeight(java.math.BigDecimal.valueOf(20));

        validator = new GCodeValidator(List.of(
                new SyntaxRule(),
                new BoundsRule(),
                new StructureRule(),
                new FeedRule()
        ), properties);

        equipment = new EquipmentInfo(
                "EQ-TEST", "CNC-TEST", "ISO6983", 3, Map.of()
        );
    }

    @Test
    void validFullProgram() {
        String program = String.join("\n",
                "%",
                "O0001 (CNC-TEST)",
                "; tool=END_MILL",
                "; mode=MILLING",
                "G00 X0.00 Y0.00 Z0.00",
                "G01 X0.00 Y0.00 Z-2.00 F0.2",
                "G01 X100.00 Y0.00 Z-2.00 F0.2",
                "G01 X100.00 Y7.00 Z-2.00 F0.2",
                "G00 X50.00 Y50.00 Z0.00",
                "%",
                "M30"
        );

        ValidationResult result = validator.validate(program, equipment);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void missingM30() {
        String program = String.join("\n",
                "%",
                "O0001 (CNC-TEST)",
                "G00 X0.00 Y0.00 Z0.00",
                "G01 X50.00 Y50.00 Z-2.00 F0.2"
        );

        ValidationResult result = validator.validate(program, equipment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .anyMatch(e -> e.contains("STRUCTURE") && e.contains("M30"));
    }

    @Test
    void outOfBoundsX() {
        String program = String.join("\n",
                "%",
                "O0001 (CNC-TEST)",
                "G00 X0.00 Y0.00 Z0.00",
                "G01 X999.00 Y50.00 Z-2.00 F0.2",
                "%",
                "M30"
        );

        ValidationResult result = validator.validate(program, equipment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .anyMatch(e -> e.contains("BOUNDS") && e.contains("X=999"));
    }

    @Test
    void syntaxError() {
        String program = String.join("\n",
                "%",
                "O0001 (CNC-TEST)",
                "GARBAGE",
                "%",
                "M30"
        );

        ValidationResult result = validator.validate(program, equipment);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .anyMatch(e -> e.contains("SYNTAX") && e.contains("GARBAGE"));
    }

    @Test
    void feedWarning() {
        // G01 без F — warning, не error
        String fragment = String.join("\n",
                "; tool=END_MILL",
                "G00 X0.00 Y0.00 Z0.00",
                "G01 X10.00 Y10.00 Z-2.00"
        );

        ValidationResult result = validator.validateFragment(fragment, equipment);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("FEED") && w.contains("G01 без F"));
    }

    @Test
    void validFragmentWithoutHeaderFooter() {
        String fragment = String.join("\n",
                "; tool=END_MILL",
                "; mode=MILLING",
                "G00 X0.00 Y0.00 Z0.00",
                "G01 X0.00 Y0.00 Z-2.00 F0.2",
                "G01 X100.00 Y0.00 Z-2.00 F0.2"
        );

        ValidationResult result = validator.validateFragment(fragment, equipment);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }
}