package ru.iktk.orchestrator.statemachine;

import java.util.EnumSet;
import java.util.Set;

/**
 * Состояния проекта в конечном автомате оркестратора ИКТК.
 *
 * <p>Перечень соответствует {@code final_TZ.md} (ред. 3), п. 4.1.
 * Каждое состояние описывает этап технологической подготовки производства (ТПП),
 * на котором оркестратор принимает решение о следующем действии.
 *
 * <p>Состояния делятся на три категории:
 * <ul>
 *   <li><b>Точки контроля</b> — требуют внешнего подтверждения или решения
 *       (APPROVE, RECOMMENDATION_ACCEPTED, PROCESS_ADJUSTED, OUTSOURCING_SELECTED);</li>
 *   <li><b>Автоматические</b> — оркестратор переходит дальше без участия пользователя,
 *       если нет ошибок и дефицитов;</li>
 *   <li><b>Терминальные</b> — завершение проекта.</li>
 * </ul>
 *
 * <p>Граф переходов:
 * <pre>
 *   PROJECT_INIT
 *     └─ START_GEOMETRY_INPUT → GEOMETRY_INPUT
 *          └─ GEOMETRY_UPLOADED → GEOMETRY_ANALYSIS
 *               ├─ IS_PART     → PART_PROCESS_PLANNING
 *               └─ IS_ASSEMBLY → ASSEMBLY_ANALYSIS
 *                    └─ BOM_EXTRACTED → PART_PROCESS_PLANNING
 *                         └─ PART_PROCESS_READY → TOOLING_DESIGN
 *                              └─ TOOLING_READY → EQUIPMENT_MATCHING
 *                                   ├─ EQUIPMENT_MATCHED → ASSEMBLY_PROCESS_PLANNING
 *                                   └─ EQUIPMENT_GAP     → EQUIPMENT_GAP_RESOLUTION
 *                                        └─ RECOMMENDATIONS_READY → RECOMMENDATION_REVIEW
 *                                             ├─ RECOMMENDATION_ACCEPTED → ASSEMBLY_PROCESS_PLANNING
 *                                             ├─ PROCESS_ADJUSTED        → EQUIPMENT_MATCHING
 *                                             └─ OUTSOURCING_SELECTED    → ASSEMBLY_PROCESS_PLANNING
 *                                                  └─ ASSEMBLY_PROCESS_READY → NORMING
 *                                                       └─ NORMS_READY → DOCUMENTATION
 *                                                            └─ DOCUMENTATION_READY → TECHNICAL_CONTROL
 *                                                                 ├─ CONTROL_PASSED   → COMPLETED
 *                                                                 └─ CONTROL_REJECTED → PART_PROCESS_PLANNING
 * </pre>
 */
public enum ProjectState {

    // ------------------------------------------------------------------
    // Инициализация и ввод геометрии
    // ------------------------------------------------------------------

    /** Проект создан, ожидает запуска. */
    PROJECT_INIT("Инициализация проекта", Category.AUTOMATIC),

    /** Ввод геометрии объекта изготовления. */
    GEOMETRY_INPUT("Ввод геометрии", Category.AUTOMATIC),

    /** Анализ геометрии: извлечение КТЭ, метаданных, определение типа объекта. */
    GEOMETRY_ANALYSIS("Анализ геометрии", Category.AUTOMATIC),

    // ------------------------------------------------------------------
    // Анализ сборочной единицы
    // ------------------------------------------------------------------

    /** Анализ сборочной единицы и извлечение BOM. */
    ASSEMBLY_ANALYSIS("Анализ сборочной единицы", Category.AUTOMATIC),

    // ------------------------------------------------------------------
    // Проектирование ТП и оснастки
    // ------------------------------------------------------------------

    /** Проектирование ТП для каждой детали. */
    PART_PROCESS_PLANNING("Проектирование ТП деталей", Category.POINT_OF_CONTROL),

