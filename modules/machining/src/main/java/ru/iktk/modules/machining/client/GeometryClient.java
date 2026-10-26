package ru.iktk.modules.machining.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.machining.dto.GeometryData;

@FeignClient(name = "integration", url = "${iktk.machining.integration.url}")
public interface GeometryClient {

    @GetMapping("/api/geometry/fetch")
    GeometryData fetchGeometry(@RequestParam("projectId") String projectId,
                               @RequestParam("objectRef") String objectRef);
}