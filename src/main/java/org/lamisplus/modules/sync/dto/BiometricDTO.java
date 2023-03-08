package org.lamisplus.modules.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricDTO implements Serializable {
	private PersonDTO person;
	 private Set<BiometricMetaDataDTO> biometric;
	
}
