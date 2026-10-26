package ru.iktk.modules.machining.model;

import java.util.List;
import java.util.Map;

    /** Карта наладки. */
    public class SetupSheet {
        private final String equipmentModel;
        private final String postprocessor;
        private final Map<String, String> zeroPoints;
        private final List<ToolSpec> tools;
        private final String notes;

        private SetupSheet(String equipmentModel, String postprocessor,
                           Map<String, String> zeroPoints, List<ToolSpec> tools,
                           String notes) {
            this.equipmentModel = equipmentModel;
            this.postprocessor = postprocessor;
            this.zeroPoints = zeroPoints == null ? Map.of() : zeroPoints;
            this.tools = tools == null ? List.of() : tools;
            this.notes = notes;
        }

        public String getEquipmentModel() { return equipmentModel; }
        public String getPostprocessor() { return postprocessor; }
        public Map<String, String> getZeroPoints() { return zeroPoints; }
        public List<ToolSpec> getTools() { return tools; }
        public String getNotes() { return notes; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String equipmentModel;
            private String postprocessor;
            private Map<String, String> zeroPoints;
            private List<ToolSpec> tools;
            private String notes;

            public Builder equipmentModel(String v) { this.equipmentModel = v; return this; }
            public Builder postprocessor(String v) { this.postprocessor = v; return this; }
            public Builder zeroPoints(Map<String, String> v) { this.zeroPoints = v; return this; }
            public Builder tools(List<ToolSpec> v) { this.tools = v; return this; }
            public Builder notes(String v) { this.notes = v; return this; }

            public SetupSheet build() {
                return new SetupSheet(equipmentModel, postprocessor,
                        zeroPoints, tools, notes);
            }
        }
    }
