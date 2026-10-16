package ru.iktk.auth.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.auth.model.RoleName;
import ru.iktk.auth.model.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий пользователей сервиса аутентификации ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code users} (PostgreSQL), которая
 * хранит учётные записи пользователей, их роли и состояние.
 *
 * <p>Схема таблицы (ориентир):
 * <pre>
 *   users (
 *     id                UUID PRIMARY KEY,
 *     username          VARCHAR(128) UNIQUE NOT NULL,
 *     password_hash     VARCHAR(255) NOT NULL,
 *     display_name      VARCHAR(255),
 *     email             VARCHAR(255) UNIQUE,
 *     enterprise_code   VARCHAR(64),
 *     department        VARCHAR(128),
 *     enabled           BOOLEAN NOT NULL DEFAULT TRUE,
 *     locked            BOOLEAN NOT NULL DEFAULT FALSE,
 *     failed_attempts   INT NOT NULL DEFAULT 0,
 *     last_login_at     TIMESTAMPTZ,
 *     password_changed_at TIMESTAMPTZ,
 *     created_at        TIMESTAMPTZ NOT NULL,
 *     updated_at        TIMESTAMPTZ NOT NULL
 *   )
 * </pre>
 *
 * <p>Связь с ролями — через таблицу {@code user_roles} (Many-to-Many).
 * Роли и их наследование определяются в {@code Role} и {@code RbacService}.
 *
 * <p>Ключевые сценарии использования:
 * <ul>
 *   <li>поиск пользователя по {@code username} или {@code email} — для входа;</li>
 *   <li>поиск по {@code id} — для операций RBAC/ABAC;</li>
 *   <li>поиск по роли — для административных задач и рассылок;</li>
 *   <li>выборка заблокированных / неактивных — для аудита;</li>
 *   <li>поиск «забытых» учёток — плановые задачи (проверка срока действия пароля).</li>
 * </ul>
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // ------------------------------------------------------------------
    // Основные выборки
    // ------------------------------------------------------------------

    /**
     * Найти пользователя по логину.
     *
     * @param username логин пользователя
     * @return {@link Optional} с пользователем
     */
    Optional<User> findByUsername(String username);

    /**
     * Найти пользователя по email.
     *
     * @param email email пользователя
     * @return {@link Optional} с пользователем
     */
    Optional<User> findByEmail(String email);

    /**
     * Найти пользователя по логину или email.
     * Используется при входе, когда пользователь может ввести любой из них.
     *
     * @param username логин
     * @param email    email
     * @return {@link Optional} с пользователем
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * Проверить существование пользователя по логину.
     *
     * @param username логин
     * @return {@code true}, если пользователь существует
     */
    boolean existsByUsername(String username);

    /**
     * Проверить существование пользователя по email.
     *
     * @param email email
     * @return {@code true}, если email занят
     */
    boolean existsByEmail(String email);

    // ------------------------------------------------------------------
    // Выборки по ролям
    // ------------------------------------------------------------------

    /**
     * Найти пользователей с указанной ролью.
     *
     * @param roleName имя роли
     * @return список пользователей
     */
    @Query("""
            SELECT DISTINCT u FROM User u
              JOIN u.roles r
             WHERE r.name = :roleName
             ORDER BY u.username
            """)
    List<User> findByRole(@Param("roleName") RoleName roleName);

    /**
     * Найти пользователей с любой из указанных ролей.
     *
     * @param roleNames набор ролей
     * @return список пользователей
     */
    @Query("""
            SELECT DISTINCT u FROM User u
              JOIN u.roles r
             WHERE r.name IN :roleNames
             ORDER BY u.username
            """)
    List<User> findByRolesIn(@Param("roleNames") List<RoleName> roleNames);

    /**
     * Постраничная выборка пользователей с указанной ролью.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
              JOIN u.roles r
             WHERE r.name = :roleName
            """)
    Page<User> findByRole(@Param("roleName") RoleName roleName, Pageable pageable);

    /**
     * Количество пользователей с указанной ролью.
     */
    @Query("""
            SELECT COUNT(DISTINCT u) FROM User u
              JOIN u.roles r
             WHERE r.name = :roleName
            """)
    long countByRole(@Param("roleName") RoleName roleName);

    // ------------------------------------------------------------------
    // Выборки по предприятию и подразделению
    // ------------------------------------------------------------------

    /**
     * Найти пользователей указанного предприятия.
     */
    List<User> findByEnterpriseCode(String enterpriseCode);

    /**
     * Найти пользователей указанного подразделения.
     */
    List<User> findByDepartment(String department);

    /**
     * Найти пользователей предприятия и подразделения.
     */
    List<User> findByEnterpriseCodeAndDepartment(String enterpriseCode, String department);

    /**
     * Найти пользователей предприятия с указанной ролью.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
              JOIN u.roles r
             WHERE u.enterpriseCode = :enterpriseCode
               AND r.name = :roleName
             ORDER BY u.username
            """)
    List<User> findByEnterpriseAndRole(@Param("enterpriseCode") String enterpriseCode,
                                       @Param("roleName") RoleName roleName);

    // ------------------------------------------------------------------
    // Выборки по состоянию учётной записи
    // ------------------------------------------------------------------

    /**
     * Найти всех активных пользователей.
     */
    List<User> findByEnabledTrue();

    /**
     * Найти всех заблокированных пользователей.
     */
    List<User> findByLockedTrue();

    /**
     * Найти активных и незаблокированных пользователей.
     */
    List<User> findByEnabledTrueAndLockedFalse();

    /**
     * Найти пользователей, у которых число неудачных попыток входа
     * превышает порог.
     *
     * @param threshold порог неудачных попыток
     * @return список пользователей
     */
    List<User> findByFailedAttemptsGreaterThanEqual(int threshold);

    // ------------------------------------------------------------------
    // Выборки по времени
    // ------------------------------------------------------------------

    /**
     * Пользователи, не входившие в систему с указанного момента.
     * Используется плановой задачей для деактивации «спящих» учёток.
     */
    @Query("""
            SELECT u FROM User u
             WHERE u.lastLoginAt IS NULL
                OR u.lastLoginAt < :since
             ORDER BY u.lastLoginAt ASC NULLS FIRST
            """)
    List<User> findInactiveSince(@Param("since") Instant since);

    /**
     * Пользователи, у которых срок действия пароля истёк.
     *
     * @param before верхняя граница по времени смены пароля
     * @return список пользователей
     */
    @Query("""
            SELECT u FROM User u
             WHERE u.passwordChangedAt IS NULL
                OR u.passwordChangedAt < :before
             ORDER BY u.passwordChangedAt ASC NULLS FIRST
            """)
    List<User> findWithExpiredPassword(@Param("before") Instant before);

    /**
     * Пользователи, созданные после указанного момента.
     */
    List<User> findByCreatedAtAfter(Instant since);

    // ------------------------------------------------------------------
    // Обновления
    // ------------------------------------------------------------------

    /**
     * Обновить метку последнего входа.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.lastLoginAt = :when,
                   u.failedAttempts = 0,
                   u.updatedAt = :when
             WHERE u.id = :userId
            """)
    int updateLastLogin(@Param("userId") String userId,
                        @Param("when") Instant when);

    /**
     * Увеличить счётчик неудачных попыток входа.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.failedAttempts = u.failedAttempts + 1,
                   u.updatedAt = :when
             WHERE u.id = :userId
            """)
    int incrementFailedAttempts(@Param("userId") String userId,
                                @Param("when") Instant when);

    /**
     * Сбросить счётчик неудачных попыток входа.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.failedAttempts = 0,
                   u.updatedAt = :when
             WHERE u.id = :userId
            """)
    int resetFailedAttempts(@Param("userId") String userId,
                            @Param("when") Instant when);

    /**
     * Заблокировать пользователя.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.locked = TRUE,
                   u.updatedAt = :when
             WHERE u.id = :userId
            """)
    int lock(@Param("userId") String userId,
             @Param("when") Instant when);

    /**
     * Разблокировать пользователя.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.locked = FALSE,
                   u.failedAttempts = 0,
                   u.updatedAt = :when
             WHERE u.id = :userId
            """)
    int unlock(@Param("userId") String userId,
               @Param("when") Instant when);

    /**
     * Включить/выключить учётную запись.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.enabled = :enabled,
                   u.updatedAt = :when
             WHERE u.id = :userId
            """)
    int setEnabled(@Param("userId") String userId,
                   @Param("enabled") boolean enabled,
                   @Param("when") Instant when);

    /**
     * Обновить хэш пароля и метку смены.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.passwordHash = :passwordHash,
                   u.passwordChangedAt = :when,
                   u.updatedAt = :when
             WHERE u.id = :userId
            """)
    int updatePassword(@Param("userId") String userId,
                       @Param("passwordHash") String passwordHash,
                       @Param("when") Instant when);

    // ------------------------------------------------------------------
    // Удаление
    // ------------------------------------------------------------------

    /**
     * Удалить пользователя по логину.
     * Физическое удаление рекомендуется заменять деактивацией
     * ({@link #setEnabled}); метод оставлен для административных задач.
     */
    @Modifying
    @Query("DELETE FROM User u WHERE u.username = :username")
    int deleteByUsername(@Param("username") String username);

    // ------------------------------------------------------------------
    // Проекции и агрегаты
    // ------------------------------------------------------------------

    /**
     * Список логинов всех активных пользователей.
     * Лёгкая проекция для рассылок и административных задач.
     */
    @Query("SELECT u.username FROM User u WHERE u.enabled = TRUE ORDER BY u.username")
    List<String> findAllActiveUsernames();

    /**
     * Распределение пользователей по предприятиям.
     */
    @Query("""
            SELECT u.enterpriseCode, COUNT(u)
              FROM User u
             WHERE u.enterpriseCode IS NOT NULL
             GROUP BY u.enterpriseCode
             ORDER BY u.enterpriseCode
            """)
    List<Object[]> countByEnterpriseGrouped();

    /**
     * Распределение пользователей по ролям.
     */
    @Query("""
            SELECT r.name, COUNT(DISTINCT u)
              FROM User u
              JOIN u.roles r
             GROUP BY r.name
             ORDER BY r.name
            """)
    List<Object[]> countByRoleGrouped();
}