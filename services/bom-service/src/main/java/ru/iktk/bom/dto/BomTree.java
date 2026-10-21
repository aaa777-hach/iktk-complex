package ru.iktk.bom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Иерархическое дерево BOM по сборочной единице.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BomTree {

    private String id;
    private String projectId;
    private String rootAssemblyId;
    private String designation;
    private String name;
    private List<BomItemDto> items;
    private Integer totalPositions;
    private Integer maxDepth;

    public BomTree() {
    }

    private BomTree(Builder b) {
        this.id = b.id;
        this.projectId = b.projectId;
        this.rootAssemblyId = b.rootAssemblyId;
        this.designation = b.designation;
        this.name = b.name;
        this.items = b.items;
        this.totalPositions = b.totalPositions;
        this.maxDepth = b.maxDepth;
    }

    public static Builder builder() { return new Builder(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getRootAssemblyId() { return rootAssemblyId; }
    public void setRootAssemblyId(String rootAssemblyId) { this.rootAssemblyId = rootAssemblyId; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<BomItemDto> getItems() { return items; }
    public void setItems(List<BomItemDto> items) { this.items = items; }
    public Integer getTotalPositions() { return totalPositions; }
    public void setTotalPositions(Integer totalPositions) { this.totalPositions = totalPositions; }
    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

    public static class Builder {
        private String id;
        private String projectId;
        private String rootAssemblyId;
        private String designation;
        private String name;
        private List<BomItemDto> items;
        private Integer totalPositions;
        private Integer maxDepth;

        public Builder id(String v) { this.id = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder rootAssemblyId(String v) { this.rootAssemblyId = v; return this; }
        public Builder designation(String v) { this.designation = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder items(List<BomItemDto> v) { this.items = v; return this; }
        public Builder totalPositions(Integer v) { this.totalPositions = v; return this; }
        public Builder maxDepth(Integer v) { this.maxDepth = v; return this; }

        public BomTree build() { return new BomTree(this); }
    }
}