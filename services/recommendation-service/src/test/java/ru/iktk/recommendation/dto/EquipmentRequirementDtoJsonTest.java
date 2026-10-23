package ru.iktk.recommendation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EquipmentRequirementDto — терпимый десериализатор requiredCnc")
class EquipmentRequirementDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("requiredCnc=true (boolean) -> true")
    void boolean_true() throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\",\"requiredCnc\":true}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isTrue();
    }

    @Test
    @DisplayName("requiredCnc=false (boolean) -> false")
    void boolean_false() throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\",\"requiredCnc\":false}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isFalse();
    }

    @Test
    @DisplayName("requiredCnc=\"true\" (строка) -> true")
    void string_true() throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\",\"requiredCnc\":\"true\"}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isTrue();
    }

    @Test
    @DisplayName("requiredCnc=\"false\" (строка) -> false")
    void string_false() throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\",\"requiredCnc\":\"false\"}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1\"", "1", "\"yes\"", "\"on\"", "\"Y\""})
    @DisplayName("truthy-варианты -> true")
    void truthy_variants(String value) throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\",\"requiredCnc\":" + value + "}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"0\"", "0", "\"no\"", "\"off\"", "\"N\""})
    @DisplayName("falsy-варианты -> false")
    void falsy_variants(String value) throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\",\"requiredCnc\":" + value + "}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isFalse();
    }

    @Test
    @DisplayName("requiredCnc=null -> null")
    void null_value() throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\",\"requiredCnc\":null}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isNull();
    }

    @Test
    @DisplayName("requiredCnc отсутствует -> null")
    void missing_value() throws Exception {
        EquipmentRequirementDto dto = mapper.readValue(
                "{\"operationType\":\"TURNING\"}", EquipmentRequirementDto.class);
        assertThat(dto.getRequiredCnc()).isNull();
    }
}