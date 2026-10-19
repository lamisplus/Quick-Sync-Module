package org.lamisplus.modules.sync.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-record outcome tracker for {@code PHSQuickSyncService}.
 * One instance is created per zip entry (i.e. per person/hts/ict bundle).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhsSyncRecordResult {

    private String hospitalNumber;
    private String clientCode;
    private String ancNo;
    private String pmtctCycleUuid;

    private Long personId;
    private Long htsEncounterId;
    private Long ictEncounterId;
    private Long pmtctHtsEncounterId;
    private Long pmtctHtsRetestingEncounterId;
    private Long pmtctEnrollmentAncEncounterId;
    private Long pmtctRevisitEncounterId;
    private Long pmtctMotherFollowUpVisitEncounterId;
    private Long pmtctChildFollowUpVisitEncounterId;
    private Long infantRegistrationEncounterId;

    /** COMPLETELY_SUCCESSFUL | PARTIALLY_SUCCESSFUL | COMPLETELY_FAILED | SKIPPED */
    private String status;
    private String message;

    @Builder.Default
    private List<String> successfulComponents = new ArrayList<>();
    @Builder.Default
    private List<String> failedComponents = new ArrayList<>();
}
