package org.lamisplus.modules.sync.service;



import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MutableConfigOverride;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.lamisplus.modules.base.domain.entities.OrganisationUnit;
import org.lamisplus.modules.base.domain.repositories.OrganisationUnitRepository;
import org.lamisplus.modules.biometric.domain.Biometric;
import org.lamisplus.modules.sync.domain.dto.BiometricMetaDataDTO;
import org.lamisplus.modules.biometric.repository.BiometricRepository;
import org.lamisplus.modules.patient.domain.entity.Person;
import org.lamisplus.modules.patient.repository.PersonRepository;
import org.lamisplus.modules.sync.domain.QuickSyncHistory;
import org.lamisplus.modules.sync.domain.dto.BiometricDTO;
import org.lamisplus.modules.sync.domain.dto.PersonDTO;
import org.lamisplus.modules.sync.domain.dto.QuickSyncHistoryDTO;
import org.lamisplus.modules.sync.repository.QuickSyncHistoryRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonQuickSyncService {
	private final PersonRepository personRepository;
	private final  PersonDTOMapper personDTOMapper;
	private  final  PersonDTOToPersonMapper personMapper;
	private final OrganisationUnitRepository  organisationUnitRepository;
	
	private final QuickSyncHistoryRepository quickSyncHistoryRepository;
	
	private  final BiometricRepository biometricRepository;
	
	private final BiometricDTOMapper biometricDTOMapper;
	
	private final BiometricDTOToBiometricMapper biometricMapper;
	
	
	public Set<PersonDTO> getPersonDTO(Long facilityId, LocalDate start, LocalDate end) {
		// not best practice
		return personRepository.findAll().stream()
				.filter(person -> isPersonCreatedWithinDateRange(start, end, person.getCreatedDate()))
				.filter(person -> person.getFacilityId()!=null
						&& person.getFacilityId().equals(facilityId)
						&& person.getArchived() == 0)
				.map(personDTOMapper)
				.collect(Collectors.toSet());
	}
	public List<BiometricDTO> getBiometricDTO(Long facilityId, LocalDate start, LocalDate end) {
		// not best practice
		List<BiometricDTO> data = new ArrayList<BiometricDTO>();
		try {
			LOG.info("start fetching biometric details... start {} and end {}", start, end);
			List<Biometric> biometricRows1 = biometricRepository.findAll().stream()
					.filter(Objects::nonNull)
					.filter(biometric -> isPersonCreatedWithinDateRange(start, end, biometric.getCreatedDate()))
					.collect(Collectors.toList());
			LOG.info("biometric list {}", biometricRows1.size());
			
			List<BiometricMetaDataDTO> biometricRows = biometricRows1
					.stream()
					.filter(biometric -> biometric.getFacilityId() != null
							&& biometric.getFacilityId().equals(facilityId)
							&& biometric.getArchived() == 0
					)
					.map(biometricDTOMapper)
					.collect(Collectors.toList());
			LOG.info("biometricRows:{}", biometricRows.size());
			Set<String> persons = biometricRows
					.stream()
					.map(BiometricMetaDataDTO::getPersonUuid).collect(Collectors.toSet());
			
			
			persons.forEach(personUuid -> {
				Optional<Person> personOp =
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
					if (!data.contains(bio)) {
						data.add(bio);
					}
					
				});
			});
		}catch (Exception e) {
			LOG.info("An error occurred while fetching biometric details  error {}", e.getMessage());
			e.printStackTrace();
		}
		return data;
	}
	
	
	
	public ByteArrayOutputStream generatePersonData(HttpServletResponse response, Long facilityId,  LocalDate start, LocalDate end){
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		try{
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			Set<PersonDTO> personData = getPersonDTO(facilityId, start, end.plusDays(1));
			byte[] personDataBytes = mapper.writeValueAsBytes(personData);
			bao.write(personDataBytes);
		}catch (Exception e){
			e.printStackTrace();
		}
		return bao;
	}
	
	public QuickSyncHistoryDTO importPersonData(Long facilityId, MultipartFile file) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.findAndRegisterModules();
		configureMapperToHandleDate(mapper);
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
				&& personCreatedDate.isBefore(endDate.minusDays(1));
	}
	
	
	public List<QuickSyncHistory> getQuickSyncHistory() {
		
		return quickSyncHistoryRepository.findAll()
				.stream()
				.sorted(Comparator.comparing(QuickSyncHistory::getDateCreated).reversed())
				.collect(Collectors.toList());
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
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			List<BiometricDTO> personData = getBiometricDTO(facilityId, start, end.plusDays(1));
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
		ObjectMapper mapper = new ObjectMapper();
		mapper.findAndRegisterModules();
		configureMapperToHandleDate(mapper);
		List<BiometricDTO> biometrics = mapper.readValue(data, new TypeReference<List<BiometricDTO>>() {});
		//LOG.info("data imported {}", biometrics);
		LOG.info("biometric List: " + biometrics.size());
		AtomicInteger total = new AtomicInteger();
		int notSave = 0;
		try {
			biometrics.parallelStream()
					.forEach(biometricDTO -> {
						Optional<Person> existPerson =
								personRepository.getPersonByUuidAndFacilityIdAndArchived(biometricDTO.getPerson().getUuid(),
										biometricDTO.getPerson().getFacilityId(), 0);
						if (!existPerson.isPresent()) {
							Person person = personMapper.getPersonFromDTO(biometricDTO.getPerson());
							personRepository.save(person);
						}
						List<Biometric> currentBiometrics = biometricDTO.getBiometric()
								.stream()
								.map(biometricMapper)
								.map(b -> {
									b.setFacilityId(facilityId);
									return b;
								})
								.collect(Collectors.toList());
						currentBiometrics.parallelStream().forEach(b ->
						{
							LOG.info("id {}", b.getId());
							List<Biometric> existBiometric =
									biometricRepository.findAllByPersonUuid(b.getPersonUuid());
							boolean alreadySaved = existBiometric.stream()
									.anyMatch(eb -> eb.getCreatedBy().equals(b.getCreatedBy()));
							
							if (alreadySaved) {
							} else {
								biometricRepository.save(b);
								total.getAndIncrement();
							}
						});
					});
		}catch (Exception e) {
			notSave++;
			e.printStackTrace();
		}
		LOG.error("not saved", notSave);
		return getQuickSyncHistoryDTO(file, facility, total.get(), "biometric");
	}
	
	private static MutableConfigOverride configureMapperToHandleDate(ObjectMapper mapper) {
		return mapper.configOverride(LocalDate.class);
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
