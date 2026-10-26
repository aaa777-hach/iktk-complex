package ru.iktk.modules.machining.toolpath;

import java.util.List;

    /** Траектория инструмента. */
    public class Toolpath {
        private final List<ToolpathPoint> points;

        public Toolpath(List<ToolpathPoint> points) {
            this.points = points == null ? List.of() : points;
        }

        public List<ToolpathPoint> getPoints() { return points; }
    }
