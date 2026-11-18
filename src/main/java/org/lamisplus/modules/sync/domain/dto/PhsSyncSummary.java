package org.lamisplus.modules.sync.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhsSyncSummary {

    private int personCreated;
    private int personUpdated;
    private int personFailed;

    private int htsCreated;
    private int htsSkipped;
    private int htsFailed;

    private int ictCreated;
    private int ictSkipped;
    private int ictFailed;

    private int pmtctHtsCreated;
    private int pmtctHtsSkipped;
    private int pmtctHtsFailed;

    private int pmtctHtsRetestingCreated;
    private int pmtctHtsRetestingSkipped;
    private int pmtctHtsRetestingFailed;

    private int pmtctEnrollmentAncCreated;
    private int pmtctEnrollmentAncSkipped;
    private int pmtctEnrollmentAncFailed;

    private int pmtctLabourDeliveryCreated;
    private int pmtctLabourDeliverySkipped;
    private int pmtctLabourDeliveryFailed;

    private int pmtctAncRevisitCreated;
    private int pmtctAncRevisitSkipped;
    private int pmtctAncRevisitFailed;

    private int pmtctMipCreated;
    private int pmtctMipSkipped;
    private int pmtctMipFailed;

    private int pmtctMotherFollowUpVisitCreated;
    private int pmtctMotherFollowUpVisitSkipped;
    private int pmtctMotherFollowUpVisitFailed;

    private int pmtctChildFollowUpVisitCreated;
    private int pmtctChildFollowUpVisitSkipped;
    private int pmtctChildFollowUpVisitFailed;

    private int pmtctInfantRegistrationCreated;
    private int pmtctInfantRegistrationSkipped;
    private int pmtctInfantRegistrationFailed;
}
