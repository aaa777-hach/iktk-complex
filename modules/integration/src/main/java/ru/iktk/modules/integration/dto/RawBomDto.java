package ru.iktk.modules.integration.dto;

import java.util.List;

public class RawBomDto {
    private String rootId;
    private String rootDesignation;
    private String rootName;
    private List<RawItemDto> items;

    public String getRootId() { return rootId; }
    public void setRootId(String rootId) { this.rootId = rootId; }
    public String getRootDesignation() { return rootDesignation; }
    public void setRootDesignation(String rootDesignation) { this.rootDesignation = rootDesignation; }
    public String getRootName() { return rootName; }
    public void setRootName(String rootName) { this.rootName = rootName; }
    public List<RawItemDto> getItems() { return items; }
    public void setItems(List<RawItemDto> items) { this.items = items; }
}
