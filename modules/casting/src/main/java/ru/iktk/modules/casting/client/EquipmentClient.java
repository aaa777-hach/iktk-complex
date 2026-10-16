package ru.iktk.modules.casting.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.casting.model.CastingMethod;

@FeignClient(name = "equipment-service", url = "${iktk.equipment.url:http://equipment-service:8080}")
public interface EquipmentClient {

    @GetMapping("/api/v1/equipment/is-method-available")
    boolean isMethodAvailable(@RequestParam("projectId") String projectId,
                              @RequestParam("method") CastingMethod method);
}