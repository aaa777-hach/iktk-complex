package ru.iktk.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.orchestrator.model.ProjectRuntime;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий runtime-состояний проектов ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code project_state} (PostgreSQL),
 * которая хранит текущее состояние каждого проекта, версию и метаданные.
 *
 * <p>Ключевые сценарии использования:
 * <ul>
 *   <li>загрузка runtime-состояния проекта по {@code projectId} — для оркестратора;</li>
 *   <li>сохранение нового состояния после каждого перехода автомата;</li>
 *   <li>выборка проектов по состоянию — для мониторинга и админ-панели;</li>
 *   <li>поиск версии для отката — {@code findByProjectIdAndVersion};</li>
 *   <li>выборка «зависших» проектов — для плановых задач
 *       ({@code @EnableScheduling} в {@code OrchestratorApplication}).</li>
 * </ul>
 *
 * <p>Схема таблицы (см. {@code database/migrations/V2__project_tables.sql}):
 * <pre>
 *   project_state (
 *     id           UUID PRIMARY KEY,
 *     project_id   VARCHAR(64) UNIQUE NOT NULL,
 *     project_name VARCHAR(255) NOT NULL,
 *     requested_by VARCHAR(128),
 *     state        VARCHAR(64) NOT NULL,
 *     version      VARCHAR(64),
 *     created_at   TIMESTAMPTZ NOT NULL,
 *     updated_at   TIMESTAMPTZ NOT NULL,
 *     payload      JSONB
 *   )
 * </pre>
 */
@Repository
public interface ProjectStateRepository extends JpaRepository<ProjectRuntime, String> {

    // ------------------------------------------------------------------
    // Основные выборки
    // ------------------------------------------------------------------

    /**
     * Найти runtime-состояние проекта по его идентификатору.
     *
     * @param projectId идентификатор проекта
     * @return {@link Optional} с runtime-состоянием
     */
    Optional<ProjectRuntime> findByProjectId(String projectId);

    /**
     * Найти runtime-состояние проекта по идентификатору и версии.
     * Используется при откате ({@code OrchestrationService#rollback}).
     *
     * @param projectId идентификатор проекта
     * @param version   версия проекта
     * @return {@link Optional} с runtime-состоянием нужной версии
     */
    Optional<ProjectRuntime> findByProjectIdAndVersion(String projectId, String version);

    /**
     * Проверить существование проекта.
     *
     * @param projectId идентификатор проекта
     * @return {@code true}, если проект существует
     */
    boolean existsByProjectId(String projectId);

    // ------------------------------------------------------------------
    // Выборки по состоянию
    // ------------------------------------------------------------------

    /**
     * Все проекты в указанном состоянии.
     *
     * @param state состояние проекта
     * @return список runtime-состояний
     */
    List<ProjectRuntime> findByState(ProjectState state);

    /**
     * Все проекты в одном из указанных состояний.
     *
     * @param states набор состояний
     * @return список runtime-состояний
     */
    List<ProjectRuntime> findByStateIn(List<ProjectState> states);

    /**
     * Количество проектов в указанном состоянии.
     *
     * @param state состояние проекта
     * @return число проектов
     */
    long countByState(ProjectState state);

    // ------------------------------------------------------------------
    // Выборки по времени
    // ------------------------------------------------------------------

    /**
     * Проекты, обновлённые после указанного момента.
     * Используется для мониторинга активности.
     *
     * @param since нижняя граница по времени
     * @return список runtime-состояний
     */
    List<ProjectRuntime> findByUpdatedAtAfter(Instant since);

    /**
     * Проекты в указанном состоянии, не обновлявшиеся дольше указанного момента.
     * Используется плановой задачей для поиска «зависших» проектов.
     *
     * @param state    состояние проекта
     * @param before   верхняя граница по времени обновления
     * @return список runtime-состояний
     */
    @Query("""
            SELECT p FROM ProjectRuntime p
            WHERE p.state = :state
              AND p.updatedAt < :before
            ORDER BY p.updatedAt ASC
            """)
    List<ProjectRuntime> findStaleByState(@Param("state") ProjectState state,
                                          @Param("before") Instant before);

    // ------------------------------------------------------------------
    // Обновления
    // ------------------------------------------------------------------

    /**
     * Обновить состояние и метку времени проекта.
     * Используется оркестратором при каждом переходе
     * (альтернатива полному {@code save} для оптимизации).
     *
     * @param projectId идентификатор проекта
     * @param state     новое состояние
     * @param updatedAt момент обновления
     * @return число обновлённых строк (0 или 1)
     */
    @Modifying
    @Query("""
            UPDATE ProjectRuntime p
               SET p.state = :state,
                   p.updatedAt = :updatedAt
             WHERE p.projectId = :projectId
            """)
    int updateState(@Param("projectId") String projectId,
                    @Param("state") ProjectState state,
                    @Param("updatedAt") Instant updatedAt);

    /**
     * Обновить версию проекта.
     * Используется при фиксации новой версии после ключевых этапов.
     *
     * @param projectId идентификатор проекта
     * @param version   новая версия
     * @param updatedAt момент обновления
     * @return число обновлённых строк (0 или 1)
     */
    @Modifying
    @Query("""
            UPDATE ProjectRuntime p
               SET p.version = :version,
                   p.updatedAt = :updatedAt
             WHERE p.projectId = :projectId
            """)
    int updateVersion(@Param("projectId") String projectId,
                      @Param("version") String version,
                      @Param("updatedAt") Instant updatedAt);

    // ------------------------------------------------------------------
    // Удаление
    // ------------------------------------------------------------------

    /**
     * Удалить проект по идентификатору.
     * Физическое удаление запрещено регламентом — рекомендуется архивация.
     * Метод оставлен для административных задач.
     *
     * @param projectId идентификатор проекта
     * @return число удалённых строк (0 или 1)
     */
    @Modifying
    @Query("DELETE FROM ProjectRuntime p WHERE p.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);

    // ------------------------------------------------------------------
    // Проекции (для отчётов и UI)
    // ------------------------------------------------------------------

    /**
     * Список идентификаторов проектов в указанном состоянии.
     * Лёгкая проекция — без загрузки полного runtime-состояния.
     *
     * @param state состояние проекта
     * @return список projectId
     */
    @Query("SELECT p.projectId FROM ProjectRuntime p WHERE p.state = :state")
    List<String> findProjectIdsByState(@Param("state") ProjectState state);

    /**
     * Распределение проектов по состояниям.
     * Возвращает пары (state, count) для дашборда.
     *
     * @return список пар состояние/количество
     */
    @Query("""
            SELECT p.state, COUNT(p)
              FROM ProjectRuntime p
             GROUP BY p.state
             ORDER BY p.state
            """)
    List<Object[]> countByStateGrouped();
}