package ru.iktk.modules.machining.client;

import ru.iktk.modules.machining.dto.GeometryData;

/**
 * Клиент к integration (CAD-геометрия).
 *
 * <p>Реальный endpoint {@code GET /api/v1/geometry/{projectId}?objectRef=...}
 * существует и возвращает {@code Map<String,Object>}. Десериализация в
 * {@link GeometryData} пока невозможна: у DTO нет no-arg конструктора и
 * {@code @JsonCreator}, а структура JSON не зафиксирована. До C-real
 * используется {@link GeometryClientStub}.
 *
 * <p>Когда контракт будет зафиксирован — заменить stub на {@code @FeignClient}:
 * <pre>
 * &#64;FeignClient(name = "integration", url = "${iktk.machining.integration.url:http://localhost:8092}")
 * public interface GeometryClient {
 *     &#64;GetMapping("/api/v1/geometry/{projectId}")
 *     GeometryData fetchGeometry(&#64;PathVariable String projectId,
 *                                &#64;RequestParam String objectRef);
 * }
 * </pre>
 */
public interface GeometryClient {

    GeometryData fetchGeometry(String projectId, String objectRef);
}