    /** Проектирование оснастки. */
    TOOLING_DESIGN("Проектирование оснастки", Category.POINT_OF_CONTROL),

    // ------------------------------------------------------------------
    // Оборудование и рекомендации
    // ------------------------------------------------------------------

    /** Сопоставление требований ТП с парком оборудования. */
    EQUIPMENT_MATCHING("Сопоставление с оборудованием", Category.AUTOMATIC),

    /** Разрешение дефицита оборудования: gap-анализ и формирование рекомендаций. */
    EQUIPMENT_GAP_RESOLUTION("Разрешение дефицита оборудования", Category.AUTOMATIC),

    /** Просмотр рекомендаций и принятие решения технологом. */
    RECOMMENDATION_REVIEW("Просмотр рекомендаций (точка контроля)", Category.POINT_OF_CONTROL),

    // ------------------------------------------------------------------
    // ТП сборки, нормирование, документация
    // ------------------------------------------------------------------

    /** Проектирование ТП сборки на основе BOM и связей. */
    ASSEMBLY_PROCESS_PLANNING("Проектирование ТП сборки", Category.POINT_OF_CONTROL),

    /** Расчёт норм времени и расхода материалов. */
    NORMING("Нормирование (точка контроля)", Category.POINT_OF_CONTROL),

    /** Формирование комплекта технологической документации. */
    DOCUMENTATION("Формирование документации", Category.AUTOMATIC),

    /** Технический контроль. */
    TECHNICAL_CONTROL("Технический контроль", Category.POINT_OF_CONTROL),

    // ------------------------------------------------------------------
    // Терминальное состояние
    // ------------------------------------------------------------------

    /** Проект завершён. */
    COMPLETED("Завершено", Category.TERMINAL);

    // ------------------------------------------------------------------
    // Поля и конструктор
    // ------------------------------------------------------------------

    private final String displayName;
    private final Category category;

    ProjectState(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Category getCategory() {
        return category;
    }

    // ------------------------------------------------------------------
    // Категории состояний
    // ------------------------------------------------------------------

    /**
     * Категория состояния — определяет политику оркестратора.
     */
    public enum Category {
        /** Переход выполняется автоматически, без участия пользователя. */
        AUTOMATIC,
        /** Требуется решение пользователя (технолога, руководителя). */
        POINT_OF_CONTROL,
        /** Терминальное состояние — проект завершён. */
        TERMINAL
    }

    // ------------------------------------------------------------------
    // Утилитные методы
    // ------------------------------------------------------------------

    /** Проверка: является ли состояние точкой контроля. */
    public boolean isPointOfControl() {
        return category == Category.POINT_OF_CONTROL;
    }

    /** Проверка: автоматическое ли состояние. */
    public boolean isAutomatic() {
        return category == Category.AUTOMATIC;
    }

    /** Проверка: терминальное ли состояние. */
    public boolean isTerminal() {
        return category == Category.TERMINAL;
    }

    /** Точки контроля. */
    public static Set<ProjectState> pointsOfControl() {
        return EnumSet.of(
                PART_PROCESS_PLANNING,
                TOOLING_DESIGN,
                RECOMMENDATION_REVIEW,
                ASSEMBLY_PROCESS_PLANNING,
                NORMING,
                TECHNICAL_CONTROL
        );
    }

    /** Автоматические состояния. */
    public static Set<ProjectState> automaticStates() {
        return EnumSet.of(
                PROJECT_INIT,
                GEOMETRY_INPUT,
                GEOMETRY_ANALYSIS,
                ASSEMBLY_ANALYSIS,
                EQUIPMENT_MATCHING,
                EQUIPMENT_GAP_RESOLUTION,
                DOCUMENTATION
        );
    }

    /** Начальное состояние. */
    public static ProjectState initial() {
        return PROJECT_INIT;
    }

    /** Терминальное состояние. */
    public static ProjectState terminal() {
        return COMPLETED;
    }

    @Override
    public String toString() {
        return name() + " (" + displayName + ")";
    }
}
