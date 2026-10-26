package ru.iktk.modules.machining.model;

import java.math.BigDecimal;

    /** Спецификация инструмента в карте наладки. */
    public class ToolSpec {
        private final int toolNumber;
        private final String operationName;
        private final String type;
        private final BigDecimal diameter;
        private final String material;
        private final String coating;
        private final BigDecimal cuttingSpeed;
        private final BigDecimal feed;
        private final BigDecimal depthOfCut;
        private final BigDecimal spindleSpeed;

        private ToolSpec(int toolNumber, String operationName, String type,
                         BigDecimal diameter, String material, String coating,
                         BigDecimal cuttingSpeed, BigDecimal feed,
                         BigDecimal depthOfCut, BigDecimal spindleSpeed) {
            this.toolNumber = toolNumber;
            this.operationName = operationName;
            this.type = type;
            this.diameter = diameter;
            this.material = material;
            this.coating = coating;
            this.cuttingSpeed = cuttingSpeed;
            this.feed = feed;
            this.depthOfCut = depthOfCut;
            this.spindleSpeed = spindleSpeed;
        }

        public int getToolNumber() { return toolNumber; }
        public String getOperationName() { return operationName; }
        public String getType() { return type; }
        public BigDecimal getDiameter() { return diameter; }
        public String getMaterial() { return material; }
        public String getCoating() { return coating; }
        public BigDecimal getCuttingSpeed() { return cuttingSpeed; }
        public BigDecimal getFeed() { return feed; }
        public BigDecimal getDepthOfCut() { return depthOfCut; }
        public BigDecimal getSpindleSpeed() { return spindleSpeed; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int toolNumber;
            private String operationName;
            private String type;
            private BigDecimal diameter;
            private String material;
            private String coating;
            private BigDecimal cuttingSpeed;
            private BigDecimal feed;
            private BigDecimal depthOfCut;
            private BigDecimal spindleSpeed;

            public Builder toolNumber(int v) { this.toolNumber = v; return this; }
            public Builder operationName(String v) { this.operationName = v; return this; }
            public Builder type(String v) { this.type = v; return this; }
            public Builder diameter(BigDecimal v) { this.diameter = v; return this; }
            public Builder material(String v) { this.material = v; return this; }
            public Builder coating(String v) { this.coating = v; return this; }
            public Builder cuttingSpeed(BigDecimal v) { this.cuttingSpeed = v; return this; }
            public Builder feed(BigDecimal v) { this.feed = v; return this; }
            public Builder depthOfCut(BigDecimal v) { this.depthOfCut = v; return this; }
            public Builder spindleSpeed(BigDecimal v) { this.spindleSpeed = v; return this; }

            public ToolSpec build() {
                return new ToolSpec(toolNumber, operationName, type, diameter,
                        material, coating, cuttingSpeed, feed, depthOfCut, spindleSpeed);
            }
        }
    }
