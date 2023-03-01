package org.lamisplus.modules.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder

public class QuickSyncHistoryDTO {
	String filename;
	String facilityName;
	String tableName;
	LocalDateTime dateUpdated;
	Integer fileSize;
	String status;
}
