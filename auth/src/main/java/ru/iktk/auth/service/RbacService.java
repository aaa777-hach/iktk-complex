package ru.iktk.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.auth.exception.AuthException;
import ru.iktk.auth.model.Permission;
import ru.iktk.auth.model.Role;
import ru.iktk.auth.model.RoleName;
import ru.iktk.auth.model.User;
import ru.iktk.auth.repository.PermissionRepository;
import ru.iktk.auth.repository.RoleRepository;
import ru.iktk.auth.repository.UserRepository;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис ролевой модели доступа (RBAC) ИКТК.
 *
 * <p>RBAC (Role-Based Access Control) — модель, в которой права доступа
 * назначаются ролям, а роли — пользователям. Сервис отвечает за:
 * <ul>
 *   <li>проверку наличия роли у пользователя;</li>
 *   <li>проверку разрешений (permissions) на уровне ролей;</li>
 *   <li>выдачу и отзыв ролей;</li>
 *   <li>получение полного набора ролей и разрешений пользователя;</li>
 *   <li>проверку иерархии ролей (например, ADMIN наследует ENGINEER).</li>
 * </ul>
 *
 * <p>Базовые роли ИКТК (см. {@link RoleName}):
 * <pre>
 *   ADMIN        — полный доступ, управление пользователями и настройками;
 *   MANAGER      — руководитель: аналитика, утверждение, точки контроля;
 *   ENGINEER     — технолог/конструктор: создание и правка проектов и ТП;
 *   NORMER       — нормировщик: работа с нормами времени и материалов;
 *   OPERATOR     — оператор: просмотр, запуск расчётов;
 *   VIEWER       — только просмотр;
 *   INTEGRATION  — служебная роль для внешних сервисов (CAD/CAM, MES/ERP).
 * </pre>
 *
 * <p>RBAC работает совместно с ABAC ({@link AbacService}) — итоговое решение
 * о доступе принимается как пересечение: роль должна разрешать операцию,
 * а атрибуты (проект, гриф, этап) — не запрещать её.
 */
@Service
public class RbacService {

