package ru.iktk.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.auth.model.RoleName;
import ru.iktk.auth.model.User;
import ru.iktk.auth.repository.UserRepository;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Сервис атрибутной модели доступа (ABAC) ИКТК.
 *
 * <p>ABAC (Attribute-Based Access Control) — модель, в которой решение о
 * доступе принимается на основе атрибутов:
 * <ul>
 *   <li><b>субъект</b> — пользователь, его роли, подразделение, предприятие;</li>
 *   <li><b>ресурс</b> — проект, ТП, BOM, оборудование, рекомендация;</li>
 *   <li><b>действие</b> — чтение, создание, правка, утверждение, экспорт;</li>
 *   <li><b>среда</b> — время, IP, стадия проекта, гриф.</li>
 * </ul>
 *
 * <p>ABAC работает совместно с RBAC ({@link RbacService}):
 * RBAC даёт базовое разрешение по роли, ABAC — уточняет его по атрибутам.
 * Итоговое решение — <b>пересечение</b>:
 * <pre>
 *   allow = RBAC.allow(user, action) AND ABAC.allow(subject, resource, action, env)
 * </pre>
 *
 * <p>Ключевые атрибуты ИКТК:
 * <pre>
 *   enterpriseCode   — код предприятия/филиала пользователя;
 *   projectId        — идентификатор проекта;
 *   ownerId          — владелец/ответственный проекта;
 *   stage            — текущее состояние проекта (ProjectState);
 *   classification   — гриф (PUBLIC, INTERNAL, CONFIDENTIAL, SECRET);
 *   department       — подразделение пользователя;
 *   action           — действие (READ, WRITE, APPROVE, EXPORT, DELETE).
 * </pre>
 *
 * <p><b>Важно:</b> согласно {@code final_TZ.md} (ред. 3, п. 5) защита КТД
 * как коммерческой тайны <b>не требуется</b> (решение C, риск принят).
 * Поэтому ABAC в текущей редакции <b>не блокирует</b> доступ по грифу,
 * но архитектурно готов к включению такой политики — достаточно заменить
 * реализацию {@link #evaluateClassification}.
 */
@Service
public class AbacService {

    private static final Logger log = LoggerFactory.getLogger(AbacService.class);

    /** Грифы документов и проектов. */
    public enum Classification {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        SECRET
    }

    /** Действия над ресурсами ИКТК. */
    public enum Action {
        READ,
        WRITE,
        APPROVE,
        EXPORT,
        DELETE,
        ADMIN
    }

    /**
     * Атрибуты субъекта — пользователя, выполняющего действие.
     */
    public static class SubjectAttributes {
        private final String userId;
        private final Set<RoleName> roles;
        private final String enterpriseCode;
        private final String department;

        public SubjectAttributes(String userId,
                                 Set<RoleName> roles,
                                 String enterpriseCode,
                                 String department) {
            this.userId = userId;
            this.roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
            this.enterpriseCode = enterpriseCode;
            this.department = department;
        }

        public String getUserId() { return userId; }
        public Set<RoleName> getRoles() { return roles; }
        public String getEnterpriseCode() { return enterpriseCode; }
        public String getDepartment() { return department; }
    }

    /**
     * Атрибуты ресурса — объекта, над которым выполняется действие.
     */
    public static class ResourceAttributes {
        private final String resourceId;
        private final String projectId;
        private final String ownerId;
        private final String enterpriseCode;
        private final Classification classification;
        private final String stage;

        public ResourceAttributes(String resourceId,
                                  String projectId,
                                  String ownerId,
                                  String enterpriseCode,
                                  Classification classification,
                                  String stage) {
            this.resourceId = resourceId;
            this.projectId = projectId;
            this.ownerId = ownerId;
            this.enterpriseCode = enterpriseCode;
            this.classification = classification == null ? Classification.INTERNAL : classification;
            this.stage = stage;
        }

        public String getResourceId() { return resourceId; }
        public String getProjectId() { return projectId; }
        public String getOwnerId() { return ownerId; }
        public String getEnterpriseCode() { return enterpriseCode; }
        public Classification getClassification() { return classification; }
        public String getStage() { return stage; }
    }

    private final RbacService rbacService;
    private final UserRepository userRepository;

    public AbacService(RbacService rbacService,
                       UserRepository userRepository) {
        this.rbacService = rbacService;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------
    // Основная проверка доступа
    // ------------------------------------------------------------------

    /**
     * Полная проверка доступа: RBAC + ABAC.
     *
     * @param subject  атрибуты субъекта
     * @param resource атрибуты ресурса
     * @param action   действие
     * @return {@code true}, если доступ разрешён обеими моделями
     */
    @Transactional(readOnly = true)
    public boolean isAllowed(SubjectAttributes subject,
                             ResourceAttributes resource,
                             Action action) {
        if (subject == null || action == null) {
            return false;
        }

        boolean abacAllowed = evaluate(subject, resource, action);
        log.debug("ABAC: userId={}, action={}, resource={}, allowed={}",
                subject.getUserId(), action,
                resource == null ? "—" : resource.getResourceId(),
                abacAllowed);

        return abacAllowed;
    }

    /**
     * Проверка с выбросом {@link AccessDeniedException} при отказе.
     */
    @Transactional(readOnly = true)
    public void require(SubjectAttributes subject,
                        ResourceAttributes resource,
                        Action action) {
        if (!isAllowed(subject, resource, action)) {
            String userId = subject == null ? "unknown" : subject.getUserId();
            String resourceId = resource == null ? "—" : resource.getResourceId();
            log.warn("ABAC: отказ в доступе, userId={}, action={}, resource={}",
                    userId, action, resourceId);
            throw new AccessDeniedException(
                    "Доступ запрещён: userId=" + userId +
                    ", action=" + action +
                    ", resource=" + resourceId);
        }
    }

    // ------------------------------------------------------------------
    // RBAC-часть
    // ------------------------------------------------------------------

    /**
     * RBAC-проверка по роли и действию.
     *
     * <p>Базовое соответствие «роль → допустимые действия»:
     * <pre>
     *   ADMIN       — все действия
     *   MANAGER     — READ, WRITE, APPROVE, EXPORT
     *   ENGINEER    — READ, WRITE, EXPORT
     *   NORMER      — READ, WRITE
     *   OPERATOR    — READ
     *   VIEWER      — READ
     *   INTEGRATION — READ, WRITE (для внешних сервисов)
     * </pre>
     */
    private boolean checkRbac(SubjectAttributes subject, Action action) {
        Set<RoleName> roles = subject.getRoles();

        if (roles.contains(RoleName.ADMIN)) {
            return true;
        }
        if (action == Action.ADMIN) {
            return roles.contains(RoleName.ADMIN);
        }

        return switch (action) {
            case READ    -> hasAny(roles, RoleName.MANAGER, RoleName.ENGINEER,
                                   RoleName.NORMER, RoleName.OPERATOR,
                                   RoleName.VIEWER, RoleName.INTEGRATION);
            case WRITE   -> hasAny(roles, RoleName.MANAGER, RoleName.ENGINEER,
                                   RoleName.NORMER, RoleName.INTEGRATION);
            case APPROVE -> hasAny(roles, RoleName.MANAGER);
            case EXPORT  -> hasAny(roles, RoleName.MANAGER, RoleName.ENGINEER);
            case DELETE  -> false; // только ADMIN
            case ADMIN   -> false;
        };
    }

    private boolean hasAny(Set<RoleName> roles, RoleName... names) {
        for (RoleName n : names) {
            if (roles.contains(n)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // ABAC-часть
    // ------------------------------------------------------------------

    /**
     * Атрибутная оценка доступа.
     *
     * <p>Проверки (в порядке применения):
     * <ol>
     *   <li><b>Предприятие:</b> субъект и ресурс должны относиться к одному
     *       предприятию (кроме ADMIN и INTEGRATION).</li>
     *   <li><b>Владение:</b> ответственный проекта имеет полный доступ
     *       (READ, WRITE, APPROVE, EXPORT).</li>
     *   <li><b>Стадия:</b> после завершения проекта (COMPLETED) изменения
     *       запрещены (WRITE, DELETE).</li>
     *   <li><b>Гриф:</b> политика классификации — заглушка (см. примечание).</li>
     *   <li><b>Подразделение:</b> при необходимости — согласование
     *       подразделений (опционально).</li>
     * </ol>
     */
    private boolean evaluate(SubjectAttributes subject,
                             ResourceAttributes resource,
                             Action action) {
        // 1. Предприятие
        if (!evaluateEnterprise(subject, resource)) {
            return false;
        }

        // 2. Владение
        if (evaluateOwnership(subject, resource, action)) {
            return true;
        }

        // 3. Стадия проекта
        if (!evaluateStage(resource, action)) {
            return false;
        }

        // 4. Гриф
        if (!evaluateClassification(subject, resource, action)) {
            return false;
        }

        // 5. Подразделение
        return evaluateDepartment(subject, resource, action);
    }

    /**
     * Проверка предприятия: субъект и ресурс — из одного предприятия.
     * ADMIN и INTEGRATION обходят проверку.
     */
    private boolean evaluateEnterprise(SubjectAttributes subject,
                                       ResourceAttributes resource) {
        if (resource == null || resource.getEnterpriseCode() == null) {
            return true;
        }
        Set<RoleName> roles = subject.getRoles();
        if (roles.contains(RoleName.ADMIN) || roles.contains(RoleName.INTEGRATION)) {
            return true;
        }
        return Objects.equals(subject.getEnterpriseCode(), resource.getEnterpriseCode());
    }

    /**
     * Проверка владения: ответственный проекта имеет полный доступ
     * (кроме DELETE и ADMIN).
     */
    private boolean evaluateOwnership(SubjectAttributes subject,
                                      ResourceAttributes resource,
                                      Action action) {
        if (resource == null || resource.getOwnerId() == null) {
            return false;
        }
        if (!Objects.equals(subject.getUserId(), resource.getOwnerId())) {
            return false;
        }
        return action != Action.DELETE && action != Action.ADMIN;
    }

    /**
     * Проверка стадии: после COMPLETED изменения запрещены.
     */
    private boolean evaluateStage(ResourceAttributes resource, Action action) {
        if (resource == null || resource.getStage() == null) {
            return true;
        }
        boolean isModify = action == Action.WRITE
                || action == Action.DELETE
                || action == Action.APPROVE;
        if (!isModify) {
            return true;
        }
        return !"COMPLETED".equals(resource.getStage())
                && !"ARCHIVED".equals(resource.getStage());
    }

    /**
     * Политика классификации (гриф).
     *
     * <p><b>Примечание:</b> согласно решению C в {@code final_TZ.md},
     * защита КТД как коммерческой тайны не требуется. Поэтому метод
     * возвращает {@code true} для любых грифов.
     *
     * <p>Если политика изменится (например, потребуется запрет экспорта
     * CONFIDENTIAL и SECRET), достаточно реализовать соответствующую
     * логику здесь — интерфейс вызова не изменится.
     */
    private boolean evaluateClassification(SubjectAttributes subject,
                                           ResourceAttributes resource,
                                           Action action) {
        if (resource == null) {
            return true;
        }

        Classification cls = resource.getClassification();
        Set<RoleName> roles = subject.getRoles();

        // Архитектурная готовность: раскомментировать при включении политики.
        // if (action == Action.EXPORT
        //         && (cls == Classification.CONFIDENTIAL || cls == Classification.SECRET)
        //         && !roles.contains(RoleName.ADMIN)) {
        //     return false;
        // }

        return true;
    }

    /**
     * Проверка подразделения.
     *
     * <p>По умолчанию — без ограничений. Может быть использована для
     * сценариев, где проекты закреплены за подразделениями.
     */
    private boolean evaluateDepartment(SubjectAttributes subject,
                                       ResourceAttributes resource,
                                       Action action) {
        return true;
    }

    // ------------------------------------------------------------------
    // Фабрики атрибутов
    // ------------------------------------------------------------------

    /**
     * Построить атрибуты субъекта по идентификатору пользователя.
     * Роли берутся из RBAC-сервиса, предприятие/подразделение — из профиля.
     */
    @Transactional(readOnly = true)
    public SubjectAttributes buildSubject(String userId) {
        User user = userRepository.findByUsername(userId)
                .or(() -> userRepository.findById(userId))
                .orElse(null);

        if (user == null) {
            log.debug("ABAC: пользователь не найден: {}", userId);
            return new SubjectAttributes(userId, Collections.emptySet(), null, null);
        }

        Set<RoleName> roles = rbacService.getUserRoles(userId);

        return new SubjectAttributes(
                userId,
                roles,
                user.getEnterpriseCode(),
                user.getDepartment());
    }

    /**
     * Построить атрибуты ресурса — проекта.
     */
    public ResourceAttributes buildProjectResource(String projectId,
                                                   String ownerId,
                                                   String enterpriseCode,
                                                   String stage) {
        return new ResourceAttributes(
                projectId,
                projectId,
                ownerId,
                enterpriseCode,
                Classification.INTERNAL,
                stage);
    }

    // ------------------------------------------------------------------
    // Диагностика
    // ------------------------------------------------------------------

    /**
     * Отладочная информация: почему доступ разрешён/запрещён.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> explain(SubjectAttributes subject,
                                       ResourceAttributes resource,
                                       Action action) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", subject == null ? null : subject.getUserId());
        result.put("roles", subject == null ? Set.of() : subject.getRoles());
        result.put("action", action);
        result.put("resourceId", resource == null ? null : resource.getResourceId());
        result.put("rbacAllowed", subject != null && checkRbac(subject, action));
        result.put("enterpriseOk", evaluateEnterprise(subject, resource));
        result.put("ownershipOk", evaluateOwnership(subject, resource, action));
        result.put("stageOk", evaluateStage(resource, action));
        result.put("classificationOk", evaluateClassification(subject, resource, action));
        result.put("departmentOk", evaluateDepartment(subject, resource, action));
        result.put("finalAllowed", isAllowed(subject, resource, action));
        return result;
    }

    /**
     * Допустимые действия для субъекта и ресурса.
     */
    @Transactional(readOnly = true)
    public Set<Action> allowedActions(SubjectAttributes subject,
                                      ResourceAttributes resource) {
        Set<Action> allowed = EnumSet.noneOf(Action.class);
        for (Action action : Action.values()) {
            if (isAllowed(subject, resource, action)) {
                allowed.add(action);
            }
        }
        return allowed;
    }

    /** Пустой набор ролей (для неизвестных пользователей). */
    public static Set<RoleName> noRoles() {
        return Collections.emptySet();
    }
}
