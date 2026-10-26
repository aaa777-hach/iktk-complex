package ru.iktk.modules.machining.validator;

import java.util.List;

    /** Результат валидации G-кода. */
    public class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid,
                                List<String> errors,
                                List<String> warnings) {
            this.valid = valid;
            this.errors = errors == null ? List.of() : errors;
            this.warnings = warnings == null ? List.of() : warnings;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }

        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }
    }
