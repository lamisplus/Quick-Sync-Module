package org.lamisplus.modules.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncSummaryByType {
    private Integer personCreated;
    private Integer personUpdated;
    private Integer personFailed;

    private Integer htsClientCreated;
    private Integer htsClientUpdated;
    private Integer htsClientFailed;

    private Integer preTestCreated;
    private Integer preTestFailed;

    private Integer postTestCreated;
    private Integer postTestFailed;

    private Integer recencyCreated;
    private Integer recencyFailed;

    private Integer familyIndexCreated;
    private Integer familyIndexSkipped; // Duplicates
    private Integer familyIndexFailed;

    private Integer partnerNotificationCreated;
    private Integer partnerNotificationSkipped; // Duplicates
    private Integer partnerNotificationFailed;

    private Integer clientReferralCreated;
    private Integer clientReferralSkipped; // Duplicates
    private Integer clientReferralFailed;

    // PMTCT Forms
    private Integer ancCreated;
    private Integer ancSkipped; // Duplicates
    private Integer ancFailed;

    private Integer childFollowupCreated;
    private Integer childFollowupFailed;

    private Integer motherFollowupCreated;
    private Integer motherFollowupFailed;

    private Integer partnerRegistrationCreated;
    private Integer partnerRegistrationFailed;
}
