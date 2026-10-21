package ru.iktk.bom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO позиции BOM (деталь/подсборка/крепеж/материал).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BomItemDto {

    private String id;
    private String parentId;
    private String position;
    private String designation;
    private String name;
    private Integer quantity;
    private String unit;
    private String material;
    private String type;
    private Integer order;
    private Integer level;
    private List<BomItemDto> children = new ArrayList<>();

    public BomItemDto() {
    }

    private BomItemDto(Builder b) {
        this.id = b.id;
        this.parentId = b.parentId;
        this.position = b.position;
        this.designation = b.designation;
        this.name = b.name;
        this.quantity = b.quantity;
        this.unit = b.unit;
        this.material = b.material;
        this.type = b.type;
        this.order = b.order;
        this.level = b.level;
    }

    public static Builder builder() { return new Builder(); }

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
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public List<BomItemDto> getChildren() { return children; }
    public void setChildren(List<BomItemDto> children) { this.children = children; }

    public static class Builder {
        private String id;
        private String parentId;
        private String position;
        private String designation;
        private String name;
        private Integer quantity;
        private String unit;
        private String material;
        private String type;
        private Integer order;
        private Integer level;

        public Builder id(String v) { this.id = v; return this; }
        public Builder parentId(String v) { this.parentId = v; return this; }
        public Builder position(String v) { this.position = v; return this; }
        public Builder designation(String v) { this.designation = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder quantity(Integer v) { this.quantity = v; return this; }
        public Builder unit(String v) { this.unit = v; return this; }
        public Builder material(String v) { this.material = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder order(Integer v) { this.order = v; return this; }
        public Builder level(Integer v) { this.level = v; return this; }

        public BomItemDto build() { return new BomItemDto(this); }
    }
}