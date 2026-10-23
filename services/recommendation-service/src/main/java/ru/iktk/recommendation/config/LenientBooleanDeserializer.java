package ru.iktk.recommendation.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Терпимый десериализатор Boolean.
 *
 * <p>Принимает:
 * <ul>
 *   <li>{@code true} / {@code false} (JSON boolean);</li>
 *   <li>{@code "true"} / {@code "false"} (строка, оркестратор может слать так);</li>
 *   <li>{@code 1} / {@code 0} (число);</li>
 *   <li>{@code "1"} / {@code "0"}, {@code "yes"} / {@code "no"}, {@code "on"} / {@code "off"};</li>
 *   <li>{@code null} / отсутствие — вернёт {@code null}.</li>
 * </ul>
 */
public class LenientBooleanDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.intValue() != 0;
        }
        if (node.isTextual()) {
            String s = node.asText().trim().toLowerCase();
            if (s.isEmpty() || "null".equals(s)) {
                return null;
            }
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s)) {
                return true;
            }
            if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "n".equals(s) || "off".equals(s)) {
                return false;
            }
            return null;
        }
        return null;
    }
}