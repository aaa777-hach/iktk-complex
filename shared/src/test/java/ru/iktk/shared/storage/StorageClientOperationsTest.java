package ru.iktk.shared.storage;

import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StorageClient — операции с MinIO (мок MinioClient)")
class StorageClientOperationsTest {

    @Mock private MinioClient minioClient;

    private StorageClient client;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        client = new StorageClient(minioClient);
        tempDir = Files.createTempDirectory("iktk-storage-test-");
        ReflectionTestUtils.setField(client, "defaultBucket", "iktk-files");
        ReflectionTestUtils.setField(client, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(client, "maxFileSize", 10_000_000L);
        ReflectionTestUtils.setField(client, "presignedTtlMinutes", 15L);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }

    private Path tmpFile(String content) throws Exception {
        Path f = Files.createTempFile("sc-test-", ".txt");
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private GetObjectResponse objectResponse(String content) {
        return new GetObjectResponse(
                new Headers.Builder().build(), "test-bucket", "us-east-1", "k",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    // ===== upload(bucket, key, Path) =====

    @Test
    @DisplayName("upload: файл существует -> putObject вызван")
    void upload_ok() throws Exception {
        Path file = tmpFile("hello");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            client.upload("test-bucket", "k.txt", file);
            verify(minioClient).putObject(any(PutObjectArgs.class));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("upload: null file -> StorageException")
    void upload_nullFile_throws() {
        assertThatThrownBy(() -> client.upload("test-bucket", "k", null))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("не задан");
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("upload: пустой файл -> StorageException")
    void upload_emptyFile_throws() throws Exception {
        Path empty = Files.createTempFile("sc-empty-", ".txt");
        try {
            assertThatThrownBy(() -> client.upload("test-bucket", "k", empty))
                    .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("пуст");
        } finally {
            Files.deleteIfExists(empty);
        }
    }

    @Test
    @DisplayName("upload: файл больше maxFileSize -> StorageException")
    void upload_tooLarge_throws() throws Exception {
        ReflectionTestUtils.setField(client, "maxFileSize", 2L);
        Path file = tmpFile("hello");
        try {
            assertThatThrownBy(() -> client.upload("test-bucket", "k", file))
                    .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("превышает");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("upload: null bucket -> StorageException")
    void upload_nullBucket_throws() throws Exception {
        Path file = tmpFile("x");
        try {
            assertThatThrownBy(() -> client.upload(null, "k", file))
                    .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("bucket");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("upload: blank key -> StorageException")
    void upload_blankKey_throws() throws Exception {
        Path file = tmpFile("x");
        try {
            assertThatThrownBy(() -> client.upload("test-bucket", "   ", file))
                    .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("objectKey");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("upload: putObject бросает -> StorageException")
    void upload_putObjectThrows_throws() throws Exception {
        Path file = tmpFile("hello");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            when(minioClient.putObject(any(PutObjectArgs.class)))
                    .thenThrow(new RuntimeException("boom"));
            assertThatThrownBy(() -> client.upload("test-bucket", "k", file))
                    .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("загрузки");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ===== uploadTextToBucket =====

    @Test
    @DisplayName("uploadTextToBucket: null content -> StorageException")
    void uploadText_nullContent_throws() {
        assertThatThrownBy(() -> client.uploadTextToBucket("test-bucket", "k", null, null))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("не задано");
    }

    @Test
    @DisplayName("uploadTextToBucket: норма -> putObject вызван")
    void uploadText_ok() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        client.uploadTextToBucket("test-bucket", "k.txt", "hello", null);
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    // ===== download =====

    @Test
    @DisplayName("download: норма -> файл создан, содержимое верное")
    void download_ok() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse("hello"));
        Path p = client.download("test-bucket", "k.txt");
        assertThat(p).exists();
        assertThat(Files.readString(p)).isEqualTo("hello");
    }

    @Test
    @DisplayName("download: ErrorResponseException -> StorageException 'не найден'")
    void download_errorResponse_throws() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(mock(ErrorResponseException.class));
        assertThatThrownBy(() -> client.download("test-bucket", "k"))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("downloadTo: null target -> StorageException")
    void downloadTo_nullTarget_throws() {
        assertThatThrownBy(() -> client.downloadTo("test-bucket", "k", null))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("целевой файл");
    }

    @Test
    @DisplayName("downloadTo: норма -> файл записан в target")
    void downloadTo_ok() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse("data"));
        Path target = tempDir.resolve("out.txt");
        client.downloadTo("test-bucket", "k", target);
        assertThat(target).exists();
        assertThat(Files.readString(target)).isEqualTo("data");
    }

    @Test
    @DisplayName("downloadText: норма -> строка содержимого")
    void downloadText_ok() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse("hello"));
        assertThat(client.downloadText("test-bucket", "k")).isEqualTo("hello");
    }

    @Test
    @DisplayName("downloadText: исключение -> StorageException")
    void downloadText_error_throws() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> client.downloadText("test-bucket", "k"))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("чтения");
    }

    // ===== delete / exists / stat =====

    @Test
    @DisplayName("delete: removeObject вызван")
    void delete_ok() throws Exception {
        client.delete("test-bucket", "k");
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("delete: исключение -> StorageException")
    void delete_error_throws() throws Exception {
        doThrow(new RuntimeException("boom")).when(minioClient).removeObject(any(RemoveObjectArgs.class));
        assertThatThrownBy(() -> client.delete("test-bucket", "k"))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("удаления");
    }

    @Test
    @DisplayName("exists: null bucket/key -> false")
    void exists_null_returnsFalse() {
        assertThat(client.exists(null, "k")).isFalse();
        assertThat(client.exists("test-bucket", null)).isFalse();
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("exists: statObject OK -> true")
    void exists_ok_true() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));
        assertThat(client.exists("test-bucket", "k")).isTrue();
    }

    @Test
    @DisplayName("exists: ErrorResponseException -> false")
    void exists_errorResponse_false() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(mock(ErrorResponseException.class));
        assertThat(client.exists("test-bucket", "k")).isFalse();
    }

    @Test
    @DisplayName("stat: норма -> ObjectInfo с полями")
    void stat_ok() throws Exception {
        StatObjectResponse s = mock(StatObjectResponse.class);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        when(s.size()).thenReturn(42L);
        when(s.contentType()).thenReturn("text/plain");
        when(s.etag()).thenReturn("etag-1");
        when(s.lastModified()).thenReturn(now);
        when(s.userMetadata()).thenReturn(Map.of("k", "v"));
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(s);

        StorageClient.ObjectInfo info = client.stat("test-bucket", "obj");
        assertThat(info.bucket()).isEqualTo("test-bucket");
        assertThat(info.objectKey()).isEqualTo("obj");
        assertThat(info.size()).isEqualTo(42L);
        assertThat(info.contentType()).isEqualTo("text/plain");
        assertThat(info.etag()).isEqualTo("etag-1");
        assertThat(info.lastModified()).isEqualTo(now.toInstant());
        assertThat(info.userMetadata()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("stat: исключение -> StorageException")
    void stat_error_throws() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> client.stat("test-bucket", "k"))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("метаданных");
    }

    // ===== list =====

    @Test
    @DisplayName("list: пустой результат")
    void list_empty() throws Exception {
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());
        assertThat(client.list("test-bucket", "prefix/")).isEmpty();
    }

    @Test
    @DisplayName("list: исключение -> StorageException")
    void list_error_throws() throws Exception {
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> client.list("test-bucket", null))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("списка");
    }

