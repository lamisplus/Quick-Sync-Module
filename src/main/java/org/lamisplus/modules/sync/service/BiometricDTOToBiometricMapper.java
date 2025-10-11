package org.lamisplus.modules.sync.service;


import lombok.extern.slf4j.Slf4j;
import org.audit4j.core.util.Log;
import org.lamisplus.modules.base.domain.entities.User;
import org.lamisplus.modules.base.service.UserService;
import org.lamisplus.modules.biometric.domain.Biometric;
import org.lamisplus.modules.sync.domain.dto.BiometricMetaDataDTO;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;

@Service
@Slf4j
public class BiometricDTOToBiometricMapper implements Function<BiometricMetaDataDTO, Biometric> {
	private final UserService userService;

    public BiometricDTOToBiometricMapper(UserService userService) {
        this.userService = userService;
    }

    @Override
	public Biometric apply(BiometricMetaDataDTO biometricDTO) {
		int recapture = biometricDTO.getRecapture() == null ? 0 : biometricDTO.getRecapture();
		Biometric biometric = new Biometric();
		biometric.setArchived(biometricDTO.getArchived());
		biometric.setVersionIso20(true);
		biometric.setCreatedBy(biometricDTO.getCreatedBy());
		biometric.setLastModifiedBy(biometricDTO.getLastModifiedBy());
		biometric.setBiometricType(biometricDTO.getBiometricType());
		biometric.setDeviceName(biometricDTO.getDeviceName());
		biometric.setDate(biometricDTO.getDate());
		biometric.setTemplate(biometricDTO.getTemplate());
		biometric.setTemplateType(biometricDTO.getTemplateType());
		biometric.setIso(biometricDTO.getIso());
		biometric.setPersonUuid(biometricDTO.getPersonUuid());
		biometric.setExtra(biometricDTO.getExtra());
		biometric.setRecapture(recapture);
		biometric.setImageQuality(biometricDTO.getImageQuality());
		biometric.setReason(biometricDTO.getReason());
		biometric.setCount(biometricDTO.getCount());
		biometric.setHashed(biometricDTO.getHashed());
		System.out.println("biometricDto: "+biometricDTO);
		return biometric;
	}
}
