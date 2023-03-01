package org.lamisplus.modules.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder

public class PersonImportDTO {
	String filename;
	String facilityName;
	LocalDateTime dateUpdated;
	String status;
}
