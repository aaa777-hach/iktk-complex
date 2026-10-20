package ru.iktk.shared.versioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VersioningService — версионность и откат")
class VersioningServiceTest {

    @Mock private JdbcTemplate jdbc;

    private ObjectMapper mapper;
    private VersioningService service;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new VersioningService(jdbc, mapper);
    }

    private ObjectNode snap(String json) throws Exception {
        return (ObjectNode) mapper.readTree(json);
    }

    private VersioningService.EntityVersionRecord record(String version, int number, JsonNode snapshot) {
        return new VersioningService.EntityVersionRecord(
                UUID.randomUUID(), "Project", UUID.randomUUID(), UUID.randomUUID(),
                version, number, null, snapshot, null,
                VersioningService.ChangeType.CREATE, null, "user", java.time.Instant.now(), true);
    }

    // ===== createVersion =====

    @Test
    @DisplayName("createVersion: current нет -> только INSERT, version=v1, number=1")
    void createVersion_noCurrent_insertOnly() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());

        VersioningService.EntityVersionRecord r = service.createVersion(
                "Project", UUID.randomUUID(), UUID.randomUUID(),
                snap("{\"state\":\"DRAFT\",\"name\":\"X\"}"),
                VersioningService.ChangeType.CREATE, "init", "user");

        assertThat(r.version()).isEqualTo("v1");
        assertThat(r.versionNumber()).isEqualTo(1);
        assertThat(r.state()).isEqualTo("DRAFT");
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("createVersion: current есть -> UPDATE + INSERT, version=v2, number=2")
    void createVersion_withCurrent_updateAndInsert() throws Exception {
        JsonNode oldSnap = snap("{\"name\":\"A\"}");
        VersioningService.EntityVersionRecord current = record("v1", 1, oldSnap);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of(current));

        VersioningService.EntityVersionRecord r = service.createVersion(
                "Project", UUID.randomUUID(), UUID.randomUUID(),
                snap("{\"name\":\"B\"}"),
                VersioningService.ChangeType.UPDATE, "update", "user");

        assertThat(r.version()).isEqualTo("v2");
        assertThat(r.versionNumber()).isEqualTo(2);
        assertThat(r.diff()).isNotNull();
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("createVersion: null snapshot -> VersioningException")
    void createVersion_nullSnapshot_throws() {
        assertThatThrownBy(() -> service.createVersion(
                "Project", UUID.randomUUID(), UUID.randomUUID(), null,
                VersioningService.ChangeType.CREATE, null, "user"))
                .isInstanceOf(VersioningService.VersioningException.class)
                .hasMessageContaining("Снапшот");
    }

    @Test
    @DisplayName("createVersion: null/blank entityType -> VersioningException")
    void createVersion_blankEntityType_throws() throws Exception {
        ObjectNode snap = snap("{}");
        assertThatThrownBy(() -> service.createVersion(null, UUID.randomUUID(), null, snap,
                VersioningService.ChangeType.CREATE, null, "user"))
                .isInstanceOf(VersioningService.VersioningException.class)
                .hasMessageContaining("entityType");
        assertThatThrownBy(() -> service.createVersion("  ", UUID.randomUUID(), null, snap,
                VersioningService.ChangeType.CREATE, null, "user"))
                .isInstanceOf(VersioningService.VersioningException.class);
    }

    @Test
    @DisplayName("createVersion: null entityId -> VersioningException")
    void createVersion_nullEntityId_throws() throws Exception {
        ObjectNode snap = snap("{}");
        assertThatThrownBy(() -> service.createVersion("Project", null, null, snap,
                VersioningService.ChangeType.CREATE, null, "user"))
                .isInstanceOf(VersioningService.VersioningException.class)
                .hasMessageContaining("entityId");
    }

    @Test
    @DisplayName("createVersion: snapshot со status -> state извлекается из status")
    void createVersion_stateFromStatus() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());

        VersioningService.EntityVersionRecord r = service.createVersion(
                "Project", UUID.randomUUID(), null,
                snap("{\"status\":\"ACTIVE\"}"),
                VersioningService.ChangeType.CREATE, null, "user");

        assertThat(r.state()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("createVersion: snapshot без state/status -> state=null")
    void createVersion_noStateField() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());

        VersioningService.EntityVersionRecord r = service.createVersion(
                "Project", UUID.randomUUID(), null,
                snap("{\"name\":\"X\"}"),
                VersioningService.ChangeType.CREATE, null, "user");

        assertThat(r.state()).isNull();
    }

    // ===== getHistory =====

    @Test
    @DisplayName("getHistory: limit <= 0 -> 100")
    void getHistory_limitZero_usesDefault100() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        service.getHistory("Project", UUID.randomUUID(), 0);

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(anyString(), any(RowMapper.class), captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        assertThat(captor.getValue()[2]).isEqualTo(100);
    }

    @Test
    @DisplayName("getHistory: limit > 1000 -> 1000")
    void getHistory_limitTooBig_capped1000() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        service.getHistory("Project", UUID.randomUUID(), 9999);

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(anyString(), any(RowMapper.class), captor.capture());
        assertThat(captor.getValue()[2]).isEqualTo(1000);
    }

    @Test
    @DisplayName("getHistory: limit 50 -> 50")
    void getHistory_limit50_kept() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        service.getHistory("Project", UUID.randomUUID(), 50);

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(anyString(), any(RowMapper.class), captor.capture());
        assertThat(captor.getValue()[2]).isEqualTo(50);
    }

    @Test
    @DisplayName("getHistory: null entityType -> VersioningException")
    void getHistory_nullEntityType_throws() {
        assertThatThrownBy(() -> service.getHistory(null, UUID.randomUUID(), 10))
                .isInstanceOf(VersioningService.VersioningException.class);
    }

    // ===== getCurrentVersion =====

    @Test
    @DisplayName("getCurrentVersion: null entityType -> VersioningException")
    void getCurrentVersion_nullType_throws() {
        assertThatThrownBy(() -> service.getCurrentVersion(null, UUID.randomUUID()))
                .isInstanceOf(VersioningService.VersioningException.class);
    }

    @Test
    @DisplayName("getCurrentVersion: пустой результат -> null")
    void getCurrentVersion_empty_null() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());
        assertThat(service.getCurrentVersion("Project", UUID.randomUUID())).isNull();
    }

    @Test
    @DisplayName("getCurrentVersion: найден -> возвращает запись")
    void getCurrentVersion_found() throws Exception {
        VersioningService.EntityVersionRecord current = record("v3", 3, snap("{}"));
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of(current));
        assertThat(service.getCurrentVersion("Project", UUID.randomUUID())).isSameAs(current);
    }

    // ===== rollbackToVersion =====

    @Test
    @DisplayName("rollbackToVersion: null/blank toVersion -> VersioningException")
    void rollback_blankVersion_throws() {
        assertThatThrownBy(() -> service.rollbackToVersion("Project", UUID.randomUUID(), null, null, "user"))
                .isInstanceOf(VersioningService.VersioningException.class)
                .hasMessageContaining("версия");
        assertThatThrownBy(() -> service.rollbackToVersion("Project", UUID.randomUUID(), "  ", null, "user"))
                .isInstanceOf(VersioningService.VersioningException.class);
    }

    @Test
    @DisplayName("rollbackToVersion: версия не найдена -> VersioningException")
    void rollback_notFound_throws() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.rollbackToVersion("Project", UUID.randomUUID(), "v99", null, "user"))
                .isInstanceOf(VersioningService.VersioningException.class)
                .hasMessageContaining("не найдена");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("rollbackToVersion: найдена -> createVersion с ChangeType.ROLLBACK")
    void rollback_found_callsCreateVersion() throws Exception {
        JsonNode snap = snap("{\"state\":\"X\"}");
        VersioningService.EntityVersionRecord target = new VersioningService.EntityVersionRecord(
                UUID.randomUUID(), "Project", UUID.randomUUID(), UUID.randomUUID(),
                "v3", 3, "X", snap, null,
                VersioningService.ChangeType.CREATE, null, "user", java.time.Instant.now(), false);

        // findCurrent (при createVersion) вернёт null -> INSERT only
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of(target));
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());

        VersioningService.EntityVersionRecord r = service.rollbackToVersion(
                "Project", UUID.randomUUID(), "v3", "reason", "user");

        assertThat(r.versionNumber()).isEqualTo(1);
        assertThat(r.changeType()).isEqualTo(VersioningService.ChangeType.ROLLBACK);
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    // ===== computeDiff =====

    @Test
    @DisplayName("computeDiff: оба null -> null")
    void computeDiff_bothNull() {
        assertThat(service.computeDiff(null, null)).isNull();
    }

    @Test
    @DisplayName("computeDiff: old null -> added")
    void computeDiff_oldNull_added() throws Exception {
        JsonNode result = service.computeDiff(null, snap("{\"a\":1}"));
        assertThat(result).isNotNull();
        assertThat(result.has("added")).isTrue();
        assertThat(result.get("added").get("a").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("computeDiff: new null -> removed")
    void computeDiff_newNull_removed() throws Exception {
        JsonNode result = service.computeDiff(snap("{\"a\":1}"), null);
        assertThat(result).isNotNull();
        assertThat(result.has("removed")).isTrue();
        assertThat(result.get("removed").get("a").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("computeDiff: равные -> null")
    void computeDiff_equal_null() throws Exception {
        assertThat(service.computeDiff(snap("{\"a\":1}"), snap("{\"a\":1}"))).isNull();
    }

    @Test
    @DisplayName("computeDiff: changed field")
    void computeDiff_changed() throws Exception {
        JsonNode result = service.computeDiff(snap("{\"a\":1}"), snap("{\"a\":2}"));
        assertThat(result).isNotNull();
        assertThat(result.has("changed")).isTrue();
        assertThat(result.get("changed").get("a").get("from").asInt()).isEqualTo(1);
        assertThat(result.get("changed").get("a").get("to").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("computeDiff: added + removed + changed одновременно")
    void computeDiff_allTypes() throws Exception {
        JsonNode result = service.computeDiff(
                snap("{\"a\":1,\"b\":2}"),
                snap("{\"a\":1,\"b\":3,\"c\":4}"));
        assertThat(result.has("added")).isTrue();
        assertThat(result.get("added").get("c").asInt()).isEqualTo(4);
        assertThat(result.has("changed")).isTrue();
        assertThat(result.get("changed").has("b")).isTrue();
    }

    // ===== ChangeType / VersioningException =====

    @Test
    @DisplayName("ChangeType: 8 значений")
    void changeType_values() {
        assertThat(VersioningService.ChangeType.values()).containsExactly(
                VersioningService.ChangeType.CREATE,
                VersioningService.ChangeType.UPDATE,
                VersioningService.ChangeType.DELETE,
                VersioningService.ChangeType.ROLLBACK,
                VersioningService.ChangeType.APPROVE,
                VersioningService.ChangeType.REJECT,
                VersioningService.ChangeType.ARCHIVE,
                VersioningService.ChangeType.RESTORE);
    }

    @Test
    @DisplayName("VersioningException: оба конструктора")
    void versioningException_constructors() {
        VersioningService.VersioningException e1 = new VersioningService.VersioningException("msg");
        assertThat(e1.getMessage()).isEqualTo("msg");
        assertThat(e1.getCause()).isNull();

        RuntimeException cause = new RuntimeException("root");
        VersioningService.VersioningException e2 = new VersioningService.VersioningException("msg2", cause);
        assertThat(e2.getMessage()).isEqualTo("msg2");
        assertThat(e2.getCause()).isSameAs(cause);
    }
}