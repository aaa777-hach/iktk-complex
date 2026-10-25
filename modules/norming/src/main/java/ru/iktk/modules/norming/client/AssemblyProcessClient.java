package ru.iktk.modules.norming.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.iktk.modules.norming.client.dto.AssemblyProcessDto;
import ru.iktk.modules.norming.client.dto.AssemblyStageDto;

import java.util.List;

/**
 * Feign-клиент к assembly-process-service.
 *
 * <p>Используется для получения спроектированного ТП сборки и его этапов,
 * чтобы выполнить расчёт норм по реальному ТП, а не по demo-заглушке.
 */
@FeignClient(name = "assembly-process-service",
        url = "${iktk.clients.assembly.url:http://localhost:8097}")
public interface AssemblyProcessClient {

    @GetMapping("/api/v1/assembly/processes/{processId}")
    AssemblyProcessDto getProcess(@PathVariable("processId") String processId);

    @GetMapping("/api/v1/assembly/processes/{processId}/stages")
    List<AssemblyStageDto> getStages(@PathVariable("processId") String processId);
}
