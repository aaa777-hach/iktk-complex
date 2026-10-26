package ru.iktk.modules.machining.dto;

import java.util.List;

    /** Отчёт о технологичности. */
    public class ManufacturabilityReport {
        private final boolean passed;
        private final List<String> issues;
        private final List<String> warnings;

        public ManufacturabilityReport(boolean passed,
                                       List<String> issues,
                                       List<String> warnings) {
            this.passed = passed;
            this.issues = issues == null ? List.of() : issues;
            this.warnings = warnings == null ? List.of() : warnings;
        }

        public boolean isPassed() { return passed; }
        public List<String> getIssues() { return issues; }
        public List<String> getWarnings() { return warnings; }
    }
