package org.lamisplus.modules.sync.service;


import org.lamisplus.modules.biometric.domain.Biometric;
import org.lamisplus.modules.sync.dto.BiometricDTO;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
public class BiometricDTOToBiometricMapper implements Function<BiometricDTO, Biometric> {
	@Override
	public Biometric apply(BiometricDTO biometricDTO) {
		Biometric biometric = new Biometric();
		biometric.setArchived(biometricDTO.getArchived());
		biometric.setBiometricType(biometricDTO.getBiometricType());
		biometric.setDeviceName(biometricDTO.getDeviceName());
		biometric.setDate(biometricDTO.getDate());
		biometric.setTemplate(biometricDTO.getTemplate());
		biometric.setTemplateType(biometricDTO.getTemplateType());
		biometric.setIso(biometricDTO.getIso());
		biometric.setPersonUuid(biometricDTO.getPersonUuid());
		biometric.setExtra(biometricDTO.getExtra());
		biometric.setReason(biometricDTO.getReason());
		return biometric;
	}
}
