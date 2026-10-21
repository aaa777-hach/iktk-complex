package ru.iktk.bom.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Запрос на обновление позиций BOM.
 */
public class BomUpdateRequest {

    @Valid
    private List<BomItemDto> items;

    private String comment;

    public BomUpdateRequest() {
    }

    public BomUpdateRequest(List<BomItemDto> items) {
        this.items = items;
    }

    public List<BomItemDto> getItems() { return items; }
    public void setItems(List<BomItemDto> items) { this.items = items; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}