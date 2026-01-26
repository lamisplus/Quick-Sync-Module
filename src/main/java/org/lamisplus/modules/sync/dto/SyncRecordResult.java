package org.lamisplus.modules.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRecordResult {
    private String hospitalNumber;
    private String riskStratificationCode; // To distinguish multiple HTS clients for same hospital number
    private String status; // COMPLETELY_SUCCESSFUL, PARTIALLY_SUCCESSFUL, COMPLETELY_FAILED, SKIPPED
    private String message;

    @Builder.Default
    private List<String> successfulComponents = new ArrayList<>();

    @Builder.Default
    private List<ComponentFailure> failedComponents = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentFailure {
        private String component; // e.g., "person", "htsClient", "familyIndexTesting"
        private String identifier; // e.g., contactId, partnerId, riskStratificationCode
        private String reason; // Human-readable error message
        private String errorCode; // For programmatic handling: DUPLICATE, VALIDATION_ERROR, DATABASE_ERROR, etc.
    }
}
