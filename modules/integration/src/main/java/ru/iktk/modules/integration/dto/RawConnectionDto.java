package ru.iktk.modules.integration.dto;

import java.util.Map;

public class RawConnectionDto {
    private String id;
    private String type;
    private String part1Id;
    private String part2Id;
    private Map<String, Object> parameters;

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
}
