package ru.iktk.bom.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import ru.iktk.bom.dto.AssemblyConnectionDto;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.dto.SpecificationDto;
import ru.iktk.bom.model.AssemblyConnection;
import ru.iktk.bom.model.AssemblyUnit;
import ru.iktk.bom.model.BomItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Маппер между сущностями BOM и DTO.
 */
@Component
public class BomMapper {

    private final ObjectMapper objectMapper;

    public BomMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // -------------------- BomItem <-> BomItemDto --------------------

    public BomItemDto toItemDto(BomItem item) {
        if (item == null) return null;
        return BomItemDto.builder()
                .id(item.getId())
                .parentId(item.getParentId())
                .position(item.getPosition())
                .designation(item.getDesignation())
                .name(item.getName())
                .quantity(item.getQuantity())
                .unit(item.getUnit())
                .material(item.getMaterial())
                .type(item.getType())
                .order(item.getOrder())
                .level(item.getLevel())
                .build();
    }

    public BomItem toItemEntity(BomItemDto dto) {
        if (dto == null) return null;
        BomItem item = new BomItem();
        item.setId(dto.getId());
        item.setParentId(dto.getParentId());
        item.setPosition(dto.getPosition());
        item.setDesignation(dto.getDesignation());
        item.setName(dto.getName());
        if (dto.getQuantity() != null) item.setQuantity(dto.getQuantity());
        if (dto.getUnit() != null) item.setUnit(dto.getUnit());
        item.setMaterial(dto.getMaterial());
        if (dto.getType() != null) item.setType(dto.getType());
        if (dto.getOrder() != null) item.setOrder(dto.getOrder());
        if (dto.getLevel() != null) item.setLevel(dto.getLevel());
        return item;
    }

    // -------------------- AssemblyConnection <-> DTO --------------------

    public AssemblyConnection toConnectionEntity(AssemblyConnectionDto dto) {
        if (dto == null) return null;
        AssemblyConnection conn = new AssemblyConnection();
        conn.setId(dto.getId());
        conn.setType(dto.getType());
        conn.setPart1Id(dto.getPart1Id());
        conn.setPart2Id(dto.getPart2Id());
        conn.setParameters(dto.getParameters());
        return conn;
    }

    public AssemblyConnectionDto toConnectionDto(AssemblyConnection conn) {
        if (conn == null) return null;
        return AssemblyConnectionDto.builder()
                .id(conn.getId())
                .type(conn.getType())
                .part1Id(conn.getPart1Id())
                .part2Id(conn.getPart2Id())
                .parameters(conn.getParameters())
                .build();
    }

    // -------------------- JSON-снимок для VersioningService --------------------

    public JsonNode toJsonNode(AssemblyUnit root, List<BomItem> items,
                               List<AssemblyConnection> connections) {
        ObjectNode rootNode = objectMapper.createObjectNode();
        if (root != null) {
            rootNode.put("id", root.getId());
            rootNode.put("projectId", root.getProjectId());
            rootNode.put("designation", root.getDesignation());
            rootNode.put("name", root.getName());
            rootNode.put("version", root.getVersion());
            rootNode.put("status", root.getStatus());
        }

        ArrayNode itemsArray = rootNode.putArray("items");
        if (items != null) {
            for (BomItem item : items) {
                ObjectNode node = itemsArray.addObject();
                node.put("id", item.getId());
                node.put("parentId", item.getParentId());
                node.put("designation", item.getDesignation());
                node.put("name", item.getName());
                node.put("quantity", item.getQuantity());
                node.put("type", item.getType());
            }
        }

        ArrayNode connArray = rootNode.putArray("connections");
        if (connections != null) {
            for (AssemblyConnection conn : connections) {
                ObjectNode node = connArray.addObject();
                node.put("id", conn.getId());
                node.put("type", conn.getType());
                node.put("part1Id", conn.getPart1Id());
                node.put("part2Id", conn.getPart2Id());
            }
        }
        return rootNode;
    }

    // -------------------- SpecificationDto --------------------

    public SpecificationDto toSpecification(List<BomItem> items) {
        if (items == null) {
            return new SpecificationDto();
        }
        SpecificationDto spec = new SpecificationDto();
        spec.setTotalPositions(items.size());

        List<SpecificationDto.Row> rows = new ArrayList<>();
        int pos = 1;
        for (BomItem item : items) {
            rows.add(new SpecificationDto.Row(
                    item.getType(),
                    item.getPosition() != null ? item.getPosition() : String.valueOf(pos++),
                    item.getDesignation(),
                    item.getName(),
                    item.getQuantity(),
                    item.getUnit(),
                    item.getNote()));
        }
        spec.setRows(rows);
        return spec;
    }
}