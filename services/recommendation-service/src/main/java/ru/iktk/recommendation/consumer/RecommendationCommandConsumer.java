package ru.iktk.recommendation.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.iktk.recommendation.dto.EquipmentRequirementDto;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.service.GapAnalysisService;
import ru.iktk.recommendation.service.OutsourcingService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecommendationCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationCommandConsumer.class);
    private static final String STATE_GAP_RESOLUTION = "EQUIPMENT_GAP_RESOLUTION";
    private static final String STATE_OUTSOURCING_SELECTED = "OUTSOURCING_SELECTED";

    private final GapAnalysisService gapAnalysisService;
    private final OutsourcingService outsourcingService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${iktk.messaging.results-exchange:iktk.results}")
    private String resultsExchange;

    @Value("${iktk.messaging.results-routing-prefix:iktk.result}")
    private String resultsRoutingPrefix;

    public RecommendationCommandConsumer(GapAnalysisService gapAnalysisService,
                                         OutsourcingService outsourcingService,
                                         RabbitTemplate rabbitTemplate,
                                         ObjectMapper objectMapper) {
        this.gapAnalysisService = gapAnalysisService;
        this.outsourcingService = outsourcingService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.recommendation-service.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        log.info("Recommendation command received: routingKey={}, body.length={}",
                routingKey, bodyJson == null ? 0 : bodyJson.length());
        try {
            JsonNode root = objectMapper.readTree(bodyJson);
            String projectId = text(root, "projectId");
            String state = text(root, "state");
            if (state == null || state.isBlank() || STATE_GAP_RESOLUTION.equalsIgnoreCase(state)) {
                handleGapAnalysis(root, projectId);
            } else if (STATE_OUTSOURCING_SELECTED.equalsIgnoreCase(state)) {
                handleOutsourcingSelected(root, projectId);
            } else {
                log.warn("Unknown state '{}', running gap analysis by default", state);
                handleGapAnalysis(root, projectId);
            }
        } catch (Exception e) {
            log.error("Failed to process recommendation command", e);
        }
    }

    private void handleGapAnalysis(JsonNode root, String projectId) throws Exception {
        GapAnalysisRequest request = new GapAnalysisRequest();
        request.setProjectId(projectId);
        request.setStrategy(text(root, "strategy"));

        List<EquipmentRequirementDto> reqs = new ArrayList<>();
        JsonNode payload = root.path("payload");
        JsonNode reqNode = payload.path("requirements");
        if (reqNode.isMissingNode() || !reqNode.isArray()) {
            reqNode = root.path("requirements");
        }
        if (reqNode.isArray()) {
            for (JsonNode n : reqNode) {
                reqs.add(objectMapper.treeToValue(n, EquipmentRequirementDto.class));
            }
        }
        request.setRequirements(reqs);

        GapAnalysisResponse response = gapAnalysisService.run(request);
        publishResults("RECOMMENDATIONS_READY", projectId, response);
    }

    private void handleOutsourcingSelected(JsonNode root, String projectId) throws Exception {
        JsonNode payload = root.path("payload");
        String optionId = text(payload, "optionId");
        String comment = text(payload, "comment");
        if (optionId == null || optionId.isBlank()) {
            throw new IllegalArgumentException("OUTSOURCING_SELECTED: missing payload.optionId");
        }
        OutsourcingOptionDto dto = outsourcingService.select(optionId, comment);
        Map<String, Object> event = baseEvent("OUTSOURCING_SELECTED", projectId);
        event.put("optionId", dto.getId());
        event.put("contractor", dto.getContractor());
        event.put("cost", dto.getCost());
        event.put("status", dto.getStatus());
        rabbitTemplate.convertAndSend(resultsExchange, resultsRoutingPrefix + ".OUTSOURCING_SELECTED", objectMapper.writeValueAsString(event));
    }

    private void publishResults(String eventType, String projectId, GapAnalysisResponse response) throws Exception {
        Map<String, Object> event = baseEvent(eventType, projectId);
        event.put("analysisId", response.getId());
        event.put("status", response.getStatus());
        event.put("gapsCount", response.getGapsCount());
        event.put("recommendationsCount", response.getRecommendationsCount());
        event.put("strategy", response.getStrategy());
        rabbitTemplate.convertAndSend(resultsExchange, resultsRoutingPrefix + "." + eventType, objectMapper.writeValueAsString(event));
        log.info("Published {} for project={} analysis={}", eventType, projectId, response.getId());
    }

    private Map<String, Object> baseEvent(String eventType, String projectId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("service", "recommendation-service");
        event.put("projectId", projectId);
        event.put("timestamp", Instant.now().toString());
        return event;
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}