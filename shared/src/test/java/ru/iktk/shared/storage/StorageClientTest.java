package ru.iktk.shared.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("StorageClient — утилиты (buildObjectKey, parseUri, sha256, ...)")
class StorageClientTest {

    private StorageClient client;

    @BeforeEach
    void setUp() {
        client = new StorageClient(mock(MinioClient.class));
        ReflectionTestUtils.setField(client, "defaultBucket", "iktk-files");
        ReflectionTestUtils.setField(client, "tempDir", System.getProperty("java.io.tmpdir"));
        ReflectionTestUtils.setField(client, "maxFileSize", 10_000_000L);
        ReflectionTestUtils.setField(client, "presignedTtlMinutes", 15L);
    }

    // ===== buildObjectKey =====

    @Test
    @DisplayName("buildObjectKey: {projectId}/{category}/{uuid}-{filename}")
    void buildObjectKey_normal() {
        String key = client.buildObjectKey("proj-1", "cnc", "O_123.nc");
        assertThat(key).startsWith("proj-1/cnc/");
        assertThat(key).endsWith("-O_123.nc");
        assertThat(key.split("/")).hasSize(3);
    }

    @Test
    @DisplayName("buildObjectKey: null-ы заменяются на common/files/unnamed")
    void buildObjectKey_nulls() {
        String key = client.buildObjectKey(null, null, null);
        assertThat(key).startsWith("common/files/");
        assertThat(key).endsWith("-unnamed");
    }

    @Test
    @DisplayName("buildObjectKey: недопустимые символы заменяются на _")
    void buildObjectKey_sanitizes() {
        String key = client.buildObjectKey("proj 1", "кат", "file name.nc");
        assertThat(key).contains("proj_1/");
        assertThat(key).contains("___/");
        assertThat(key).endsWith("-file_name.nc");
    }

    @Test
    @DisplayName("buildObjectKey: каждый вызов даёт новый UUID")
    void buildObjectKey_uniqueEachCall() {
        String a = client.buildObjectKey("p", "c", "f.nc");
        String b = client.buildObjectKey("p", "c", "f.nc");
        assertThat(a).isNotEqualTo(b);
    }

    // ===== buildUri / parseUri =====

    @Test
    @DisplayName("buildUri: minio://bucket/key")
    void buildUri_ok() {
        assertThat(client.buildUri("iktk-files", "a/b/c"))
                .isEqualTo("minio://iktk-files/a/b/c");
    }

    @Test
    @DisplayName("parseUri: валидный URI")
    void parseUri_valid() {
        StorageClient.UriParts parts = client.parseUri("minio://iktk-files/proj-1/cnc/file.nc");
        assertThat(parts.bucket()).isEqualTo("iktk-files");
        assertThat(parts.objectKey()).isEqualTo("proj-1/cnc/file.nc");
    }

    @Test
    @DisplayName("parseUri: без префикса -> defaultBucket")
    void parseUri_noPrefix_usesDefaultBucket() {
        StorageClient.UriParts parts = client.parseUri("proj-1/file.nc");
        assertThat(parts.bucket()).isEqualTo("iktk-files");
        assertThat(parts.objectKey()).isEqualTo("proj-1/file.nc");
    }

    @Test
    @DisplayName("parseUri: пустой/null -> StorageException")
    void parseUri_blank_throws() {
        assertThatThrownBy(() -> client.parseUri(null))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("URI");
        assertThatThrownBy(() -> client.parseUri(""))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("URI");
        assertThatThrownBy(() -> client.parseUri("   "))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("URI");
    }

    @Test
    @DisplayName("parseUri: без / после bucket -> StorageException")
    void parseUri_noSlash_throws() {
        assertThatThrownBy(() -> client.parseUri("minio://bucket-only"))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("URI");
    }

    @Test
    @DisplayName("parseUri: пустой bucket или key -> StorageException")
    void parseUri_blankParts_throws() {
        assertThatThrownBy(() -> client.parseUri("minio:///key"))
                .isInstanceOf(StorageClient.StorageException.class);
        assertThatThrownBy(() -> client.parseUri("minio://bucket/"))
                .isInstanceOf(StorageClient.StorageException.class);
    }

    // ===== sha256 =====

    @Test
    @DisplayName("sha256(Path): известное значение для 'hello'")
    void sha256_path_knownValue() throws Exception {
        Path tmp = Files.createTempFile("sha-test-", ".txt");
        try {
            Files.writeString(tmp, "hello", StandardCharsets.UTF_8);
            String hash = client.sha256(tmp);
            assertThat(hash).isEqualTo(
                    "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("sha256(Path): null -> StorageException")
    void sha256_path_null_throws() {
        assertThatThrownBy(() -> client.sha256((Path) null))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("sha256(Path): несуществующий файл -> StorageException")
    void sha256_path_missing_throws() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"), "no-such-file-" + System.nanoTime());
        assertThatThrownBy(() -> client.sha256(missing))
                .isInstanceOf(StorageClient.StorageException.class);
    }

    @Test
    @DisplayName("sha256(String): известное значение для 'hello'")
    void sha256_string_knownValue() {
        assertThat(client.sha256("hello")).isEqualTo(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    @DisplayName("sha256(String): null -> null; пустая строка -> хэш пустой строки")
    void sha256_string_nullAndEmpty() {
        assertThat(client.sha256((String) null)).isNull();
        assertThat(client.sha256("")).isEqualTo(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    // ===== ObjectInfo / UriParts =====

    @Test
    @DisplayName("ObjectInfo.uri() возвращает minio://bucket/key")
    void objectInfo_uri() {
        StorageClient.ObjectInfo info = new StorageClient.ObjectInfo(
                "iktk-files", "a/b/c", 100L, "text/plain", "etag-1", null, java.util.Map.of());
        assertThat(info.uri()).isEqualTo("minio://iktk-files/a/b/c");
    }

    @Test
    @DisplayName("UriParts хранит bucket и objectKey")
    void uriParts_record() {
        StorageClient.UriParts parts = new StorageClient.UriParts("b", "k");
        assertThat(parts.bucket()).isEqualTo("b");
        assertThat(parts.objectKey()).isEqualTo("k");
    }

    // ===== StorageException =====

    @Test
    @DisplayName("StorageException: message и cause сохраняются")
    void storageException_constructors() {
        StorageClient.StorageException e1 = new StorageClient.StorageException("msg");
        assertThat(e1.getMessage()).isEqualTo("msg");
        assertThat(e1.getCause()).isNull();

        RuntimeException cause = new RuntimeException("root");
        StorageClient.StorageException e2 = new StorageClient.StorageException("msg2", cause);
        assertThat(e2.getMessage()).isEqualTo("msg2");
        assertThat(e2.getCause()).isSameAs(cause);
    }
}