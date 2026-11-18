package org.lamisplus.modules.sync.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhsSyncResponse {

    private String fileName;
    private String facilityName;
    private LocalDateTime processedAt;

    private int totalRecords;
    private int completelySuccessful;
    private int partiallySuccessful;
    private int completelyFailed;
    private int skippedRecords;

    private PhsSyncSummary summary;

    private List<PhsSyncRecordResult> problemRecords;
    private List<PhsSyncRecordResult> successfulRecords;

    private long processingTimeMs;
}