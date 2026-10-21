package ru.iktk.recommendation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@FeignClient(
        name = "equipment-service",
        url = "${iktk.clients.equipment-service.url:http://localhost:8085}"
)
public interface EquipmentClient {

    @PostMapping("/api/equipment/capabilities/matching")
    Optional<Map<String, Object>> findMatching(
            @RequestParam("operationType") String operationType,
            @RequestParam("requiredAccuracy") String requiredAccuracy,
            @RequestParam("maxDimensions") String maxDimensions,
            @RequestParam("material") String material,
            @RequestParam("requiredCnc") String requiredCnc,
            @RequestParam("requiredPowerKw") BigDecimal requiredPowerKw,
            @RequestParam("requiredForceKn") String requiredForceKn);

    @PostMapping("/api/equipment/capabilities/alternatives")
    List<Map<String, Object>> findAlternatives(
            @RequestParam("operationType") String operationType,
            @RequestParam("requiredAccuracy") String requiredAccuracy,
            @RequestBody List<Map<String, Object>> candidates);

    @PostMapping("/api/equipment/assign")
    void assignEquipment(
            @RequestParam("projectId") String projectId,
            @RequestParam("equipmentId") String equipmentId,
            @RequestParam("acceptedBy") String acceptedBy);
}