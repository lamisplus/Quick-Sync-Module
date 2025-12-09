package org.lamisplus.modules.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.modules.sync.dto.BatchSyncResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSyncLogService {

    private final ObjectMapper objectMapper;

    @Value("${sync.log.directory:quick-sync-files}")
    private String logDirectory;

    /**
     * Logs the batch sync response to a JSON file
     *
     * @param response   The batch sync response to log
     * @param facilityId The facility ID
     * @param userId     The user ID who initiated the sync (optional)
     * @return The path to the created log file
     */
    public String logBatchSyncResponse(BatchSyncResponse response, Long facilityId, String userId) {
        try {
            // Create log directory structure: {baseDir}/{year}/{month}/{day}/
            LocalDateTime now = LocalDateTime.now();
            String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
            String month = now.format(DateTimeFormatter.ofPattern("MM"));
            String day = now.format(DateTimeFormatter.ofPattern("dd"));

            Path logPath = Paths.get(logDirectory, year, month, day);
            Files.createDirectories(logPath);

            // Generate filename with timestamp and status
            String timestamp = now.format(DateTimeFormatter.ofPattern("HHmmss"));
            String status = determineOverallStatus(response);
            String sanitizedFileName = sanitizeFileName(response.getFileName());
            String fileName = String.format("hts-batch-sync-%d-%s-%s-%s.json",
                    facilityId, timestamp, status, sanitizedFileName);

            File logFile = logPath.resolve(fileName).toFile();

            // Configure ObjectMapper for pretty printing
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // Create enhanced log object with metadata
            BatchSyncLogEntry logEntry = BatchSyncLogEntry.builder()
                    .loggedAt(LocalDateTime.now())
                    .userId(userId)
                    .facilityId(facilityId)
                    .response(response)
                    .build();

            // Write to file
            mapper.writeValue(logFile, logEntry);

//            log.info("Batch sync response logged to: {}", logFile.getAbsolutePath());
            return logFile.getAbsolutePath();

        } catch (IOException e) {
//            log.error("Failed to write batch sync log file for facility {}: {}", facilityId, e.getMessage(), e);
            // Don't throw exception - logging failure shouldn't break the sync process
            return null;
        }
    }

    /**
     * Determine overall status for filename
     */
    private String determineOverallStatus(BatchSyncResponse response) {
        if (response.getCompletelyFailed() > 0 && response.getCompletelySuccessful() == 0) {
            return "FAILED";
        } else if (response.getPartiallySuccessful() > 0 || response.getCompletelyFailed() > 0) {
            return "PARTIAL";
        } else {
            return "SUCCESS";
        }
    }

    /**
     * Sanitize filename to remove invalid characters
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        // Remove extension and sanitize
        String name = fileName.replaceAll("\\.[^.]+$", ""); // Remove extension
        name = name.replaceAll("[^a-zA-Z0-9-_]", "-"); // Replace invalid chars
        // Limit length
        return name.length() > 50 ? name.substring(0, 50) : name;
    }

    /**
     * Inner class to wrap the response with additional metadata
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchSyncLogEntry {
        private LocalDateTime loggedAt;
        private String userId;
        private Long facilityId;
        private BatchSyncResponse response;
    }
}
