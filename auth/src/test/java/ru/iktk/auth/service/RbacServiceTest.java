package ru.iktk.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.iktk.auth.model.Permission;
import ru.iktk.auth.model.Role;
import ru.iktk.auth.model.RoleName;
import ru.iktk.auth.model.User;
import ru.iktk.auth.repository.PermissionRepository;
import ru.iktk.auth.repository.RoleRepository;
import ru.iktk.auth.repository.UserRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Тесты RbacService (роли, разрешения, наследование).
 */
@ExtendWith(MockitoExtension.class)
class RbacServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;

    private RbacService rbacService;
    private User admin;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        rbacService = new RbacService(userRepository, roleRepository, permissionRepository);

        Permission userRead  = new Permission("user:read", "Read users");
        Permission userWrite = new Permission("user:write", "Write users");
        Permission projRead  = new Permission("project:read", "Read projects");

        adminRole = new Role(RoleName.ADMIN);
        adminRole.getPermissions().add(userRead);
        adminRole.getPermissions().add(userWrite);
        adminRole.getPermissions().add(projRead);

        admin = new User();
        admin.setId("user-123");
        admin.setUsername("admin");
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        admin.setRoles(roles);
    }

    @Test
    void hasRole_true() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.hasRole("user-123", RoleName.ADMIN)).isTrue();
    }

    @Test
    void hasRole_false() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.hasRole("user-123", RoleName.MANAGER)).isFalse();
    }

    @Test
    void hasAnyRole_trueIfAtLeastOne() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.hasAnyRole("user-123", RoleName.MANAGER, RoleName.ADMIN)).isTrue();
    }

    @Test
    void hasAllRoles_falseIfMissingOne() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.hasAllRoles("user-123", RoleName.ADMIN, RoleName.MANAGER)).isFalse();
    }

    @Test
    void getUserRoles_returnsRoleNames() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.getUserRoles("user-123")).containsExactly(RoleName.ADMIN);
    }

    @Test
    void getUserRoles_unknownUser_returnsEmpty() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThat(rbacService.getUserRoles("ghost")).isEmpty();
    }

    @Test
    void getUserPermissions_returnsCodesFromRole() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.getUserPermissions("user-123"))
                .containsExactlyInAnyOrder("user:read", "user:write", "project:read");
    }

    @Test
    void hasPermission_true() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.hasPermission("user-123", "user:read")).isTrue();
    }

    @Test
    void hasPermission_false() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThat(rbacService.hasPermission("user-123", "unknown:perm")).isFalse();
    }

    @Test
    void hasPermission_blankCode_returnsFalse() {
        assertThat(rbacService.hasPermission("user-123", "")).isFalse();
        assertThat(rbacService.hasPermission("user-123", null)).isFalse();
    }

    @Test
    void requirePermission_throwsWhenMissing() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> rbacService.requirePermission("user-123", "unknown:perm"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePermission_doesNotThrowWhenPresent() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        rbacService.requirePermission("user-123", "user:read");
    }

    @Test
    void requireRole_throwsWhenMissing() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> rbacService.requireRole("user-123", RoleName.MANAGER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void roleInherits_sameRole_returnsTrue() {
        assertThat(rbacService.roleInherits(RoleName.ADMIN, RoleName.ADMIN)).isTrue();
    }

    @Test
    void roleInherits_directInheritance_true() {
        Role managerRole = new Role(RoleName.MANAGER);
        managerRole.getInheritedRoles().add(adminRole);
        when(roleRepository.findByName(RoleName.MANAGER)).thenReturn(Optional.of(managerRole));

        assertThat(rbacService.roleInherits(RoleName.MANAGER, RoleName.ADMIN)).isTrue();
    }

    @Test
    void roleInherits_noInheritance_false() {
        Role managerRole = new Role(RoleName.MANAGER);
        when(roleRepository.findByName(RoleName.MANAGER)).thenReturn(Optional.of(managerRole));

        assertThat(rbacService.roleInherits(RoleName.MANAGER, RoleName.ADMIN)).isFalse();
    }

    @Test
    void roleInherits_nullArgs_false() {
        assertThat(rbacService.roleInherits(null, RoleName.ADMIN)).isFalse();
        assertThat(rbacService.roleInherits(RoleName.ADMIN, null)).isFalse();
    }
}