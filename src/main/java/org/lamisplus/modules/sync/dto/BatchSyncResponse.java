package org.lamisplus.modules.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSyncResponse {
    private String fileName;
    private String facilityName;
    private LocalDateTime processedAt;

    private Integer totalRecords;
    private Integer completelySuccessful;
    private Integer partiallySuccessful;
    private Integer completelyFailed;
    private Integer skippedRecords;

    private SyncSummaryByType summary;

    @Builder.Default
    private List<SyncRecordResult> problemRecords = new ArrayList<>(); // Only failed/partial/skipped records

    @Builder.Default
    private List<SyncRecordResult> successfulRecords = new ArrayList<>(); // Completely successful records with hospitalNumber and riskStratificationCode

    private Long processingTimeMs;
}
