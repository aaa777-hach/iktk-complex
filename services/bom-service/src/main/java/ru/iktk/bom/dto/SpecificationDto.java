package ru.iktk.bom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Спецификация BOM по ГОСТ 2.106 (упрощённая структура).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpecificationDto {

    private String designation;
    private String name;
    private Integer totalPositions;
    private List<Row> rows = new ArrayList<>();

    public SpecificationDto() {
    }

    public SpecificationDto(String designation, String name) {
        this.designation = designation;
        this.name = name;
    }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getTotalPositions() { return totalPositions; }
    public void setTotalPositions(Integer totalPositions) { this.totalPositions = totalPositions; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }

    /**
     * Строка спецификации.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Row {
        private String section;
        private String position;
        private String designation;
        private String name;
        private Integer quantity;
        private String unit;
        private String note;

        public Row() {
        }

        public Row(String section, String position, String designation,
                   String name, Integer quantity, String unit, String note) {
            this.section = section;
            this.position = position;
            this.designation = designation;
            this.name = name;
            this.quantity = quantity;
            this.unit = unit;
            this.note = note;
        }

        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }
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
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}