package ru.iktk.modules.machining.postprocessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр постпроцессоров.
 *
 * <p>Поиск по модели станка (подстрока) и по имени.
 */
@Component
public class PostprocessorRegistry {

    private static final Logger log = LoggerFactory.getLogger(PostprocessorRegistry.class);

    private final Map<String, Postprocessor> byName = new ConcurrentHashMap<>();

    public PostprocessorRegistry(List<Postprocessor> postprocessors) {
        if (postprocessors != null) {
            for (Postprocessor pp : postprocessors) {
                byName.put(pp.getName(), pp);
            }
        }
        log.info("PostprocessorRegistry: зарегистрировано {}", byName.keySet());
    }

    /** Найти постпроцессор по модели станка. */
    public Postprocessor find(String equipmentModel) {
        if (equipmentModel == null || byName.isEmpty()) {
            return null;
        }
        String m = equipmentModel.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Postprocessor> e : byName.entrySet()) {
            if (m.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Найти постпроцессор по имени. */
    public Postprocessor findByName(String name) {
        return name == null ? null : byName.get(name);
    }
}