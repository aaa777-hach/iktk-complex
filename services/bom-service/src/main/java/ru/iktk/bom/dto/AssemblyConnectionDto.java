package ru.iktk.bom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * DTO связи/сопряжения сборочной единицы.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyConnectionDto {

    private String id;
    private String type;
    private String part1Id;
    private String part2Id;
    private Map<String, Object> parameters;

    public AssemblyConnectionDto() {
    }

    private AssemblyConnectionDto(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.part1Id = b.part1Id;
        this.part2Id = b.part2Id;
        this.parameters = b.parameters;
    }

    public static Builder builder() { return new Builder(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPart1Id() { return part1Id; }
    public void setPart1Id(String part1Id) { this.part1Id = part1Id; }
    public String getPart2Id() { return part2Id; }
    public void setPart2Id(String part2Id) { this.part2Id = part2Id; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public static class Builder {
        private String id;
        private String type;
        private String part1Id;
        private String part2Id;
        private Map<String, Object> parameters;

        public Builder id(String v) { this.id = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder part1Id(String v) { this.part1Id = v; return this; }
        public Builder part2Id(String v) { this.part2Id = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }

        public AssemblyConnectionDto build() { return new AssemblyConnectionDto(this); }
    }
}