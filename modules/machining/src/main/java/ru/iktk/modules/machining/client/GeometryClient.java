package ru.iktk.modules.machining.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.machining.dto.GeometryData;

/**
 * Feign-клиент к integration (CAD-геометрия).
 *
 * <p>Совпадает с forging/casting: GET /api/v1/geometry/{projectId}?objectRef=...
 * Возвращает Map<String,Object>, который Jackson десериализует в
 * {@link GeometryData} (недостающие поля — дефолты).
 */
@FeignClient(name = "integration", url = "${iktk.machining.integration.url:http://localhost:8092}")
public interface GeometryClient {

    @GetMapping("/api/v1/geometry/{projectId}")
    GeometryData fetchGeometry(@PathVariable("projectId") String projectId,
                               @RequestParam("objectRef") String objectRef);
}