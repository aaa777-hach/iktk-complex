package ru.iktk.modules.machining.dto;

import java.util.Map;

    /** Информация о станке. */
    public class EquipmentInfo {
        private final String id;
        private final String model;
        private final String cncSystem;
        private final int axes;
        private final Map<String, Object> parameters;

        public EquipmentInfo(String id, String model, String cncSystem,
                             int axes, Map<String, Object> parameters) {
            this.id = id;
            this.model = model;
            this.cncSystem = cncSystem;
            this.axes = axes;
            this.parameters = parameters == null ? Map.of() : parameters;
        }

        public String getId() { return id; }
        public String getModel() { return model; }
        public String getCncSystem() { return cncSystem; }
        public int getAxes() { return axes; }
        public Map<String, Object> getParameters() { return parameters; }
    }
