package org.lamisplus.modules.sync.service;

import org.lamisplus.modules.patient.domain.entity.Person;
import org.lamisplus.modules.sync.dto.PersonDTO;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
public class PersonDTOToPersonMapper  implements Function<PersonDTO , Person> {
	@Override
	public Person apply(PersonDTO personDTO) {
		Person person = new Person();
		person.setArchived(personDTO.getArchived());
		person.setActive(personDTO.getActive());
		person.setAddress(personDTO.getAddress());
		person.setContact(personDTO.getContact());
		person.setContactPoint(personDTO.getContactPoint());
		person.setDateOfBirth(personDTO.getDateOfBirth());
		person.setDeceased(personDTO.getDeceased());
		person.setDateOfRegistration(personDTO.getDateOfRegistration());
		person.setDeceasedDateTime(personDTO.getDeceasedDateTime());
		person.setEducation(personDTO.getEducation());
		person.setEmrId(personDTO.getEmrId());
		person.setEmploymentStatus(personDTO.getEmploymentStatus());
		person.setFirstName(personDTO.getFirstName());
		person.setFullName(personDTO.getFullName());
		person.setFacilityId(personDTO.getFacilityId());
		person.setGender(personDTO.getGender());
		person.setHospitalNumber(personDTO.getHospitalNumber());
		person.setIdentifier(personDTO.getIdentifier());
		person.setIsDateOfBirthEstimated(personDTO.getIsDateOfBirthEstimated());
		person.setMaritalStatus(personDTO.getMaritalStatus());
		person.setNinNumber(personDTO.getNinNumber());
		person.setOrganization(personDTO.getOrganization());
		person.setOtherName(personDTO.getOtherName());
		person.setSex(personDTO.getSex());
		person.setSurname(personDTO.getSurname());
		person.setUuid(personDTO.getUuid());
		return person;
	}
}
