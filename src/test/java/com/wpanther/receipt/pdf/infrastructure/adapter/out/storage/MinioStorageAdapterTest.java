package com.wpanther.receipt.pdf.infrastructure.adapter.out.storage;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioStorageAdapterTest {

    @Mock
    private S3Client s3Client;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private MinioStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MinioStorageAdapter(s3Client, "receipts", "http://localhost:9000/receipts", meterRegistry);
    }

    @Test
    void store_uploadsAndReturnsS3Key() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = adapter.store("RCP-001", new byte[]{1, 2, 3});

        assertThat(key).matches("\\d{4}/\\d{2}/\\d{2}/receipt-RCP-001-.+\\.pdf");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void resolveUrl_prependsBaseUrl() {
        String url = adapter.resolveUrl("2024/01/15/file.pdf");
        assertThat(url).isEqualTo("http://localhost:9000/receipts/2024/01/15/file.pdf");
    }

    @Test
    void delete_callsS3DeleteObject() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        assertThatNoException().isThrownBy(() -> adapter.delete("2024/01/15/file.pdf"));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_s3Failure_throwsException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> adapter.delete("bad-key"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void store_preservesThaiCharacters() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = adapter.store("INV-ไทย-001", new byte[]{1, 2, 3});

        assertThat(key).contains("receipt-INV-ไทย-001-");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void store_sanitizesProblematicCharacters() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = adapter.store("INV:test<>|001", new byte[]{1, 2, 3});

        // Should replace : < > | with underscores
        assertThat(key).contains("receipt-INV_test___001-");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
