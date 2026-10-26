package ru.iktk.modules.machining.dto;

import java.util.List;

    /** Установ и схема базирования. */
    public class Setup {
        private final int order;
        private final String name;
        private final String scheme;
        private final List<String> fixtures;
        private final List<String> surfaces;

        private Setup(int order, String name, String scheme,
                      List<String> fixtures, List<String> surfaces) {
            this.order = order;
            this.name = name;
            this.scheme = scheme;
            this.fixtures = fixtures == null ? List.of() : fixtures;
            this.surfaces = surfaces == null ? List.of() : surfaces;
        }

        public int getOrder() { return order; }
        public String getName() { return name; }
        public String getScheme() { return scheme; }
        public List<String> getFixtures() { return fixtures; }
        public List<String> getSurfaces() { return surfaces; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int order;
            private String name;
            private String scheme;
            private List<String> fixtures;
            private List<String> surfaces;

            public Builder order(int v) { this.order = v; return this; }
            public Builder name(String v) { this.name = v; return this; }
            public Builder scheme(String v) { this.scheme = v; return this; }
            public Builder fixtures(List<String> v) { this.fixtures = v; return this; }
            public Builder surfaces(List<String> v) { this.surfaces = v; return this; }

            public Setup build() {
                return new Setup(order, name, scheme, fixtures, surfaces);
            }
        }
    }
