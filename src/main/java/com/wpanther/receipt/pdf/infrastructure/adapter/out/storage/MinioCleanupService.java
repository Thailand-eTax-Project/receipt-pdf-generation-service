package com.wpanther.receipt.pdf.infrastructure.adapter.out.storage;

import com.wpanther.receipt.pdf.infrastructure.adapter.out.persistence.JpaReceiptPdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.minio.cleanup.enabled", havingValue = "true", matchIfMissing = false)
public class MinioCleanupService {

    private final MinioStorageAdapter minioStorage;
    private final JpaReceiptPdfDocumentRepository repository;

    @Scheduled(cron = "${app.minio.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOrphanedPdfs() {
        log.info("Starting orphaned PDF cleanup job");
        try {
            List<String> minioKeys = minioStorage.listAllPdfs();
            log.debug("Found {} PDF objects in MinIO", minioKeys.size());
            Set<String> databaseKeys = repository.findAllDocumentPaths();
            log.debug("Found {} document paths in database", databaseKeys.size());
            List<String> orphanedKeys = minioKeys.stream()
                    .filter(key -> !databaseKeys.contains(key))
                    .toList();
            if (orphanedKeys.isEmpty()) {
                log.info("No orphaned PDFs found");
                return;
            }
            log.warn("Found {} orphaned PDF(s) to delete: {}", orphanedKeys.size(), orphanedKeys);
            int deletedCount = 0;
            for (String key : orphanedKeys) {
                minioStorage.deleteWithoutCircuitBreaker(key);
                deletedCount++;
            }
            log.info("Orphaned PDF cleanup completed: {} of {} objects deleted",
                    deletedCount, orphanedKeys.size());
        } catch (Exception e) {
            log.error("Orphaned PDF cleanup job failed: {}", e.getMessage(), e);
        }
    }
}
