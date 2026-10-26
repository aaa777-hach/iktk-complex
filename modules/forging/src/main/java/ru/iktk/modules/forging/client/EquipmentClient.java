package ru.iktk.modules.forging.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.forging.model.ForgingMethod;

@FeignClient(name = "equipment-service", url = "${iktk.equipment.url:http://equipment-service:8080}")
public interface EquipmentClient {

    @GetMapping("/api/v1/equipment/is-method-available")
    boolean isMethodAvailable(@RequestParam("projectId") String projectId,
                              @RequestParam("method") ForgingMethod method);
}
