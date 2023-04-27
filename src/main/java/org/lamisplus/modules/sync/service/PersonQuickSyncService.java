package org.lamisplus.modules.sync.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.lamisplus.modules.base.domain.entities.OrganisationUnit;
import org.lamisplus.modules.base.domain.repositories.OrganisationUnitRepository;
import org.lamisplus.modules.biometric.domain.Biometric;
import org.lamisplus.modules.biometric.repository.BiometricRepository;
import org.lamisplus.modules.patient.domain.entity.Person;
import org.lamisplus.modules.patient.repository.PersonRepository;
import org.lamisplus.modules.sync.domain.QuickSyncHistory;
import org.lamisplus.modules.sync.dto.BiometricDTO;
import org.lamisplus.modules.sync.dto.BiometricMetaDataDTO;
import org.lamisplus.modules.sync.dto.PersonDTO;
import org.lamisplus.modules.sync.dto.QuickSyncHistoryDTO;
import org.lamisplus.modules.sync.repository.QuickSyncHistoryRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.util.BeanUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PersonQuickSyncService {
	private final PersonRepository personRepository;
	private final  PersonDTOMapper personDTOMapper;
	private  final  PersonDTOToPersonMapper personMapper;
	private  final ObjectMapper mapper;
	private final OrganisationUnitRepository  organisationUnitRepository;
	
	private final QuickSyncHistoryRepository quickSyncHistoryRepository;
	
	private  final BiometricRepository biometricRepository;
	
	private final BiometricDTOMapper biometricDTOMapper;
	
	private final BiometricDTOToBiometricMapper biometricMapper;
	
	
	public Set<PersonDTO> getPersonDTO(Long facilityId, LocalDate start, LocalDate end) {
		// not best practice
		return personRepository.findAll().stream()
				.filter(person -> isPersonCreatedWithinDateRange(start, end, person.getCreatedDate()))
				.filter(person -> person.getFacilityId().equals(facilityId))
				.map(personDTOMapper)
				.collect(Collectors.toSet());
	}
	public List<BiometricDTO> getBiometricDTO(Long facilityId, LocalDate start, LocalDate end) {
		// not best practice
		List<BiometricDTO> data = new ArrayList<BiometricDTO>();
		
		Set<BiometricMetaDataDTO> biometricRows = biometricRepository.findAll().stream()
				.filter(biometric -> isPersonCreatedWithinDateRange(start, end, biometric.getCreatedDate()))
				.filter(biometric -> biometric.getFacilityId().equals(facilityId))
				.map(biometricDTOMapper)
				.collect(Collectors.toSet());
		
		Set<String> persons = biometricRows.stream().map(BiometricMetaDataDTO::getPersonUuid).collect(Collectors.toSet());
		
		persons.forEach(personUuid -> {
			Optional<Person> personOp  =
					personRepository.getPersonByUuidAndFacilityIdAndArchived(personUuid, facilityId, 0);
			personOp.ifPresent(person -> {
				Set<BiometricMetaDataDTO> personBiometricRows =
						biometricRows.stream()
								.filter(biometricRow -> biometricRow.getPersonUuid().equals(person.getUuid()))
								.collect(Collectors.toSet());
				BiometricDTO bio = BiometricDTO.builder()
						.biometric(personBiometricRows)
						.person(personDTOMapper.getPersonDTO(person))
						.build();
				if(!data.contains(bio)) {
				    data.add(bio);
				}
				
			});
		});
		return data;
	}
	
	
	
	public ByteArrayOutputStream generatePersonData(HttpServletResponse response, Long facilityId,  LocalDate start, LocalDate end){
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		try{
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			Set<PersonDTO> personData = getPersonDTO(facilityId, start, end);
			byte[] personDataBytes = mapper.writeValueAsBytes(personData);
			bao.write(personDataBytes);
		}catch (Exception e){
			e.printStackTrace();
		}
		return bao;
	}
	
	public QuickSyncHistoryDTO importPersonData(Long facilityId, MultipartFile file) throws IOException {
		byte[] bytes = file.getBytes();
		String data = new String(bytes, StandardCharsets.UTF_8);
		OrganisationUnit facility = organisationUnitRepository.getOne(facilityId);
		List<PersonDTO> personDTOS = mapper.readValue(data, new TypeReference<List<PersonDTO>>() {
		});
		personDTOS.stream()
				.map(personMapper)
				.forEach(person -> {
					Optional<Person> existPerson =
							personRepository.getPersonByUuidAndFacilityIdAndArchived(person.getUuid(), person.getFacilityId(), 0);
					if(existPerson.isPresent()) {
						Person person1 = existPerson.get();
						PersonDTO personDTO = convertPersonDTO(person);
						BeanUtils.copyProperties(personDTO, person1);
						personRepository.save(person1);
					}else {
						personRepository.save(person);
					}
				});
		return getQuickSyncHistoryDTO(file, facility, personDTOS.size(), "person");
		
		
	}
	private static void writeDataToFile(Long facilityId, byte[] personDataBytes, Date date) throws IOException {
		SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyy");
		SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss.ms");
		String folder = ("quicksync/").concat(Long.toString(facilityId).concat("/")).concat("person").concat("/");
		String fileName = dateFormat.format(date) + "_" + timeFormat.format(date) + ".json";
		File file = new File(folder.concat(fileName));
		FileUtils.writeByteArrayToFile(file, personDataBytes);
	}
	
	private static boolean isPersonCreatedWithinDateRange(
			LocalDate start,
			LocalDate end,
			LocalDateTime personCreatedDate) {
		LocalDateTime startDate = start.atTime(0, 0);
		LocalDateTime endDate = end.atTime(23, 59);
		return personCreatedDate.isAfter(startDate)
				&& personCreatedDate.isBefore(endDate);
	}
	
	
	public List<QuickSyncHistory> getQuickSyncHistory() {
		return quickSyncHistoryRepository.findAll();
	}
	
	private PersonDTO convertPersonDTO(Person person) {
		return personDTOMapper.getPersonDTO(person);
		
	}
	
	
//	public Object importBiometricData(Long facility, MultipartFile file) {
//
//	}
	
	public ByteArrayOutputStream generateBiometricData(HttpServletResponse response, Long facilityId, LocalDate start, LocalDate end) {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		try{
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			List<BiometricDTO> personData = getBiometricDTO(facilityId, start, end);
			byte[] personDataBytes = mapper.writeValueAsBytes(personData);
			bao.write(personDataBytes);
		}catch (Exception e){
			e.printStackTrace();
		}
		return bao;
	}
	
	public QuickSyncHistoryDTO importBiometricData(Long facilityId, MultipartFile file) throws IOException {
		byte[] bytes = file.getBytes();
		String data = new String(bytes, StandardCharsets.UTF_8);
		OrganisationUnit facility = organisationUnitRepository.getOne(facilityId);
		List<BiometricDTO> biometrics = mapper.readValue(data, new TypeReference<List<BiometricDTO>>() {});
		
		biometrics.parallelStream()
				.forEach(biometricDTO -> {
					Optional<Person> existPerson =
							personRepository.getPersonByUuidAndFacilityIdAndArchived(biometricDTO.getPerson().getUuid(),
									biometricDTO.getPerson().getFacilityId(), 0);
					if(!existPerson.isPresent()) {
						Person person = personMapper.getPersonFromDTO(biometricDTO.getPerson());
						personRepository.save(person);
					}
					List<Biometric> existBiometrics = biometricRepository.findAllByPersonUuid(existPerson.get().getUuid());
					
					List<Biometric> currentBiometrics = biometricDTO.getBiometric()
							.stream()
							.map(biometricMapper).collect(Collectors.toList());
					
					if(existBiometrics.size() != currentBiometrics.size()){
						currentBiometrics.forEach(biometricRepository::save);
					}
				});
		return getQuickSyncHistoryDTO(file, facility, biometrics.size(), "biometric");
	}
	
	@NotNull
	private QuickSyncHistoryDTO getQuickSyncHistoryDTO(MultipartFile file, OrganisationUnit facility, int filesize, String tableName) {
		QuickSyncHistoryDTO historyDTO = QuickSyncHistoryDTO.builder()
				.status("completed")
				.filename(file.getOriginalFilename())
				.facilityName(facility.getName())
				.tableName(tableName)
				.fileSize(filesize)
				.dateUpdated(LocalDateTime.now())
				.build();
		QuickSyncHistory quickSyncHistory = new QuickSyncHistory();
		quickSyncHistory.setFilename(historyDTO.getFilename());
		quickSyncHistory.setStatus("completed");
		quickSyncHistory.setTableName(historyDTO.getTableName());
		quickSyncHistory.setFileSize(historyDTO.getFileSize());
		quickSyncHistory.setFilename(file.getOriginalFilename());
		quickSyncHistory.setFacilityName(historyDTO.getFacilityName());
		quickSyncHistory.setDateCreated(historyDTO.getDateUpdated());
		quickSyncHistoryRepository.save(quickSyncHistory);
		return historyDTO;
	}
}
