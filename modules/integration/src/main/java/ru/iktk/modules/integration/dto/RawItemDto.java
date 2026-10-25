package ru.iktk.modules.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RawItemDto {
    private String id;
    private String parentId;
    private String position;
    private String designation;
    private String name;
    private Integer quantity;
    private String unit;
    private String material;
    private String type;
    private boolean hasChildren;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isHasChildren() { return hasChildren; }
    public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }
}
