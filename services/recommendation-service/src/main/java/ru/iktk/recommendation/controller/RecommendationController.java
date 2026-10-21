package ru.iktk.recommendation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.recommendation.dto.EquipmentRecommendationDto;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.dto.RecommendationResponse;
import ru.iktk.recommendation.dto.VendorCatalogDto;
import ru.iktk.recommendation.model.EquipmentRecommendation;
import ru.iktk.recommendation.model.OutsourcingOption;
import ru.iktk.recommendation.service.EquipmentRecommendationService;
import ru.iktk.recommendation.service.GapAnalysisService;
import ru.iktk.recommendation.service.OutsourcingService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final GapAnalysisService gapAnalysisService;
    private final EquipmentRecommendationService recommendationService;
    private final OutsourcingService outsourcingService;

    public RecommendationController(GapAnalysisService gapAnalysisService,
                                    EquipmentRecommendationService recommendationService,
                                    OutsourcingService outsourcingService) {
        this.gapAnalysisService = gapAnalysisService;
        this.recommendationService = recommendationService;
        this.outsourcingService = outsourcingService;
    }

    @PostMapping("/gap-analysis")
    public GapAnalysisResponse runGapAnalysis(@RequestBody GapAnalysisRequest request) {
        return gapAnalysisService.run(request);
    }

    @GetMapping("/gap-analysis/{id}")
    public GapAnalysisResponse getGapAnalysis(@PathVariable String id) {
        return gapAnalysisService.get(id);
    }

    @GetMapping("/gap-analysis/latest/{projectId}")
    public GapAnalysisResponse getLatestGapAnalysis(@PathVariable String projectId) {
        return gapAnalysisService.getLatest(projectId);
    }

    @GetMapping("/gap-analysis/project/{projectId}")
    public List<GapAnalysisResponse> getAllGapAnalyses(@PathVariable String projectId) {
        return gapAnalysisService.getAllByProject(projectId);
    }

    @GetMapping("/gap-analysis/{id}/gaps")
    public List<Map<String, Object>> getGapsBySeverity(@PathVariable String id,
                                                       @RequestParam String severity) {
        return gapAnalysisService.getGapsBySeverity(id, severity);
    }

    @GetMapping("/gap-analysis/{id}/summary")
    public Map<String, Object> getGapAnalysisSummary(@PathVariable String id) {
        return gapAnalysisService.getSummary(id);
    }

    @GetMapping("/gap-analysis/project/{projectId}/summary")
    public Map<String, Object> getGapAnalysisProjectSummary(@PathVariable String projectId) {
        return gapAnalysisService.getProjectSummary(projectId);
    }

    @PostMapping("/gap-analysis/{id}/recalculate")
    public GapAnalysisResponse recalculate(@PathVariable String id,
                                           @RequestParam(required = false) String strategy) {
        return gapAnalysisService.recalculate(id, strategy);
    }

    @PostMapping("/gap/{gapId}/generate")
    public List<EquipmentRecommendation> generateRecommendations(@PathVariable String gapId) {
        return recommendationService.generateForGap(gapId);
    }

    @GetMapping("/project/{projectId}")
    public List<EquipmentRecommendationDto> getByProject(@PathVariable String projectId,
                                                         @RequestParam(required = false) String type,
                                                         @RequestParam(required = false) String status) {
        return recommendationService.getByProject(projectId, type, status);
    }

    @GetMapping("/gap/{gapId}")
    public List<EquipmentRecommendationDto> getRecommendationsByGap(@PathVariable String gapId) {
        return recommendationService.getByGap(gapId);
    }

    @GetMapping("/{id}")
    public EquipmentRecommendationDto getRecommendation(@PathVariable String id) {
        return recommendationService.getById(id);
    }

    @PostMapping("/{id}/accept")
    public RecommendationResponse accept(@PathVariable String id,
                                         @RequestParam(required = false) String comment) {
        return recommendationService.accept(id, comment);
    }

    @PostMapping("/{id}/reject")
    public RecommendationResponse reject(@PathVariable String id,
                                         @RequestParam(required = false) String reason) {
        return recommendationService.reject(id, reason);
    }

    @PostMapping("/{id}/adjust-process")
    public RecommendationResponse adjustProcess(@PathVariable String id,
                                                @RequestBody(required = false) Map<String, String> payload) {
        return recommendationService.adjustProcess(id, payload);
    }

    @PostMapping("/{id}/purchase")
    public RecommendationResponse purchase(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, String> payload) {
        return recommendationService.purchase(id, payload);
    }

    @PostMapping("/{id}/select-outsourcing")
    public RecommendationResponse selectOutsourcing(@PathVariable String id,
                                                @RequestBody(required = false) Map<String, String> payload) {
        return recommendationService.selectOutsourcing(id, payload);
    }

    @GetMapping("/vendors")
    public List<VendorCatalogDto> searchVendors(@RequestParam(required = false) String equipmentType,
                                                @RequestParam(required = false) Double maxPrice,
                                                @RequestParam(required = false) Integer maxLeadTimeDays,
                                                @RequestParam(required = false) String region) {
        return recommendationService.searchVendorCatalog(equipmentType, maxPrice, maxLeadTimeDays, region);
    }

    @GetMapping("/vendors/{id}")
    public VendorCatalogDto getVendor(@PathVariable String id) {
        return recommendationService.getVendorById(id);
    }

    @GetMapping("/project/{projectId}/summary")
    public Map<String, Object> getRecommendationSummary(@PathVariable String projectId) {
        return recommendationService.getSummary(projectId);
    }

    @PostMapping("/outsourcing/gap/{gapId}/generate")
    public List<OutsourcingOption> generateOutsourcing(@PathVariable String gapId) {
        return outsourcingService.generateForGap(gapId);
    }

    @GetMapping("/outsourcing/gap/{gapId}")
    public List<OutsourcingOptionDto> getOutsourcingByGap(@PathVariable String gapId) {
        return outsourcingService.getByGap(gapId);
    }

    @GetMapping("/outsourcing/project/{projectId}")
    public List<OutsourcingOptionDto> getOutsourcingByProject(@PathVariable String projectId) {
        return outsourcingService.getByProject(projectId);
    }

    @GetMapping("/outsourcing/{id}")
    public OutsourcingOptionDto getOutsourcing(@PathVariable String id) {
        return outsourcingService.getById(id);
    }

    @GetMapping("/outsourcing/project/{projectId}/type/{type}")
    public List<OutsourcingOptionDto> getOutsourcingByProjectAndType(@PathVariable String projectId,
                                                                     @PathVariable String type) {
        return outsourcingService.getByProjectAndType(projectId, type);
    }

    @PostMapping("/outsourcing/{id}/select")
    public OutsourcingOptionDto selectOutsourcingOption(@PathVariable String id,
                                                        @RequestParam(required = false) String comment) {
        return outsourcingService.select(id, comment);
    }

    @PostMapping("/outsourcing/{id}/reject")
    public OutsourcingOptionDto rejectOutsourcingOption(@PathVariable String id,
                                                        @RequestParam(required = false) String reason) {
        return outsourcingService.reject(id, reason);
    }

    @PostMapping("/outsourcing/gap/{gapId}")
    public OutsourcingOptionDto addOutsourcing(@PathVariable String gapId,
                                               @RequestBody OutsourcingOptionDto dto) {
        return outsourcingService.add(gapId, dto);
    }

    @DeleteMapping("/outsourcing/{id}")
    public ResponseEntity<Void> deleteOutsourcing(@PathVariable String id) {
        outsourcingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/outsourcing/project/{projectId}/summary")
    public Map<String, Object> getOutsourcingSummary(@PathVariable String projectId) {
        return outsourcingService.getSummary(projectId);
    }

    @GetMapping("/outsourcing/gap/{gapId}/has-options")
    public boolean hasOutsourcingOptions(@PathVariable String gapId) {
        return outsourcingService.hasOptions(gapId);
    }

    @GetMapping("/outsourcing/gap/{gapId}/selected")
    public OutsourcingOptionDto getSelectedOutsourcing(@PathVariable String gapId) {
        return outsourcingService.getSelected(gapId);
    }
}