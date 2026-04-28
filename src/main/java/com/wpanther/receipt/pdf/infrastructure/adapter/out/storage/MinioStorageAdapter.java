package com.wpanther.receipt.pdf.infrastructure.adapter.out.storage;

import com.wpanther.receipt.pdf.application.port.out.PdfStoragePort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MinIO/S3-backed implementation of {@link PdfStoragePort}.
 * All AWS SDK types are confined to this adapter.
 */
@Component
@Slf4j
public class MinioStorageAdapter implements PdfStoragePort {

    private final S3Client s3Client;
    private final String bucketName;
    private final String baseUrl;
    private final Timer storeTimer;
    private final Timer deleteTimer;

    public MinioStorageAdapter(
            S3Client s3Client,
            @Value("${app.minio.bucket-name}") String bucketName,
            @Value("${app.minio.base-url}") String baseUrl,
            MeterRegistry meterRegistry) {
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("not an absolute URL");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.minio.base-url is not a valid absolute URL: " + baseUrl, e);
        }
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.storeTimer = meterRegistry.timer("pdf.minio.store", "bucket", bucketName);
        this.deleteTimer = meterRegistry.timer("pdf.minio.delete", "bucket", bucketName);
    }

    @Override
    @CircuitBreaker(name = "minio")
    public String store(String receiptNumber, byte[] pdfBytes) {
        return storeTimer.record(() -> doStore(receiptNumber, pdfBytes));
    }

    @Override
    @CircuitBreaker(name = "minio")
    public void delete(String s3Key) {
        deleteTimer.record(() -> doDelete(s3Key));
    }

    @Override
    public String resolveUrl(String s3Key) {
        return baseUrl + "/" + s3Key;
    }

    private String doStore(String receiptNumber, byte[] pdfBytes) {
        LocalDate now = LocalDate.now();
        String safeName = sanitizeFilename(receiptNumber);
        String fileName = String.format("receipt-%s-%s.pdf", safeName, UUID.randomUUID());
        String s3Key = String.format("%04d/%02d/%02d/%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), fileName);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("application/pdf")
                .contentLength((long) pdfBytes.length)
                .build();

        s3Client.putObject(put, RequestBody.fromBytes(pdfBytes));
        log.debug("Uploaded PDF to MinIO: bucket={}, key={}", bucketName, s3Key);
        return s3Key;
    }

    /**
     * Sanitize document number for use in S3 object key.
     * <p>
     * Preserves Unicode characters (including Thai) while removing characters
     * problematic for file systems and URLs. S3 supports Unicode in object keys.
     * <p>
     * Removed characters:
     * - Control characters (0x00-0x1F, 0x7F)
     * - File system path separators: \ (backslash)
     * - URL problematic: " ' * ? < > | : & ; space
     * - Reserved for S3 special handling: @
     *
     * @param rawNumber the raw document number
     * @return sanitized filename safe for S3 and file systems
     */
    private String sanitizeFilename(String rawNumber) {
        // Remove control characters and problematic file system/URL characters
        // Preserves alphanumeric, Thai/Unicode characters, and safe special chars
        return rawNumber.replaceAll("[\\x00-\\x1F\\x7F\\\\\"'*?<>|:&; @]", "_")
                .replaceAll("\\s+", "_");  // Replace whitespace with single underscore
    }

    private void doDelete(String s3Key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build());
        log.info("Deleted PDF from MinIO: bucket={}, key={}", bucketName, s3Key);
    }

    /**
     * List all PDF objects in the MinIO bucket.
     * <p>
     * Used by periodic cleanup job to find orphaned objects.
     * This operation bypasses the circuit breaker as it's a maintenance task.
     *
     * @return list of all S3 object keys in the bucket
     */
    public List<String> listAllPdfs() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }

    /**
     * Delete an object from MinIO without circuit breaker protection.
     * <p>
     * Used by periodic cleanup job to remove orphaned objects.
     * Failures are logged but do not throw exceptions to avoid
     * interrupting batch cleanup operations.
     *
     * @param s3Key the S3 object key to delete
     */
    public void deleteWithoutCircuitBreaker(String s3Key) {
        try {
            doDelete(s3Key);
        } catch (Exception e) {
            log.warn("Failed to delete orphaned PDF from MinIO: bucket={}, key={}, error={}",
                    bucketName, s3Key, e.getMessage());
        }
    }
}
