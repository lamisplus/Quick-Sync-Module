package org.lamisplus.modules.sync.service;

import org.lamisplus.modules.biometric.domain.Biometric;
import org.lamisplus.modules.sync.dto.BiometricDTO;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
public class BiometricDTOMapper  implements Function<Biometric, BiometricDTO> {
	@Override
	public BiometricDTO apply(Biometric biometric) {
		return  BiometricDTO.builder()
				.archived(biometric.getArchived())
				.biometricType(biometric.getBiometricType())
				.date(biometric.getDate())
				.extra(biometric.getExtra())
				.deviceName(biometric.getDeviceName())
				.iso(biometric.getIso())
				.personUuid(biometric.getPersonUuid())
				.template(biometric.getTemplate())
				.templateType(biometric.getTemplateType())
				.reason(biometric.getReason())
				.build();
	}
}
