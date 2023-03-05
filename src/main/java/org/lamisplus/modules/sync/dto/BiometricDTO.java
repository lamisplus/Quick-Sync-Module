package org.lamisplus.modules.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricDTO implements Serializable {
	private String personUuid;
	@NotNull
	private byte[] template;
	@NotNull
	private String biometricType;
	@NotNull
	private String templateType;
	@NotNull
	private LocalDate date;
	private Integer archived = 0;
	private Boolean iso = false;
	@Type(type = "jsonb-node")
	private JsonNode extra;
	private String deviceName;
	private String reason;
	
}
