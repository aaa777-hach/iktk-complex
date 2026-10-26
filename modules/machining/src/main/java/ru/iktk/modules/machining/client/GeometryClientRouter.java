package ru.iktk.modules.machining.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.GeometryData;

/**
 * Роутер геометрии: пробует реальный integration через Feign,
 * при ошибке — fallback на stub.
 *
 * <p>Инъектируется в {@code MachiningProcessService} вместо {@link GeometryClient}.
 */
@Component
public class GeometryClientRouter {

    private static final Logger log = LoggerFactory.getLogger(GeometryClientRouter.class);

    private final GeometryClient feign;
    private final GeometryClientStub fallback;

    public GeometryClientRouter(GeometryClient feign, GeometryClientStub fallback) {
        this.feign = feign;
        this.fallback = fallback;
    }

    public GeometryData fetchGeometry(String projectId, String objectRef) {
        try {
            GeometryData data = feign.fetchGeometry(projectId, objectRef);
            if (data != null) {
                log.info("GeometryClientRouter: integration OK for projectId={}, objectRef={}",
                        projectId, objectRef);
                return data;
            }
            log.warn("GeometryClientRouter: integration returned null for {}", objectRef);
        } catch (Exception e) {
            log.warn("GeometryClientRouter: integration failed for projectId={}, objectRef={}: {} — fallback to stub",
                    projectId, objectRef, e.getMessage());
        }
        return fallback.fetchGeometry(projectId, objectRef);
    }
}