package ru.iktk.modules.machining.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.MachiningMethod;

@FeignClient(name = "equipment", url = "${iktk.machining.equipment.url}")
public interface EquipmentClient {

    @GetMapping("/api/equipment/{id}")
    EquipmentInfo getEquipment(@org.springframework.web.bind.annotation.PathVariable("id") String equipmentId);

    @GetMapping("/api/equipment/find")
    String findAvailable(@RequestParam("projectId") String projectId,
                         @RequestParam("method") MachiningMethod method,
                         @RequestParam("cnc") boolean cnc,
                         @RequestParam("geometry") GeometryData geometry);
}