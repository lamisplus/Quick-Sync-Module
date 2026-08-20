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
}
