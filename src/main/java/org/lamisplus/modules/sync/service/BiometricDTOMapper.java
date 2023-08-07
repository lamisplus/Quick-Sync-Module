package org.lamisplus.modules.sync.service;

import lombok.RequiredArgsConstructor;
import org.lamisplus.modules.biometric.domain.Biometric;
import org.lamisplus.modules.sync.domain.dto.BiometricMetaDataDTO;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BiometricDTOMapper  implements Function<Biometric, BiometricMetaDataDTO> {
	@Override
	public BiometricMetaDataDTO apply(Biometric biometric) {
			return	BiometricMetaDataDTO.builder()
				.archived(biometric.getArchived())
				.biometricType(biometric.getBiometricType())
				.date(biometric.getDate())
				.extra(biometric.getExtra())
				.deviceName(biometric.getDeviceName())
				.iso(biometric.getIso())
				.personUuid(biometric.getPersonUuid())
				.template(biometric.getTemplate())
				.templateType(biometric.getTemplateType())
				//.reason(biometric.getReason())
				.build();
	}
}