    // ===== copy / move / compose =====

    @Test
    @DisplayName("copy: ensureBucket + copyObject")
    void copy_ok() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        client.copy("src", "s", "dst", "d");
        verify(minioClient).copyObject(any(CopyObjectArgs.class));
    }

    @Test
    @DisplayName("move: copyObject + removeObject")
    void move_ok() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        client.move("src", "s", "dst", "d");
        verify(minioClient).copyObject(any(CopyObjectArgs.class));
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("compose: пустые sources -> StorageException")
    void compose_empty_throws() {
        assertThatThrownBy(() -> client.compose("test-bucket", "target", List.of()))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("исходные");
        assertThatThrownBy(() -> client.compose("test-bucket", "target", null))
                .isInstanceOf(StorageClient.StorageException.class);
    }

    @Test
    @DisplayName("compose: норма -> composeObject")
    void compose_ok() throws Exception {
        client.compose("test-bucket", "target", List.of("a", "test-bucket"));
        verify(minioClient).composeObject(any(ComposeObjectArgs.class));
    }

    // ===== presigned =====

    @Test
    @DisplayName("presignedGetUrl: возвращает URL")
    void presignedGetUrl_ok() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio/get");
        assertThat(client.presignedGetUrl("test-bucket", "k", null)).isEqualTo("https://minio/get");
    }

    @Test
    @DisplayName("presignedGetUrl: исключение -> StorageException")
    void presignedGetUrl_error_throws() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> client.presignedGetUrl("test-bucket", "k", Duration.ofMinutes(5)))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("GET");
    }

    @Test
    @DisplayName("presignedPutUrl: возвращает URL")
    void presignedPutUrl_ok() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio/put");
        assertThat(client.presignedPutUrl("test-bucket", "k", Duration.ofMinutes(5))).isEqualTo("https://minio/put");
    }

    // ===== buckets =====

    @Test
    @DisplayName("bucketExists: null/blank -> false")
    void bucketExists_null_returnsFalse() {
        assertThat(client.bucketExists(null)).isFalse();
        assertThat(client.bucketExists("   ")).isFalse();
    }

    @Test
    @DisplayName("bucketExists: OK")
    void bucketExists_ok() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        assertThat(client.bucketExists("test-bucket")).isTrue();
    }

    @Test
    @DisplayName("bucketExists: исключение -> false")
    void bucketExists_error_false() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("boom"));
        assertThat(client.bucketExists("test-bucket")).isFalse();
    }

    @Test
    @DisplayName("ensureBucket: null/blank -> StorageException")
    void ensureBucket_null_throws() {
        assertThatThrownBy(() -> client.ensureBucket(null))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("bucket");
        assertThatThrownBy(() -> client.ensureBucket("   "))
                .isInstanceOf(StorageClient.StorageException.class);
    }

    @Test
    @DisplayName("ensureBucket: bucket уже есть -> makeBucket не вызван")
    void ensureBucket_exists_noMake() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        client.ensureBucket("test-bucket");
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("ensureBucket: bucket нет -> makeBucket вызван")
    void ensureBucket_missing_make() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        client.ensureBucket("test-bucket");
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("deleteBucket: removeBucket вызван")
    void deleteBucket_ok() throws Exception {
        client.deleteBucket("test-bucket");
        verify(minioClient).removeBucket(any(RemoveBucketArgs.class));
    }

    @Test
    @DisplayName("deleteBucket: исключение -> StorageException")
    void deleteBucket_error_throws() throws Exception {
        doThrow(new RuntimeException("boom")).when(minioClient).removeBucket(any(RemoveBucketArgs.class));
        assertThatThrownBy(() -> client.deleteBucket("test-bucket"))
                .isInstanceOf(StorageClient.StorageException.class).hasMessageContaining("удаления");
    }

    // ===== удобные обёртки =====

    @Test
    @DisplayName("upload(Path, projectId, category) -> URI")
    void upload_convenience() throws Exception {
        Path file = tmpFile("data");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            String uri = client.upload(file, "p1", "cnc");
            assertThat(uri).startsWith("minio://iktk-files/p1/cnc/");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("uploadText -> URI")
    void uploadText_convenience() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        String uri = client.uploadText("content", "p1", "cnc", "prog.nc");
        assertThat(uri).startsWith("minio://iktk-files/p1/cnc/").endsWith("-prog.nc");
    }

    @Test
    @DisplayName("download(uri) парсит URI")
    void download_uri() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse("hi"));
        Path p = client.download("minio://iktk-files/p1/cnc/file.txt");
        assertThat(Files.readString(p)).isEqualTo("hi");
    }

    @Test
    @DisplayName("downloadText(uri) парсит URI")
    void downloadText_uri() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse("hi"));
        assertThat(client.downloadText("minio://test-bucket/k")).isEqualTo("hi");
    }

    @Test
    @DisplayName("delete(uri) парсит URI")
    void delete_uri() throws Exception {
        client.delete("minio://test-bucket/k");
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("exists(uri): норма true")
    void exists_uri_true() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));
        assertThat(client.exists("minio://test-bucket/k")).isTrue();
    }

    @Test
    @DisplayName("exists(uri): некорректный URI -> false")
    void exists_uri_invalid_false() {
        assertThat(client.exists("minio://invalid")).isFalse();
        assertThat(client.exists("")).isFalse();
    }

    @Test
    @DisplayName("exists(uri): null -> false")
    void exists_uri_null_false() {
        assertThat(client.exists((String) null)).isFalse();
    }
}