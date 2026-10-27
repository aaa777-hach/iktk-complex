package ru.iktk.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.iktk.auth.model.RoleName;
import ru.iktk.auth.model.User;
import ru.iktk.auth.repository.UserRepository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Тесты AbacService (ABAC: enterprise/ownership/stage; RBAC через explain).
 *
 * <p>Ключевое: {@code isAllowed} = ABAC-часть ({@code evaluate}), НЕ RBAC.
 * RBAC ({@code checkRbac}) доступен через {@code explain(...).get("rbacAllowed")}.
 */
@ExtendWith(MockitoExtension.class)
class AbacServiceTest {

    @Mock private RbacService rbacService;
    @Mock private UserRepository userRepository;

    private AbacService abacService;

    @BeforeEach
    void setUp() {
        abacService = new AbacService(rbacService, userRepository);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private AbacService.SubjectAttributes subject(String userId, Set<RoleName> roles,
                                                   String enterprise) {
        return new AbacService.SubjectAttributes(userId, roles, enterprise, "dept-1");
    }

    private AbacService.SubjectAttributes userSubject() {
        return subject("user-1", Set.of(RoleName.ENGINEER), "ENT-1");
    }

    private AbacService.ResourceAttributes resource(String ownerId, String enterprise,
                                                     String stage) {
        return new AbacService.ResourceAttributes(
                "res-1", "proj-1", ownerId, enterprise,
                AbacService.Classification.INTERNAL, stage);
    }

    private AbacService.ResourceAttributes ownedResource() {
        return resource("user-1", "ENT-1", "DRAFT");
    }

    // ------------------------------------------------------------------
    // isAllowed: null-handling
    // ------------------------------------------------------------------

    @Test
    void isAllowed_nullSubject_false() {
        assertThat(abacService.isAllowed(null, ownedResource(), AbacService.Action.READ)).isFalse();
    }

    @Test
    void isAllowed_nullAction_false() {
        assertThat(abacService.isAllowed(userSubject(), ownedResource(), null)).isFalse();
    }

    @Test
    void isAllowed_nullResource_true() {
        // enterprise=true (resource null), ownership=false, stage=true, cls=true, dept=true
        assertThat(abacService.isAllowed(userSubject(), null, AbacService.Action.READ)).isTrue();
    }

    // ------------------------------------------------------------------
    // isAllowed: enterprise
    // ------------------------------------------------------------------

    @Test
    void isAllowed_otherEnterprise_noAdmin_false() {
        var r = resource("other-user", "ENT-2", "DRAFT");
        assertThat(abacService.isAllowed(userSubject(), r, AbacService.Action.READ)).isFalse();
    }

    @Test
    void isAllowed_adminBypassesEnterprise() {
        var admin = subject("admin-1", Set.of(RoleName.ADMIN), "ENT-X");
        var r = resource("other-user", "ENT-2", "DRAFT");
        assertThat(abacService.isAllowed(admin, r, AbacService.Action.READ)).isTrue();
    }

    @Test
    void isAllowed_integrationBypassesEnterprise() {
        var integration = subject("svc-1", Set.of(RoleName.INTEGRATION), "ENT-X");
        var r = resource("other-user", "ENT-2", "DRAFT");
        assertThat(abacService.isAllowed(integration, r, AbacService.Action.READ)).isTrue();
    }

    // ------------------------------------------------------------------
    // isAllowed: ownership (short-circuit)
    // ------------------------------------------------------------------

    @Test
    void isAllowed_owner_READ_true() {
        assertThat(abacService.isAllowed(userSubject(), ownedResource(), AbacService.Action.READ)).isTrue();
    }

    @Test
    void isAllowed_owner_WRITE_true() {
        assertThat(abacService.isAllowed(userSubject(), ownedResource(), AbacService.Action.WRITE)).isTrue();
    }

    @Test
    void isAllowed_owner_WRITE_stageCompleted_stillTrue_shortCircuit() {
        // ownership short-circuit до stage: даже COMPLETED не блокирует
        var r = resource("user-1", "ENT-1", "COMPLETED");
        assertThat(abacService.isAllowed(userSubject(), r, AbacService.Action.WRITE)).isTrue();
    }

    // ------------------------------------------------------------------
    // isAllowed: stage
    // ------------------------------------------------------------------

    @Test
    void isAllowed_otherUser_stageCompleted_WRITE_false() {
        var r = resource("other-user", "ENT-1", "COMPLETED");
        assertThat(abacService.isAllowed(userSubject(), r, AbacService.Action.WRITE)).isFalse();
    }

    @Test
    void isAllowed_otherUser_stageCompleted_READ_true() {
        // READ не modify -> stage не блокирует
        var r = resource("other-user", "ENT-1", "COMPLETED");
        assertThat(abacService.isAllowed(userSubject(), r, AbacService.Action.READ)).isTrue();
    }

    @Test
    void isAllowed_otherUser_stageArchived_APPROVE_false() {
        var r = resource("other-user", "ENT-1", "ARCHIVED");
        assertThat(abacService.isAllowed(userSubject(), r, AbacService.Action.APPROVE)).isFalse();
    }

    // ------------------------------------------------------------------
    // allowedActions
    // ------------------------------------------------------------------

    @Test
    void allowedActions_owner_returnsAll6() {
        // ownership short-circuit для READ/WRITE/APPROVE/EXPORT;
        // DELETE/ADMIN: ownership=false, но stage (DRAFT) не блокирует -> true
        Set<AbacService.Action> allowed = abacService.allowedActions(userSubject(), ownedResource());
        assertThat(allowed).containsExactlyInAnyOrder(
                AbacService.Action.READ,
                AbacService.Action.WRITE,
                AbacService.Action.APPROVE,
                AbacService.Action.EXPORT,
                AbacService.Action.DELETE,
                AbacService.Action.ADMIN
        );
    }

    @Test
    void allowedActions_otherEnterprise_empty() {
        var r = resource("other-user", "ENT-2", "DRAFT");
        assertThat(abacService.allowedActions(userSubject(), r)).isEmpty();
    }

    // ------------------------------------------------------------------
    // require
    // ------------------------------------------------------------------

    @Test
    void require_allowed_doesNotThrow() {
        abacService.require(userSubject(), ownedResource(), AbacService.Action.READ);
    }

    @Test
    void require_notAllowed_throwsAccessDenied() {
        var r = resource("other-user", "ENT-2", "DRAFT");
        assertThatThrownBy(() -> abacService.require(userSubject(), r, AbacService.Action.READ))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------
    // explain: 11 ключей + rbacAllowed
    // ------------------------------------------------------------------

    @Test
    void explain_hasAll11Keys() {
        Map<String, Object> m = abacService.explain(
                userSubject(), ownedResource(), AbacService.Action.READ);

        assertThat(m).containsKeys(
                "userId", "roles", "action", "resourceId",
                "rbacAllowed", "enterpriseOk", "ownershipOk",
                "stageOk", "classificationOk", "departmentOk", "finalAllowed"
        );
    }

    @Test
    void explain_nullSubject() {
        Map<String, Object> m = abacService.explain(null, null, AbacService.Action.READ);
        assertThat(m.get("userId")).isNull();
        assertThat(m.get("roles")).isEqualTo(Set.of());
        assertThat(m.get("rbacAllowed")).isEqualTo(false);
        assertThat(m.get("finalAllowed")).isEqualTo(false);
    }

    @Test
    void explain_rbacAllowed_trueForAdmin() {
        var admin = subject("admin-1", Set.of(RoleName.ADMIN), "ENT-1");
        Map<String, Object> m = abacService.explain(admin, null, AbacService.Action.ADMIN);
        assertThat(m.get("rbacAllowed")).isEqualTo(true);
    }

    @Test
    void explain_rbacAllowed_falseForViewerWrite() {
        var viewer = subject("v-1", Set.of(RoleName.VIEWER), "ENT-1");
        Map<String, Object> m = abacService.explain(viewer, null, AbacService.Action.WRITE);
        assertThat(m.get("rbacAllowed")).isEqualTo(false);
    }

    @Test
    void explain_rbacAllowed_DELETE_blocksManager() {
        var manager = subject("m-1", Set.of(RoleName.MANAGER), "ENT-1");
        Map<String, Object> m = abacService.explain(manager, null, AbacService.Action.DELETE);
        assertThat(m.get("rbacAllowed")).isEqualTo(false);
    }

    @Test
    void explain_rbacAllowed_EXPORT_allowsEngineer() {
        var engineer = subject("e-1", Set.of(RoleName.ENGINEER), "ENT-1");
        Map<String, Object> m = abacService.explain(engineer, null, AbacService.Action.EXPORT);
        assertThat(m.get("rbacAllowed")).isEqualTo(true);
    }

    @Test
    void explain_rbacAllowed_APPROVE_allowsManager() {
        var manager = subject("m-1", Set.of(RoleName.MANAGER), "ENT-1");
        Map<String, Object> m = abacService.explain(manager, null, AbacService.Action.APPROVE);
        assertThat(m.get("rbacAllowed")).isEqualTo(true);
    }

    // ------------------------------------------------------------------
    // buildSubject
    // ------------------------------------------------------------------

    @Test
    void buildSubject_unknownUser_returnsEmptyRoles() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        AbacService.SubjectAttributes s = abacService.buildSubject("ghost");

        assertThat(s.getUserId()).isEqualTo("ghost");
        assertThat(s.getRoles()).isEmpty();
        assertThat(s.getEnterpriseCode()).isNull();
    }
}