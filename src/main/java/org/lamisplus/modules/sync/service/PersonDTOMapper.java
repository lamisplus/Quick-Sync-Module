package org.lamisplus.modules.sync.service;

import org.lamisplus.modules.patient.domain.entity.Person;
import org.lamisplus.modules.sync.domain.dto.PersonDTO;
import org.springframework.stereotype.Service;

import java.util.function.Function;


@Service
public class PersonDTOMapper implements Function<Person, PersonDTO> {
	
	@Override
	public PersonDTO apply(Person person) {
		return getPersonDTO(person);
		
	}
	
	public  PersonDTO getPersonDTO(Person person) {
		return PersonDTO.builder()
				.active(person.getActive())
				.address(person.getAddress())
				.archived(person.getArchived())
				.contact(person.getContact())
				.contactPoint(person.getContactPoint())
				.dateOfBirth(person.getDateOfBirth())
				.dateOfRegistration(person.getDateOfRegistration())
				.deceasedDateTime(person.getDeceasedDateTime())
				.deceased(person.getDeceased())
				.emrId(person.getEmrId())
				.education(person.getEducation())
				.employmentStatus(person.getEmploymentStatus())
				.firstName(person.getFirstName())
				.fullName(person.getFullName())
				.facilityId(person.getFacilityId())
				.gender(person.getGender())
				.hospitalNumber(person.getHospitalNumber())
				.isDateOfBirthEstimated(person.getIsDateOfBirthEstimated())
				.identifier(person.getIdentifier())
				.maritalStatus(person.getMaritalStatus())
				.ninNumber(person.getNinNumber())
				.otherName(person.getOtherName())
				.organization(person.getOrganization())
				.sex(person.getSex())
				.surname(person.getSurname())
				.uuid(person.getUuid())
				.build();
	}
}
