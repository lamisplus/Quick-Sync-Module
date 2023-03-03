package org.lamisplus.modules.sync.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.lamisplus.modules.base.domain.entities.OrganisationUnit;
import org.lamisplus.modules.base.domain.repositories.OrganisationUnitRepository;
import org.lamisplus.modules.patient.domain.entity.Person;
import org.lamisplus.modules.patient.repository.PersonRepository;
import org.lamisplus.modules.sync.domain.QuickSyncHistory;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonQuickSyncService {
	private final PersonRepository personRepository;
	private final  PersonDTOMapper personDTOMapper;
	private  final  PersonDTOToPersonMapper personMapper;
	private  final ObjectMapper mapper;
	private final OrganisationUnitRepository  organisationUnitRepository;
	
	private final QuickSyncHistoryRepository quickSyncHistoryRepository;
	
	
	
	public Set<PersonDTO> getPersonDTO(Long facilityId, LocalDate start, LocalDate end) {
		// not best practice
		return personRepository.findAll().stream()
				.filter(person -> isPersonCreatedWithinDateRange(start, end, person.getCreatedDate()))
				.filter(person -> person.getFacilityId().equals(facilityId))
				.map(personDTOMapper)
				.collect(Collectors.toSet());
	}
	
	public ByteArrayOutputStream generatePersonData(HttpServletResponse response, Long facilityId,  LocalDate start, LocalDate end){
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		try{
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			Set<PersonDTO> personData = getPersonDTO(facilityId, start, end);
			byte[] personDataBytes = mapper.writeValueAsBytes(personData);
			Date date = new Date();
			//writeDataToFile(facilityId, personDataBytes, date);
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
						BeanUtils.copyProperties(person,person1);
						personRepository.save(person1);
					}else {
						personRepository.save(person);
					}
				});
		QuickSyncHistoryDTO historyDTO = QuickSyncHistoryDTO.builder()
				.status("completed")
				.filename(file.getOriginalFilename())
				.facilityName(facility.getName())
				.tableName("person")
				.fileSize(personDTOS.size())
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
		return  historyDTO;
	
		
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
		System.out.println("date created:"+ personCreatedDate);
		LocalDateTime startDate = start.atTime(0, 0);
		System.out.println("start: "+ startDate);
		LocalDateTime endDate = end.atTime(23, 59);
		System.out.println("end: "+ endDate);
		return personCreatedDate.isAfter(startDate)
				&& personCreatedDate.isBefore(endDate);
	}
	
	
	public List<QuickSyncHistory> getQuickSyncHistory() {
		return quickSyncHistoryRepository.findAll();
	}
}