    private static final Logger log = LoggerFactory.getLogger(RbacService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RbacService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    // ------------------------------------------------------------------
    // Проверка ролей и разрешений
    // ------------------------------------------------------------------

    /**
     * Проверить, обладает ли пользователь указанной ролью.
     *
     * @param userId   идентификатор пользователя
     * @param roleName имя роли
     * @return {@code true}, если роль есть у пользователя
     */
    @Transactional(readOnly = true)
    public boolean hasRole(String userId, RoleName roleName) {
        if (userId == null || roleName == null) {
            return false;
        }
        return getUserRoles(userId).contains(roleName);
    }

    /**
     * Проверить, обладает ли пользователь хотя бы одной из указанных ролей.
     */
    @Transactional(readOnly = true)
    public boolean hasAnyRole(String userId, RoleName... roleNames) {
        if (userId == null || roleNames == null || roleNames.length == 0) {
            return false;
        }
        Set<RoleName> userRoles = getUserRoles(userId);
        for (RoleName r : roleNames) {
            if (userRoles.contains(r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверить, обладает ли пользователь всеми указанными ролями.
     */
    @Transactional(readOnly = true)
    public boolean hasAllRoles(String userId, RoleName... roleNames) {
        if (userId == null || roleNames == null || roleNames.length == 0) {
            return false;
        }
        Set<RoleName> userRoles = getUserRoles(userId);
        for (RoleName r : roleNames) {
            if (!userRoles.contains(r)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Проверить, обладает ли пользователь разрешением.
     *
     * <p>Разрешение — атомарная операция (например, {@code PROJECT_CREATE},
     * {@code TP_APPROVE}, {@code EQUIPMENT_EDIT}). Назначается ролям.
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(String userId, String permissionCode) {
        if (userId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        Set<String> permissions = getUserPermissions(userId);
        return permissions.contains(permissionCode);
    }

    /**
     * Проверить разрешение и выбросить {@link AccessDeniedException}, если нет.
     * Удобно для использования в бизнес-логике.
     */
    @Transactional(readOnly = true)
    public void requirePermission(String userId, String permissionCode) {
        if (!hasPermission(userId, permissionCode)) {
            log.warn("RBAC: отказ в доступе, userId={}, permission={}", userId, permissionCode);
            throw new AccessDeniedException(
                    "Пользователь " + userId + " не имеет разрешения " + permissionCode);
        }
    }

    /**
     * Проверить роль и выбросить {@link AccessDeniedException}, если нет.
     */
    @Transactional(readOnly = true)
    public void requireRole(String userId, RoleName roleName) {
        if (!hasRole(userId, roleName)) {
            log.warn("RBAC: отказ в доступе, userId={}, role={}", userId, roleName);
            throw new AccessDeniedException(
                    "Пользователь " + userId + " не имеет роли " + roleName);
        }
    }

    // ------------------------------------------------------------------
    // Получение ролей и разрешений
    // ------------------------------------------------------------------

    /**
     * Получить полный набор ролей пользователя с учётом иерархии.
     *
     * <p>Например, если у пользователя роль {@code ADMIN}, а {@code ADMIN}
     * наследует {@code MANAGER} и {@code ENGINEER}, то в результате будут
     * все три роли.
     */
    @Transactional(readOnly = true)
    public Set<RoleName> getUserRoles(String userId) {
        User user = findUser(userId);
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return Collections.emptySet();
        }

        Set<RoleName> result = EnumSet.noneOf(RoleName.class);
        for (Role role : user.getRoles()) {
            result.add(role.getName());
            for (Role inherited : role.getInheritedRoles()) { result.add(inherited.getName()); }
        }
        return result;
    }

    /**
     * Получить полный набор разрешений пользователя.
     */
    @Transactional(readOnly = true)
    public Set<String> getUserPermissions(String userId) {
        User user = findUser(userId);
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> permissions = new HashSet<>();
        for (Role role : user.getRoles()) {
            if (role.getPermissions() != null) {
                permissions.addAll(
                        role.getPermissions().stream()
                                .map(Permission::getCode)
                                .collect(Collectors.toSet()));
            }
            for (Role inherited : role.getInheritedRoles()) {
                if (inherited.getPermissions() != null) {
                    permissions.addAll(
                            inherited.getPermissions().stream()
                                    .map(Permission::getCode)
                                    .collect(Collectors.toSet()));
                }
            }
        }
        return permissions;
    }

    /**
     * Получить список ролей пользователя как строки (для JWT и UI).
     */
    @Transactional(readOnly = true)
    public List<String> getUserRoleNames(String userId) {
        return getUserRoles(userId).stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    // ------------------------------------------------------------------
    // Управление ролями
    // ------------------------------------------------------------------

    /**
     * Выдать пользователю роль.
     */
    @Transactional
    public void assignRole(String userId, RoleName roleName) {
        User user = findUser(userId);
        if (user == null) {
            throw new AuthException("USER_NOT_FOUND", "Пользователь не найден: " + userId);
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AuthException(
                        "ROLE_NOT_FOUND", "Роль не найдена: " + roleName));

        if (user.getRoles().contains(role)) {
            log.debug("RBAC: роль {} уже есть у пользователя {}", roleName, userId);
            return;
        }

        user.getRoles().add(role);
        userRepository.save(user);

        log.info("RBAC: роль {} выдана пользователю {}", roleName, userId);
    }

    /**
     * Отозвать роль у пользователя.
     */
    @Transactional
    public void revokeRole(String userId, RoleName roleName) {
        User user = findUser(userId);
        if (user == null) {
            throw new AuthException("USER_NOT_FOUND", "Пользователь не найден: " + userId);
        }

        boolean removed = user.getRoles().removeIf(r -> r.getName() == roleName);
        if (removed) {
            userRepository.save(user);
            log.info("RBAC: роль {} отозвана у пользователя {}", roleName, userId);
        } else {
            log.debug("RBAC: роль {} отсутствует у пользователя {}", roleName, userId);
        }
    }

    /**
     * Заменить все роли пользователя на указанный набор.
     */
    @Transactional
    public void replaceRoles(String userId, Set<RoleName> roleNames) {
        User user = findUser(userId);
        if (user == null) {
            throw new AuthException("USER_NOT_FOUND", "Пользователь не найден: " + userId);
        }

        user.getRoles().clear();

        if (roleNames != null && !roleNames.isEmpty()) {
            for (RoleName roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new AuthException(
                                "ROLE_NOT_FOUND", "Роль не найдена: " + roleName));
                user.getRoles().add(role);
            }
        }

        userRepository.save(user);
        log.info("RBAC: роли пользователя {} заменены на {}", userId, roleNames);
    }

    // ------------------------------------------------------------------
    // Иерархия ролей
    // ------------------------------------------------------------------

    /**
     * Проверка: наследует ли роль {@code parent} роль {@code child}.
     */
    @Transactional(readOnly = true)
    public boolean roleInherits(RoleName parent, RoleName child) {
        if (parent == null || child == null) {
            return false;
        }
        if (parent == child) {
            return true;
        }
        Role role = roleRepository.findByName(parent).orElse(null);
        if (role == null) {
            return false;
        }
        return role.getInheritedRoles().stream()
                .anyMatch(r -> r.getName() == child);
    }

    // ------------------------------------------------------------------
    // Диагностика
    // ------------------------------------------------------------------

    /**
     * Полная сводка по пользователю: роли и разрешения.
     */
    @Transactional(readOnly = true)
    public RbacSummary getSummary(String userId) {
        Set<RoleName> roles = getUserRoles(userId);
        Set<String> permissions = getUserPermissions(userId);
        return new RbacSummary(userId, roles, permissions);
    }

    private User findUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(userId)
                .or(() -> userRepository.findById(userId))
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Вложенный DTO: сводка RBAC
    // ------------------------------------------------------------------

    /**
     * Сводка ролей и разрешений пользователя.
     */
    public static class RbacSummary {
        private final String userId;
        private final Set<RoleName> roles;
        private final Set<String> permissions;

        public RbacSummary(String userId, Set<RoleName> roles, Set<String> permissions) {
            this.userId = userId;
            this.roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
            this.permissions = permissions == null ? Collections.emptySet() : Set.copyOf(permissions);
        }

        public String getUserId() { return userId; }
        public Set<RoleName> getRoles() { return roles; }
        public Set<String> getPermissions() { return permissions; }
    }
}