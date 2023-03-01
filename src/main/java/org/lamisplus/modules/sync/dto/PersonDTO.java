package org.lamisplus.modules.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.hibernate.annotations.Type;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonDTO implements Serializable {
	private Boolean active = false;
	@Type(type = "jsonb-node")
	private JsonNode contactPoint;
	@Type(type = "jsonb-node")
	private JsonNode address;
	@Type(type = "jsonb-node")
	private JsonNode gender;
	@Type(type = "jsonb-node")
	private JsonNode identifier;
	private Boolean deceased;
	private LocalDateTime deceasedDateTime;
	@Type(type = "jsonb-node")
	private JsonNode  maritalStatus;
	@Type(type = "jsonb-node")
	private JsonNode employmentStatus;
	@Type(type = "jsonb-node")
	private JsonNode education;
	private  String sex;
	@Type(type = "jsonb-node")
	private JsonNode organization;
	@Type(type = "jsonb-node")
	private JsonNode contact;
	private LocalDate dateOfBirth;
	private LocalDate dateOfRegistration;
	private Integer archived;
	private  String ninNumber;
	private  String emrId;
	private String uuid;
	private String firstName;
	private String surname;
	private String otherName;
	private String hospitalNumber;
	private Boolean isDateOfBirthEstimated;
	private String fullName;
	private  Long  facilityId;
}
