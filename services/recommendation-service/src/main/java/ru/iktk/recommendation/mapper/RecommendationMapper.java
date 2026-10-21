package ru.iktk.recommendation.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.iktk.recommendation.dto.*;
import ru.iktk.recommendation.model.*;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RecommendationMapper {

    private final ObjectMapper objectMapper;

    public RecommendationMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EquipmentRecommendationDto toDto(EquipmentRecommendation e) {
        if (e == null) return null;
        return EquipmentRecommendationDto.builder()
                .id(e.getId()).projectId(e.getProjectId()).gapId(e.getGapId())
                .analysisId(e.getAnalysisId()).type(e.getType()).status(e.getStatus())
                .equipmentId(e.getEquipmentId()).vendorId(e.getVendorId())
                .rationale(e.getRationale()).score(e.getScore())
                .estimatedCost(e.getEstimatedCost()).leadTimeDays(e.getLeadTimeDays())
                .acceptedBy(e.getAcceptedBy()).acceptedAt(e.getAcceptedAt())
                .parameters(e.getParameters())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    public GapAnalysisResponse toGapResponse(EquipmentGapAnalysis a) {
        if (a == null) return null;
        return GapAnalysisResponse.builder()
                .id(a.getId()).analysisId(a.getId()).projectId(a.getProjectId())
                .strategy(a.getStrategy()).status(a.getStatus())
                .requestedBy(a.getRequestedBy()).gapsCount(a.getGapsCount())
                .recommendationsCount(a.getRecommendationsCount())
                .gaps(a.getGaps()).summary(a.getSummary())
                .startedAt(a.getStartedAt()).completedAt(a.getCompletedAt())
                .durationMs(a.durationMs()).error(a.getError())
                .build();
    }

    public GapAnalysisResponse toDto(EquipmentGapAnalysis a) { return toGapResponse(a); }

    public VendorCatalogDto toVendorDto(VendorCatalog v) {
        if (v == null) return null;
        return VendorCatalogDto.builder()
                .id(v.getId()).vendorId(v.getVendorId()).vendorName(v.getVendorName())
                .equipmentType(v.getEquipmentType()).equipmentModel(v.getEquipmentModel())
                .price(v.getPrice()).leadTimeDays(v.getLeadTimeDays())
                .contactInfo(v.getContactInfo()).region(v.getRegion())
                .active(v.getActive()).specifications(v.getSpecifications())
                .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt())
                .build();
    }

    public VendorCatalogDto toDto(VendorCatalog v) { return toVendorDto(v); }

    public OutsourcingOptionDto toOutsourcingDto(OutsourcingOption o) {
        if (o == null) return null;
        return OutsourcingOptionDto.builder()
                .id(o.getId()).gapId(o.getGapId()).projectId(o.getProjectId())
                .type(o.getType()).operation(o.getOperation()).contractor(o.getContractor()).cost(o.getCost())
                .qualityRating(o.getQualityRating()).comment(o.getComment())
                .leadTimeDays(o.getLeadTimeDays()).status(o.getStatus())
                .score(o.getScore())
                .selectedBy(o.getSelectedBy()).selectedAt(o.getSelectedAt())
                .parameters(o.getParameters())
                .createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt())
                .build();
    }

    public OutsourcingOptionDto toDto(OutsourcingOption o) { return toOutsourcingDto(o); }

    public OutsourcingOption toOutsourcingEntity(OutsourcingOptionDto d) {
        if (d == null) return null;
        OutsourcingOption o = new OutsourcingOption();
        o.setId(d.getId());
        o.setGapId(d.getGapId());
        o.setProjectId(d.getProjectId());
        o.setType(d.getType());
        o.setOperation(d.getOperation());
        o.setContractor(d.getContractor());
        o.setCost(d.getCost());
        o.setQualityRating(d.getQualityRating());
        o.setComment(d.getComment());
        o.setLeadTimeDays(d.getLeadTimeDays());
        o.setStatus(d.getStatus());
        o.setScore(d.getScore());
        o.setSelectedBy(d.getSelectedBy());
        o.setSelectedAt(d.getSelectedAt());
        o.setParameters(d.getParameters());
        o.setCreatedAt(d.getCreatedAt());
        o.setUpdatedAt(d.getUpdatedAt());
        return o;
    }

    public JsonNode toJsonNode(Object value) {
        try { return objectMapper.valueToTree(value); }
        catch (Exception e) { return null; }
    }

    public List<EquipmentRecommendationDto> toDtoList(List<EquipmentRecommendation> list) {
        return list == null ? List.of() : list.stream().map(this::toDto).collect(Collectors.toList());
    }
}