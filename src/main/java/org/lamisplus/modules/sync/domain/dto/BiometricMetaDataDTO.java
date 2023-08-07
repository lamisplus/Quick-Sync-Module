package org.lamisplus.modules.sync.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hibernate.annotations.Type;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
public class BiometricMetaDataDTO {
	private String personUuid;
	@NotNull
	private byte[] template;
	@NotNull
	private String biometricType;
	@NotNull
	private String templateType;
	@NotNull
	@JsonSerialize(using = LocalDateToStringSerializer.class)
	private LocalDate date;
	private Integer archived = 0;
	private Boolean iso = false;
	@Type(type = "jsonb-node")
	private JsonNode extra;
	private String deviceName;
	private String reason;
	private Integer imageQuality=0;
	private Integer recapture;
	private String recaptureMessage;
	private String hashed;
	private Integer count;
}
