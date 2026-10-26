package ru.iktk.modules.machining.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

@FeignClient(name = "tooling", url = "${iktk.machining.tooling.url}")
public interface ToolingClient {

    @GetMapping("/api/tooling/select")
    CuttingTool selectTool(@RequestParam("method") MachiningMethod method,
                           @RequestParam("geometry") GeometryData geometry);
}