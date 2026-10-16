package ru.iktk.modules.casting.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.casting.service.CastingProcessService;

@FeignClient(name = "integration", url = "${iktk.integration.url:http://integration:8080}")
public interface GeometryClient {

    @GetMapping("/api/v1/geometry/{projectId}")
    CastingProcessService.GeometryData fetchGeometry(
            @PathVariable("projectId") String projectId,
            @RequestParam("objectRef") String objectRef);
}