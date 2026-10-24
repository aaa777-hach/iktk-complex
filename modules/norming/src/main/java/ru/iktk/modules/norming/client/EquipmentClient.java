package ru.iktk.modules.norming.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "equipment-service", url = "${iktk.clients.equipment.url:http://localhost:8094}")
public interface EquipmentClient {
}
