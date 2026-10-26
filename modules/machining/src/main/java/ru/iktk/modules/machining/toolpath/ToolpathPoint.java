package ru.iktk.modules.machining.toolpath;

import java.math.BigDecimal;

    /** Точка траектории. */
    public class ToolpathPoint {
        private final BigDecimal x;
        private final BigDecimal y;
        private final BigDecimal z;
        private final String type;
        private final BigDecimal feed;

        public ToolpathPoint(BigDecimal x, BigDecimal y, BigDecimal z,
                             String type, BigDecimal feed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.feed = feed;
        }

        public BigDecimal getX() { return x; }
        public BigDecimal getY() { return y; }
        public BigDecimal getZ() { return z; }
        public String getType() { return type; }
        public BigDecimal getFeed() { return feed; }
    }
