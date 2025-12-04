package org.lamisplus.modules.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.lamisplus.modules.base.domain.entities.OrganisationUnit;
import org.lamisplus.modules.base.domain.repositories.OrganisationUnitRepository;
import org.lamisplus.modules.hts.domain.dto.*;
import org.lamisplus.modules.hts.domain.entity.HtsClient;
import org.lamisplus.modules.hts.repository.*;
import org.lamisplus.modules.hts.service.*;
import org.lamisplus.modules.patient.domain.dto.*;
import org.lamisplus.modules.patient.domain.entity.Person;
import org.lamisplus.modules.patient.repository.PersonRepository;
import org.lamisplus.modules.patient.service.PersonService;
import org.lamisplus.modules.pmtct.domain.dto.*;
import org.lamisplus.modules.pmtct.domain.entity.ANC;
import org.lamisplus.modules.pmtct.repository.ANCRepository;
import org.lamisplus.modules.pmtct.service.*;
import org.lamisplus.modules.sync.domain.QuickSyncHistory;
import org.lamisplus.modules.sync.domain.dto.QuickSyncHistoryDTO;
import org.lamisplus.modules.sync.dto.BatchSyncResponse;
import org.lamisplus.modules.sync.dto.SyncRecordResult;
import org.lamisplus.modules.sync.dto.SyncSummaryByType;
import org.lamisplus.modules.sync.repository.QuickSyncHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Service
@RequiredArgsConstructor
public class QRReaderService {

    private final PersonService personService;
    private final HtsClientService htsClientService;
    private final RiskStratificationService riskStratificationService;
    private final IndexElicitationService indexElicitationService;
    private final FamilyIndexTestingService familyIndexTestingService;
    private final ClientReferralService clientReferralService;
    private final PNSService pnsService;
    private final ANCService ancService;
    private final DeliveryService deliveryService;
    private final PMTCTEnrollmentService pmtctService;
    private final InfantVisitService infantVisitService;
    private final InfantService infantService;
    private final PmtctVisitService pmtctVisitService;
    private final ANCRepository ancRepository;
    private final PersonRepository personRepository;
    private final QuickSyncHistoryRepository quickSyncHistoryRepository;
    private final OrganisationUnitRepository organisationUnitRepository;
    private final RiskStratificationRepository riskStratificationRepository;
    private final HtsClientRepository htsClientRepository;
    private final FamilyIndexTestingRepository familyIndexTestingRepository;
    private final PersonalNotificationServiceRepository partnerNotificationRepository;
    private final HtsClientReferralRepository clientReferralRepository;
    private final  FamilyIndexRepository familyIndexRepository;
//    private final RegimenRepository regimenRepository;

    private final ObjectMapper objectMapper;

    private String decompressAndDecode(String base64CompressedData) throws IOException {
        // Remove any spaces or newlines from the base64 encoded string
        base64CompressedData = base64CompressedData.replaceAll("\\s", "");
        // Decode the Base64 encoded data
        byte[] compressedData = Base64.getDecoder().decode(base64CompressedData);
        // Decompress the GZIP data
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gzipInputStream, "UTF-8"))) {

            StringBuilder decompressedData = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                decompressedData.append(line);
            }
            return decompressedData.toString();
        }
    }


    public List<Map<String, Object>> processZipFile(Long facilityId, MultipartFile multipartFile) throws IOException {
        List<Map<String, Object>> resultList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        OrganisationUnit facility = organisationUnitRepository.getOne(facilityId);
        String fileName = multipartFile.getOriginalFilename();
        int fileSizeInMB = (int) Math.ceil(multipartFile.getSize()/(1024.0 * 1024.0));
        // check if the filename exist in quickSyn history
        Boolean fileExists = quickSyncHistoryRepository.existsByFilename(fileName);
        if(fileExists){
            throw new IllegalArgumentException("This file has already been uploaded and processed. Please upload a different file.");
        }

        byte[] fileBytes = multipartFile.getBytes();

        // Convert the byte array to a ZipInputStream3
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
             ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Check if the entry is a file
                if (!entry.isDirectory()) {
                    String base64CompressedData = readZipEntry(zipInputStream);
                    String decompressedData = decompressAndDecode(base64CompressedData);
                    // Convert decompressed JSON data to a Map
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> jsonData = objectMapper.readValue(decompressedData, new TypeReference<Map<String, Object>>() {
                    });
                    // Add the parsed JSON data to the result list
                    resultList.add(jsonData);
                }
            }
        }
        if (!resultList.isEmpty()) {
            int recordsCount = 0;
            // Iterate over all elements in resultList
            for (Map<String, Object> result : resultList) {
                if (result.containsKey("person")) {
                    Object personField = result.get("person");
                    Object clientIntakeField = result.get("clientIntake");
                    Object riskStratificationField = result.get("riskStratification");
                    Object preTestField = result.get("preTest");
                    Object requestResultField = result.get("RequestResult");
                    Object postTestField = result.get("postTest");
                    Object recencyField = result.get("recency");
                    Object elicitationField = result.get("elicitation");
                    Object familyIndexTestingField = result.get("familyIndexTesting");
                    Object htsClientReferralField = result.get("htsClientReferral");
                    Object partnerNotificationServicesField = result.get("partnerNotificationServices");
                    Object ancField = result.get("anc");
                    Object childFollowupVisitField = result.get("childFollowupVisit");
                    Object infantRegistrationField = result.get("infantRegistration");
                    Object labourDeliveryField = result.get("labourDelivery");
                    Object infantRapidAntibodyTestField = result.get("infantRapidAntibodyTest");
                    Object motherFollowupVisitField = result.get("motherFollowupVisit");
                    Object partnerRegistrationField = result.get("partnerRegistration");
                    Object pmtctEnrollmentField = result.get("pmtctEnrollment");

                    // Safely cast fields to their expected types
                    Map<String, Object> clientIntakeData = (Map<String, Object>) clientIntakeField;
                    Map<String, Object> riskStratificationData = (Map<String, Object>) riskStratificationField;
                    Map<String, Object> preTestData = (Map<String, Object>) preTestField;
                    Map<String, Object> requestResultData = (Map<String, Object>) requestResultField;
                    Map<String, Object> postTestData = (Map<String, Object>) postTestField;
                    Map<String, Object> recencyData = (Map<String, Object>) recencyField;
                    Map<String, Object> elicitationData = (Map<String, Object>) elicitationField;
                    Map<String, Object> familyIndexTestingData = (Map<String, Object>) familyIndexTestingField;
                    Map<String, Object> htsClientReferralData = (Map<String, Object>) htsClientReferralField;
                    Map<String, Object> partnerNotificationServicesData = (Map<String, Object>) partnerNotificationServicesField;
                    Map<String, Object> ancData = (Map<String, Object>) ancField;
                    Map<String, Object> childFollowupVisitData = (Map<String, Object>) childFollowupVisitField;
                    Map<String, Object> infantRegistrationData = (Map<String, Object>) infantRegistrationField;
                    Map<String, Object> labourDeliveryData = (Map<String, Object>) labourDeliveryField;
                    Map<String, Object> motherFollowupVisitData = (Map<String, Object>) motherFollowupVisitField;
                    Map<String, Object> partnerRegistrationData = (Map<String, Object>) partnerRegistrationField;
                    Map<String, Object> pmtctEnrollmentData = (Map<String, Object>) pmtctEnrollmentField;
                    Map<String, Object> infantRapidAntibodyTestData = (Map<String, Object>) pmtctEnrollmentField;


                    if (personField instanceof Map) {
                        Map<String, Object> personData = (Map<String, Object>) personField;

                        // Validate that the person's facilityId matches the input facilityId
                        Object personFacilityIdObj = personData.get("facilityId");
                        if (personFacilityIdObj != null) {
                            Long personFacilityId = ((Number) personFacilityIdObj).longValue();
                            if (!personFacilityId.equals(facilityId)) {
                                throw new IllegalArgumentException(
                                    "Upload failed: This file belongs to a different facility. " +
                                    "Please ensure you are uploading the correct file for your assigned facility."
                                );
                            }
                        }

                        PersonDto personDto = convertToPersonDto(personData);
                        PersonResponseDto personResponseDto = personService.createPerson(personDto);

                        if (personResponseDto != null) {
                            Long patientId = personResponseDto.getId();
                            String patientUuid = String.valueOf(personResponseDto.getUuid());
                            RiskStratificationDto riskStratificationDto = createRiskStratification(riskStratificationData);
                            riskStratificationDto.setPersonId(patientId);
                            Person person = personRepository.findById(riskStratificationDto.getPersonId())
                                    .orElseThrow(() -> new IllegalArgumentException("Person not found with ID: " + riskStratificationDto.getPersonId()));

                            if (person.getUuid() == null || person.getUuid().isEmpty()) {
                                String generatedUuid = UUID.randomUUID().toString();
                                person.setUuid(generatedUuid);
                                personRepository.save(person);
                            }

                            RiskStratificationResponseDto riskStratificationResponseDto = riskStratificationService.save(riskStratificationDto);
                            if (riskStratificationResponseDto != null && riskStratificationResponseDto.getCode() != null) {
                                HtsClientRequestDto htsClientRequestDto = createHtsClientRequestDto(personResponseDto, clientIntakeData, patientId, riskStratificationResponseDto.getCode());
                                htsClientRequestDto.setPersonId(patientId);
                                htsClientRequestDto.setPersonDto(personDto);

                                HtsClientDto htsClientDto = htsClientService.save(htsClientRequestDto);

                                if (htsClientDto != null) {
                                    Long clientId = htsClientDto.getId();
                                    String clientUuid = htsClientDto.getHtsClientUUid();
                                    if (preTestField != null) {
                                            HtsPreTestCounselingDto dto = createPreTestCounseling(preTestData, clientId, patientId);
                                            htsClientService.updatePreTestCounseling(clientId, dto);
                                    }
                                    if (requestResultField != null) {
                                            HtsRequestResultDto dto = createRequestResult(requestResultData, clientId, patientId);
                                            htsClientService.updateRequestResult(clientId, dto);
                                    }
                                    if (postTestField != null) {
                                            PostTestCounselingDto dto = createPostTestCounseling(postTestData, clientId, patientId);
                                            htsClientService.updatePostTestCounselingKnowledgeAssessment(clientId, dto);
                                    }

                                    if (recencyField != null) {
                                            HtsRecencyDto dto = createRecency(recencyData, clientId, patientId);
                                            htsClientService.updateRecency(clientId, dto);
                                    }
                                    if (elicitationField != null) {
                                            IndexElicitationDto dto = createIndexElicitation(elicitationData, clientId);
                                            indexElicitationService.save(dto);
                                    }

                                    if (familyIndexTestingField != null) {
                                            FamilyIndexTestingRequestDTO dto = createFamilyIndexTesting(familyIndexTestingData, clientUuid, clientId);
                                            familyIndexTestingService.save(dto);
                                    }
                                    if (htsClientReferralField != null) {
                                            HtsClientReferralRequestDTO dto = createHtsClientReferral(htsClientReferralData, clientUuid, clientId);
                                            clientReferralService.registerClientReferralForm(dto);
                                    }
                                    if (partnerNotificationServicesField != null) {
                                            PersonalNotificationServiceRequestDTO dto = createPartnerNotificationServices(partnerNotificationServicesData, clientUuid, clientId);
                                            pnsService.save(dto);
                                    }
                                    if (ancField != null) {
                                            ANCEnrollementRequestDto dto = createAnc(ancData, patientUuid,patientId,personDto);
                                            ancService.ANCEnrollement(dto);
                                    }
                                    if (childFollowupVisitField != null) {
                                            InfantVisitationConsolidatedDto dto = createChildFollowup(childFollowupVisitData);
                                            infantVisitService.saveConsolidation(dto,dto.getInfantRapidAntiBodyTestDto());
                                    }
                                    if (motherFollowupVisitField != null) {
                                            pmtctVisitService.save(objectMapper.convertValue(motherFollowupVisitData,PmtctVisitRequestDto.class));
                                    }
                                    if (partnerRegistrationField != null) {
                                            PartnerInformation dto = createPartnerInformation(partnerRegistrationData);
                                           Optional<ANC> anc = ancRepository.findANCByPersonUuid(patientUuid);
                                            if(anc.isPresent()){
                                               ancService.updateAncWithPartnerInfo(anc.get().getId(), dto);
                                           }
                                    }
                                    if (pmtctEnrollmentField != null) {
                                        PMTCTEnrollmentRequestDto dto = createPmtctEnrollmentDto(pmtctEnrollmentData, personDto, patientUuid);
                                        boolean enrollmentSaved = pmtctService.save(dto).isPmtctRegStatus(); // capture result

                                        if (enrollmentSaved && labourDeliveryField != null) {
                                            DeliveryRequestDto ldto = createDeliveryRequestDto(labourDeliveryData, patientUuid);
                                            deliveryService.save(ldto);
                                        }
                                    }

                                    if (infantRegistrationField != null) {
                                            InfantDto dto = createInfantDto(infantRegistrationData,patientUuid);
                                            infantService.save(dto);
                                    }
                                    if (infantRapidAntibodyTestField != null) {
                                            assert infantRapidAntibodyTestData != null;
                                            InfantRapidAntiBodyTestDto dto = createInfantRapidAntibodyTest(infantRapidAntibodyTestData);
                                            infantVisitService.save(dto);
                                    }
                                }
                            }
                        }
                    }
                    recordsCount++;
                }

            }
            getQuickSyncHistoryDTO(multipartFile, facility, fileSizeInMB, recordsCount,"");
        }

        return resultList;
    }

    private Long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        } else {
            return null;
        }
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Integer) {
            return ((Integer) value) == 1;
        } else {
            return null;
        }
    }


    private String readZipEntry(ZipInputStream zipInputStream) throws IOException {
        StringBuilder fileContent = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipInputStream));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            fileContent.append(line);
        }
        return fileContent.toString();
    }

    public PersonDto convertToPersonDto(Map<String, Object> personData) {
        Long facilityId = ((Number) personData.get("facilityId")).longValue();
        return PersonDto.builder()
                .active((Boolean) personData.get("active"))
                .address(parseAddressList(getList(personData.get("address")), facilityId))
                .contactPoint(parseContactPointList(getList(personData.get("contactPoint"))))
                .dateOfBirth(parseLocalDate((String) personData.get("dateOfBirth")))
                .dateOfRegistration(parseLocalDate((String) personData.get("dateOfRegistration")))
                .deceased((Boolean) personData.get("deceased"))
                .deceasedDateTime(parseLocalDateTime((String) personData.get("deceasedDateTime")))
                .emrId((String) personData.get("emrId"))
                .facilityId(((Number) personData.get("facilityId")).longValue())
                .firstName((String) personData.get("firstName"))
                .genderId(((Number) personData.get("genderId")).longValue())
                .identifier(parseIdentifierList(getList(personData.get("identifier"))))
                .isDateOfBirthEstimated((Boolean) personData.get("isDateOfBirthEstimated"))
                .maritalStatusId(((Number) personData.get("maritalStatusId")).longValue())
                .organizationId(((Number) personData.get("organizationId")).longValue())
                .sexId(((Number) personData.get("sexId")).longValue())
                .surname((String) personData.get("surname"))
                .uuid((String) personData.get("uuid"))
                .build();
    }

    private LocalDateTime parseLocalDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr);
    }

    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateStr);
    }

    private List<AddressDto> parseAddressList(List<Map<String, Object>> addressList, Long facilityId) {
        return addressList.stream()
//                .map(this::convertToAddressDto)
                .map(addressData -> convertToAddressDto(addressData, facilityId))
                .collect(Collectors.toList());
    }

    private List<ContactPointDto> parseContactPointList(List<Map<String, Object>> contactPointList) {
        return contactPointList.stream()
                .map(this::convertToContactPointDto)
                .collect(Collectors.toList());
    }

    private List<IdentifierDto> parseIdentifierList(List<Map<String, Object>> identifierList) {
        return identifierList.stream()
                .map(this::convertToIdentifierDto)
                .collect(Collectors.toList());
    }


    private AddressDto convertToAddressDto(Map<String, Object> addressData, Long facilityId) {
        List<String> line = (List<String>) addressData.get("line");
        String city = (String) addressData.get("city");
        String district = (String) addressData.get("district");
        Long stateId = ((Number) addressData.get("stateId")).longValue();
        String postalCode = (String) addressData.get("postalCode");
        Long countryId = ((Number) addressData.get("countryId")).longValue();
        Long organisationUnitId = addressData.containsKey("organisationUnitId")
                ? ((Number) addressData.get("organisationUnitId")).longValue()
                : facilityId;

        return new AddressDto(line, city, district, stateId, postalCode, countryId, organisationUnitId);
    }


    private ContactPointDto convertToContactPointDto(Map<String, Object> contactPointData) {
        String type = (String) contactPointData.get("type");
        String value = (String) contactPointData.get("value");
        return new ContactPointDto(type, value);
    }

    private IdentifierDto convertToIdentifierDto(Map<String, Object> identifierData) {
        return new IdentifierDto(
                (String) identifierData.get("type"),
                (String) identifierData.get("value"),
                identifierData.get("assignerId") != null ? ((Number) identifierData.get("assignerId")).longValue() : null
        );
    }

    private List<Map<String, Object>> getList(Object obj) {
        if (obj instanceof List) {
            return (List<Map<String, Object>>) obj;
        } else if (obj instanceof String) {
            // Handle the case where obj is a JSON string that needs to be parsed
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.readValue((String) obj, new TypeReference<List<Map<String, Object>>>() {
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse JSON string", e);
            }
        } else {
            throw new IllegalArgumentException("Unexpected type: " + obj.getClass().getName());
        }
    }

    //
    private HtsClientRequestDto createHtsClientRequestDto(PersonResponseDto personResponseDto, Map<String, Object> jsonData, Long personId, String riskStratificationCode) {
        String dateVisitStr = (String) jsonData.get("dateVisit");
        LocalDate dateVisit = dateVisitStr != null ? LocalDate.parse(dateVisitStr) : null;

        // Check if riskStratificationCode in jsonData is empty or null and set it to the parameter value if so
        String jsonRiskStratificationCode = (String) jsonData.get("riskStratificationCode");
        String finalRiskStratificationCode = (jsonRiskStratificationCode == null || jsonRiskStratificationCode.isEmpty())
                ? riskStratificationCode
                : jsonRiskStratificationCode;

        // Construct the HtsClientRequestDto with the required fields
        return new HtsClientRequestDto(
                (String) jsonData.get("targetGroup"),
                (String) jsonData.get("clientCode"),
                dateVisit,
                jsonData.get("referredFrom") != null ? Long.valueOf((Integer) jsonData.get("referredFrom")) : null,
                (String) jsonData.get("testingSetting"),
                finalRiskStratificationCode,
                jsonData.get("firstTimeVisit") != null ? Boolean.parseBoolean((String) jsonData.get("firstTimeVisit")) : null,
                jsonData.get("numChildren") != null ? (Integer) jsonData.get("numChildren") : null,
                jsonData.get("numWives") != null ? (Integer) jsonData.get("numWives") : null,
                jsonData.get("typeCounseling") != null ? Long.valueOf((Integer) jsonData.get("typeCounseling")) : null,
                jsonData.get("indexClient") != null ? Boolean.parseBoolean((String) jsonData.get("indexClient")) : null,
                (String) jsonData.get("indexClientCode"),
                jsonData.get("previouslyTested") != null ? Boolean.parseBoolean((String) jsonData.get("previouslyTested")) : null,
                jsonData.get("extra"),
                jsonData.get("pregnant") != null ? Long.valueOf((Integer) jsonData.get("pregnant")) : null,
                jsonData.get("breastFeeding") != null ? Boolean.parseBoolean((String) jsonData.get("breastFeeding")) : null,
                jsonData.get("relationWithIndexClient") != null ? Long.valueOf((Integer) jsonData.get("relationWithIndexClient")) : null
        );

    }


    private RiskStratificationDto createRiskStratification(Map<String, Object> riskstratificationData) {
        // 🧠 Parse date of birth
        String dobStr = (String) riskstratificationData.get("dob");
        LocalDate dob = dobStr != null && !dobStr.trim().isEmpty() ? LocalDate.parse(dobStr) : null;

        // 🧠 Parse visit date
        String visitDateStr = (String) riskstratificationData.get("visitDate");
        LocalDate visitDate = visitDateStr != null && !visitDateStr.trim().isEmpty() ? LocalDate.parse(visitDateStr) : null;

        // 🧠 Extract riskAssessment as a Map
        Object rawRiskAssessment = riskstratificationData.get("riskAssessment");
        Map<String, Object> assessmentMap = null;
        if (rawRiskAssessment instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> castedMap = (Map<String, Object>) rawRiskAssessment;
            assessmentMap = castedMap;
            String lastHivTestDone = (String) assessmentMap.get("lastHivTestDone");
        }

        return RiskStratificationDto.builder()
                .age((Integer) riskstratificationData.get("age"))
                .entryPoint((String) riskstratificationData.get("entryPoint"))
                .testingSetting((String) riskstratificationData.get("testingSetting"))
                .modality((String) riskstratificationData.get("modality"))
                .targetGroup((String) riskstratificationData.get("targetGroup"))
                .visitDate(visitDate)
                .dob(dob)
                .code((String) riskstratificationData.get("code"))
                .personId(riskstratificationData.get("personId") != null ? Long.valueOf(riskstratificationData.get("personId").toString()) : null)
                .source((String) riskstratificationData.get("source"))
                .riskAssessment(assessmentMap) // stored as jsonb
                .build();
    }



    private HtsPreTestCounselingDto createPreTestCounseling(Map<String, Object> preTestData, Long htsClientId, Long personId) {
        Object knowledgeAssessment = preTestData.get("knowledgeAssessment");
        Object riskAssessment = preTestData.get("riskAssessment");
        Object tbScreening = preTestData.get("tbScreening");
        Object stiScreening = preTestData.get("stiScreening");
        Object sexPartnerRiskAssessment = preTestData.get("sexPartnerRiskAssessment");
        String latitude = (String) preTestData.get("latitude");
        String longitude = (String) preTestData.get("longitude");

        HtsPreTestCounselingDto htsPreTestCounselingDto = new HtsPreTestCounselingDto(htsClientId, personId);
        htsPreTestCounselingDto.setKnowledgeAssessment(knowledgeAssessment);
        htsPreTestCounselingDto.setRiskAssessment(riskAssessment);
        htsPreTestCounselingDto.setStiScreening(stiScreening);
        htsPreTestCounselingDto.setTbScreening(tbScreening);
        htsPreTestCounselingDto.setSexPartnerRiskAssessment(sexPartnerRiskAssessment);

        return htsPreTestCounselingDto;

    }

    private HtsRequestResultDto createRequestResult(Map<String, Object> requestResultData, Long htsClientId, Long personId) {
        Object test1 = requestResultData.get("test1");
        Object confirmatoryTest = requestResultData.get("confirmatoryTest");
        Object tieBreakerTest = requestResultData.get("tieBreakerTest");
        String hivTestResult = (String) requestResultData.get("hivTestResult");

        // Second test if first test is positive
        Object test2 = requestResultData.get("test2");
        Object confirmatoryTest2 = requestResultData.get("confirmatoryTest2");
        Object tieBreakerTest2 = requestResultData.get("tieBreakerTest2");
        String hivTestResult2 = (String) requestResultData.get("hivTestResult2");

        Object syphilisTesting = requestResultData.get("syphilisTesting");
        Object hepatitisTesting = requestResultData.get("hepatitisTesting");
        Object others = requestResultData.get("others");
        Object cd4 = requestResultData.get("cd4");

        // Prep offered and accepted (default to null or false if not available)
//        Boolean prepAccepted = (Boolean) requestResultData.getOrDefault("prepAccepted", null);
        Boolean prepOffered = convertToBoolean(requestResultData.getOrDefault("prepOffered", null));
        Boolean prepAccepted = convertToBoolean(requestResultData.getOrDefault("prepAccepted", null));
        HtsRequestResultDto htsRequestResultDto = new HtsRequestResultDto(
                htsClientId,
                personId,
                test1,
                confirmatoryTest,
                tieBreakerTest,
                hivTestResult,
                test2,
                confirmatoryTest2,
                tieBreakerTest2,
                hivTestResult2,
                syphilisTesting,
                hepatitisTesting
        );
        htsRequestResultDto.setCd4(cd4);
        htsRequestResultDto.setPrepOffered(prepOffered);
        htsRequestResultDto.setPrepAccepted(prepAccepted);
        htsRequestResultDto.setOthers(others);

        return htsRequestResultDto;

    }

    private PostTestCounselingDto createPostTestCounseling(Map<String, Object> postTestData, Long htsClientId, Long personId) {
        // Extracting fields from the postTestData map
        Object postTestCounselingKnowledgeAssessment = postTestData.get("postTestCounselingKnowledgeAssessment");
        String source = (String) postTestData.get("source");
        String latitude = (String) postTestData.get("latitude");
        String longitude = (String) postTestData.get("longitude");
        return new PostTestCounselingDto(
                htsClientId,
                personId,
                postTestCounselingKnowledgeAssessment
        );
    }

    private HtsRecencyDto createRecency(Map<String, Object> recencyData, Long htsClientId, Long personId) {
        Object recency = recencyData.get("recency");
        String source = (String) recencyData.get("source");
        String latitude = (String) recencyData.get("latitude");
        String longitude = (String) recencyData.get("longitude");
        return new HtsRecencyDto(
                htsClientId,
                personId,
                recency
        );
    }


    private IndexElicitationDto createIndexElicitation(Map<String, Object> elicitationData, Long htsClientId) {
        // Extracting fields from the elicitationData map
        String firstName = (String) elicitationData.get("firstName");
        String lastName = (String) elicitationData.get("lastName");
        String middleName = (String) elicitationData.get("middleName");
        String phoneNumber = (String) elicitationData.get("phoneNumber");
        String altPhoneNumber = (String) elicitationData.get("altPhoneNumber");
        String address = (String) elicitationData.get("address");
        String hangOutSpots = (String) elicitationData.get("hangOutSpots");
        String latitude = (String) elicitationData.get("latitude");
        String longitude = (String) elicitationData.get("longitude");
        String uuid = (String) elicitationData.get("uuid");

        Boolean isDateOfBirthEstimated = (Boolean) elicitationData.get("isDateOfBirthEstimated");
        LocalDate dob = elicitationData.containsKey("dob") ? LocalDate.parse((String) elicitationData.get("dob")) : null;
        LocalDate datePartnerCameForTesting = elicitationData.containsKey("datePartnerCameForTesting") ? LocalDate.parse((String) elicitationData.get("datePartnerCameForTesting")) : null;
        // Converting values to Long using helper method
        Long sex = convertToLong(elicitationData.get("sex"));
        Long physicalHurt = convertToLong(elicitationData.get("physicalHurt"));
        Long threatenToHurt = convertToLong(elicitationData.get("threatenToHurt"));
        Long notificationMethod = convertToLong(elicitationData.get("notificationMethod"));
        Long partnerTestedPositive = convertToLong(elicitationData.get("partnerTestedPositive"));
        Long relationshipToIndexClient = convertToLong(elicitationData.get("relativeToIndexClient"));
        Long sexuallyUncomfortable = convertToLong(elicitationData.get("sexuallyUncomfortable"));
        Boolean currentlyLiveWithPartner = convertToBoolean(elicitationData.get("currentlyLiveWithPartner"));

        String offeredIns = elicitationData.containsKey("offeredIns") ? elicitationData.get("offeredIns").toString() : null;
        String acceptedIns = elicitationData.containsKey("acceptedIns") ? elicitationData.get("acceptedIns").toString() : null;

        return IndexElicitationDto.builder()
                .htsClientId(htsClientId)
                .dob(dob)
                .isDateOfBirthEstimated(isDateOfBirthEstimated)
                .sex(sex)
                .address(address)
                .lastName(lastName)
                .firstName(firstName)
                .middleName(middleName)
                .phoneNumber(phoneNumber)
                .altPhoneNumber(altPhoneNumber)
                .hangOutSpots(hangOutSpots)
                .physicalHurt(physicalHurt)
                .threatenToHurt(threatenToHurt)
                .partnerTestedPositive(partnerTestedPositive)
                .relationshipToIndexClient(relationshipToIndexClient)
                .sexuallyUncomfortable(sexuallyUncomfortable)
                .currentlyLiveWithPartner(currentlyLiveWithPartner)
                .datePartnerCameForTesting(datePartnerCameForTesting)
                .offeredIns(offeredIns)
                .acceptedIns(acceptedIns)
//                .longitude(longitude)
//                .latitude(latitude)
                .source("Mobile")
                .uuid(uuid)
                .build();
    }
    private FamilyIndexTestingRequestDTO createFamilyIndexTesting(Map<String, Object> familyIndexTestingData, String htsClientUuid, Long htsClientId) {
        Object rawFamilyIndexDto = familyIndexTestingData.get("familyIndexRequestDto");
        FamilyIndexRequestDto familyIndexRequestDto = null;

        if (rawFamilyIndexDto instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> familyIndexMap = (Map<String, Object>) rawFamilyIndexDto;

            // 🧼 Sanitize int fields: convert empty strings to null
            String[] intKeys = {"age", "childNumber"};
            for (String key : intKeys) {
                Object value = familyIndexMap.get(key);
                if (value instanceof String && ((String) value).trim().isEmpty()) {
                    familyIndexMap.put(key, null);
                } else if (value != null) {
                    try {
                        familyIndexMap.put(key, Integer.parseInt(value.toString()));
                    } catch (NumberFormatException e) {
                        familyIndexMap.put(key, null);
                    }
                }
            }

            // 📅 Handle LocalDate fields: yearChildDead, yearMotherDead
            String[] dateKeys = {"yearChildDead", "yearMotherDead"};
            for (String key : dateKeys) {
                Object value = familyIndexMap.get(key);
                if (value instanceof String) {
                    String dateStr = ((String) value).trim();
                    if (dateStr.isEmpty()) {
                        familyIndexMap.put(key, null);
                    } else {
                        try {
                            familyIndexMap.put(key, LocalDate.parse(dateStr));
                        } catch (DateTimeParseException e) {
                            familyIndexMap.put(key, null);
                        }
                    }
                }
            }

            // 🧹 Clean familyTestingTrackerRequestDTOs
            Object trackersObj = familyIndexMap.get("familyTestingTrackerRequestDTOs");
            if (trackersObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> trackers = (List<Map<String, Object>>) trackersObj;

                List<Map<String, Object>> cleanedTrackers = trackers.stream()
                        .filter(tracker -> tracker != null && !tracker.isEmpty())
                        .collect(Collectors.toList());

                familyIndexMap.put("familyTestingTrackerRequestDTOs", cleanedTrackers.isEmpty() ? null : cleanedTrackers);
            }

            // ✅ Deserialize safely
            familyIndexRequestDto = objectMapper.convertValue(familyIndexMap, FamilyIndexRequestDto.class);
        }

        // 🛡️ Final cleanup: remove null facilityId entries
        if (familyIndexRequestDto != null && familyIndexRequestDto.getFamilyTestingTrackerRequestDTOs() != null) {
            familyIndexRequestDto.setFamilyTestingTrackerRequestDTOs(
                    familyIndexRequestDto.getFamilyTestingTrackerRequestDTOs().stream()
                            .filter(tracker -> tracker.getFacilityId() != null)
                            .collect(Collectors.toList())
            );
        }

        return FamilyIndexTestingRequestDTO.builder()
                .htsClientId(htsClientId)
                .htsClientUuid(htsClientUuid)
                .extra(familyIndexTestingData.get("extra"))
                .age(String.valueOf(convertToInteger(familyIndexTestingData.get("age"))))
                .alternatePhoneNumber((String) familyIndexTestingData.get("alternatePhoneNumber"))
                .dateClientEnrolledOnTreatment(String.valueOf(parseDate(familyIndexTestingData.get("dateClientEnrolledOnTreatment"))))
                .dateIndexClientConfirmedHivPositiveTestResult(parseDate(familyIndexTestingData.get("dateIndexClientConfirmedHivPositiveTestResult")))
                .dateOfBirth(parseDate(familyIndexTestingData.get("dateOfBirth")))
                .facilityName((String) familyIndexTestingData.get("facilityName"))
                .familyIndexClient((String) familyIndexTestingData.get("familyIndexClient"))
                .indexClientId((String) familyIndexTestingData.get("indexClientId"))
                .isClientCurrentlyOnHivTreatment(String.valueOf(familyIndexTestingData.get("isClientCurrentlyOnHivTreatment")))
                .lga((String) familyIndexTestingData.get("lga"))
                .maritalStatus(String.valueOf(convertToLong(familyIndexTestingData.get("maritalStatus"))))
                .name((String) familyIndexTestingData.get("name"))
                .phoneNumber((String) familyIndexTestingData.get("phoneNumber"))
                .recencyTesting((String) familyIndexTestingData.get("recencyTesting"))
                .familyIndexRequestDto(familyIndexRequestDto)
                .setting((String) familyIndexTestingData.get("setting"))
                .sex(String.valueOf(convertToLong(familyIndexTestingData.get("sex"))))
                .state(String.valueOf(convertToLong(familyIndexTestingData.get("state"))))
                .virallyUnSuppressed(String.valueOf(familyIndexTestingData.get("virallyUnSuppressed")))
                .visitDate(parseDate(familyIndexTestingData.get("visitDate")))
                .willingToHaveChildrenTestedElseWhere(String.valueOf(familyIndexTestingData.get("willingToHaveChildrenTestedElseWhere")))
                .build();
    }


    private HtsClientReferralRequestDTO createHtsClientReferral(Map<String, Object> referralData, String htsClientUuid, Long htsClientId) {
        String addressOfReceivingFacility = (String) referralData.get("addressOfReceivingFacility");
        String addressOfReferringFacility = (String) referralData.get("addressOfReferringFacility");
        String comments = (String) referralData.get("comments");
        LocalDate dateVisit = parseDate(referralData.get("dateVisit"));
        String middleName = (String) referralData.get("middleName");
        String nameOfContactPerson = (String) referralData.get("nameOfContactPerson");
        String nameOfPersonReferringClient = (String) referralData.get("nameOfPersonReferringClient");
        String nameOfReceivingFacility = (String) referralData.get("nameOfReceivingFacility");
        String nameOfReferringFacility = (String) referralData.get("nameOfReferringFacility");
        String phoneNoOfReceivingFacility = (String) referralData.get("phoneNoOfReceivingFacility");
        String phoneNoOfReferringFacility = (String) referralData.get("phoneNoOfReferringFacility");
        String receivingFacilityLgaName = (String) referralData.get("receivingFacilityLgaName");
        String receivingFacilityStateName = (String) referralData.get("receivingFacilityStateName");
        String referredFromFacility = (String) referralData.get("referredFromFacility");
        String referredTo = (String) referralData.get("referredTo");
        Map<String, Object> serviceNeeded = (Map<String, Object>) referralData.get("serviceNeeded");

        HtsClientReferralRequestDTO dto = new HtsClientReferralRequestDTO();
        dto.setHtsClientUuid(htsClientUuid);
        dto.setHtsClientId(htsClientId);
        dto.setAddressOfReceivingFacility(addressOfReceivingFacility);
        dto.setAddressOfReferringFacility(addressOfReferringFacility);
        dto.setComments(comments);
        dto.setDateVisit(dateVisit);
        dto.setNameOfContactPerson(nameOfContactPerson);
        dto.setNameOfPersonReferringClient(nameOfPersonReferringClient);
        dto.setNameOfReceivingFacility(nameOfReceivingFacility);
        dto.setNameOfReferringFacility(nameOfReferringFacility);
        dto.setPhoneNoOfReceivingFacility(phoneNoOfReceivingFacility);
        dto.setPhoneNoOfReferringFacility(phoneNoOfReferringFacility);
        dto.setReceivingFacilityLgaName(receivingFacilityLgaName);
        dto.setReceivingFacilityStateName(receivingFacilityStateName);
        dto.setReferredFromFacility(referredFromFacility);
        dto.setReferredTo(referredTo);
        dto.setServiceNeeded(serviceNeeded);
        return dto;
    }
    private PersonalNotificationServiceRequestDTO createPartnerNotificationServices(Map<String, Object> pnsData,String htsClientUuid, Long htsClientId) {
        PersonalNotificationServiceRequestDTO dto = new PersonalNotificationServiceRequestDTO();
        Map<String, Object> htsClientInfo = (Map<String, Object>) pnsData.get("htsClientInformation");
        Map<String, Object> contactTracing = (Map<String, Object>) pnsData.get("contactTracing");
        Map<String, Object> violence = (Map<String, Object>) pnsData.get("intermediatePartnerViolence");

        dto.setHtsClientId(htsClientId);
        dto.setAcceptedHts(String.valueOf(pnsData.get("acceptedHts")));
        dto.setAcceptedPns((String) pnsData.get("acceptedPns"));
        dto.setAddress((String) pnsData.get("address"));
        dto.setAlternatePhoneNumber((String) pnsData.get("alternatePhoneNumber"));
        dto.setDateEnrollmentOnART(parseDate(pnsData.get("dateEnrollmentOnART")));
        dto.setDateOfElicitation(parseDate(pnsData.get("dateOfElicitation")));
        dto.setDatePartnerTested(parseDate(pnsData.get("datePartnerTested")));
        dto.setDob(parseDate(pnsData.get("dob")));
        dto.setFirstName((String) pnsData.get("firstName"));
        dto.setHivTestResult((String) pnsData.get("hivTestResult"));
        dto.setIndexClientId((String) pnsData.get("indexClientId"));
        dto.setKnownHivPositive((String) pnsData.get("knownHivPositive"));
        dto.setLastName((String) pnsData.get("lastName"));
        dto.setMiddleName((String) pnsData.get("middleName"));
        dto.setNotificationMethod(String.valueOf(pnsData.get("notificationMethod")));
        dto.setOfferedPns((String) pnsData.get("offeredPns"));
        dto.setPartnerId((String) pnsData.get("partnerId"));
        dto.setPhoneNumber((String) pnsData.get("phoneNumber"));
        dto.setReasonForDecline((String) pnsData.get("reasonForDecline"));
        dto.setRelationshipToIndexClient((String) pnsData.get("relationshipToIndexClient"));
        dto.setSex(String.valueOf( pnsData.get("sex")));
        dto.setContactTracing(contactTracing);
        dto.setHtsClientInformation(htsClientInfo);
        dto.setIntermediatePartnerViolence(violence);
        return dto;
    }

    private PMTCTEnrollmentRequestDto createPmtctEnrollmentDto(Map<String, Object> pmtctData, PersonDto personDto, String patientUuid) {
        Optional<Person> person = personRepository.findByUuid(patientUuid);
        Long id = null;
        Object idObj = pmtctData.get("id");
        if (idObj instanceof String) {
            String idStr = (String) idObj;
            if (idStr != null && !idStr.trim().isEmpty()) {
                try {
                    id = Long.valueOf(idStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid ID format: " + idStr);
                }
            }
        } else if (idObj instanceof Number) {
            id = ((Number) idObj).longValue();
        }

        Long regimenTypeId = null;
        Object regimenTypeIdObj = pmtctData.get("regimenTypeId");
        if (regimenTypeIdObj instanceof String) {
            String regimenStr = (String) regimenTypeIdObj;
            if (regimenStr != null && !regimenStr.trim().isEmpty()) {
                try {
                    regimenTypeId = Long.valueOf(regimenStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid regimenTypeId format: " + regimenStr);
                }
            }
        } else if (regimenTypeIdObj instanceof Number) {
            regimenTypeId = ((Number) regimenTypeIdObj).longValue();
        }

        // Ensure personUuid is present
        String personUuid = patientUuid;
        if (personUuid == null || personUuid.trim().isEmpty()) {
            throw new RuntimeException("Unable to resolve personUuid from input data.");
        }

        return PMTCTEnrollmentRequestDto.builder()
                .id(id)
                .ancNo(getString(pmtctData, "ancNo"))
                .pmtctEnrollmentDate(parseDate(getString(pmtctData, "pmtctEnrollmentDate")))
                .gravida(convertToInteger(pmtctData.get("gravida")))
                .gAWeeks(convertToInteger(pmtctData.get("gaweeks")))
                .entryPoint(getString(pmtctData, "entryPoint"))
                .artStartDate(parseDate(getString(pmtctData, "artStartDate")))
                .artStartTime(getString(pmtctData, "artStartTime"))
                .tbStatus(getString(pmtctData, "tbStatus"))
                .personDto(personDto)
                .pmtctType(getString(pmtctData, "pmtctType"))
                .personUuid(personUuid)
                .hivStatus(getString(pmtctData, "hivStatus"))
                .lmp(parseDate(getString(pmtctData, "lmp")))
                .motherArtInitiationTime(getString(pmtctData, "motherArtInitiationTime"))
                .regimenTypeId(regimenTypeId)
                .regimenId(getString(pmtctData, "regimenId"))
                .hepatitisB(getString(pmtctData, "hepatitisB"))
                .urinalysis(getString(pmtctData, "urinalysis"))
                .timeOfHivDiagnosis(getString(pmtctData, "timeOfHivDiagnosis"))
                .dateOfDelivery(getString(pmtctData, "dateOfDelivery"))
                .expectedDeliveryDate(getString(pmtctData, "expectedDeliveryDate"))
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return dateStr != null ? LocalDate.parse(dateStr) : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }


    private <T> T convertAncToDto(Object value, Class<T> clazz) {
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        if (value instanceof Map) {
            try {
                return objectMapper.convertValue(value, clazz);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to convert " + value + " to " + clazz.getSimpleName(), e);
            }
        }
        throw new IllegalArgumentException("Expected instance of " + clazz.getSimpleName() +
                " or Map, but got: " + (value != null ? value.getClass().getName() : "null"));
    }

    private ANCEnrollementRequestDto createAnc(Map<String, Object> ancData, String personUuid, Long patientId, PersonDto personDto) {
        if (ancData == null) {
            throw new IllegalArgumentException("ancData cannot be null");
        }
        if (personUuid == null || personUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("personUuid is required");
        }

        try {
            PersonResponseDto personResponseDto = personService.getPersonById(patientId);
            if (personResponseDto == null) {
                throw new IllegalArgumentException("Person not found for ID: " + patientId);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not retrieve person data for ANC sync", e);
        }

        ANCEnrollementRequestDto dto = ANCEnrollementRequestDto.builder()
                .person_uuid(personUuid)
//                .personDto(personDto)
                .ancNo(String.valueOf(ancData.get("ancNo")))
                .ancSetting(String.valueOf(ancData.get("ancSetting")))
                .communitySetting(String.valueOf(ancData.get("communitySetting")))
                .firstAncDate(parseDate(ancData.get("firstAncDate")))
                .gravida(convertToInteger(ancData.get("gravida")))
                .parity(convertToInteger(ancData.get("parity")))
                .LMP(parseDate(ancData.get("lmp")))
                .expectedDeliveryDate(parseDate(ancData.get("expectedDeliveryDate")))
                .gAWeeks(convertToInteger(ancData.get("gaweeks")))
                .hivDiognosicTime(String.valueOf(ancData.get("hivDiognosicTime")))
                .staticHivStatus(String.valueOf(ancData.get("staticHivStatus")))
                .testedSyphilis(String.valueOf(ancData.get("testedSyphilis")))
                .testResultSyphilis(String.valueOf(ancData.get("testResultSyphilis")))
                .treatedSyphilis(String.valueOf(ancData.get("treatedSyphilis")))
                .referredSyphilisTreatment(String.valueOf(ancData.get("referredSyphilisTreatment")))
                .previouslyKnownHivStatus(String.valueOf(ancData.get("previouslyKnownHivStatus")))
                .currentlyOnArt(String.valueOf(ancData.get("currentlyOnArt")))
                .dateOfHepatitisB(parseDate(ancData.get("dateOfHepatitisB")))
                .hepatitisB(String.valueOf(ancData.get("hepatitisB")))
                .testedHepatitisB(String.valueOf(ancData.get("testedHepatitisB")))
                .treatedHepatitisB(String.valueOf(ancData.get("treatedHepatitisB")))
                .referredHepatitisB(String.valueOf(ancData.get("referredHepatitisB")))
                .dateOfHepatitisC(parseDate(ancData.get("dateOfHepatitisC")))
                .hepatitisC(String.valueOf(ancData.get("hepatitisC")))
                .testedHepatitisC(String.valueOf(ancData.get("testedHepatitisC")))
                .treatedHepatitisC(String.valueOf(ancData.get("treatedHepatitisC")))
                .referredHepatitisC(String.valueOf(ancData.get("referredHepatitisC")))
                .facilityEnrolledIn(String.valueOf(ancData.get("facilityEnrolledIn")))
                .pmtctHtsInfo(convertAncToDto(ancData.get("pmtctHtsInfo"), PmtctHtsInfo.class))
                .partnerNotification(convertAncToDto(ancData.get("partnerNotification"), PartnerNotification.class))
                .build();
        return dto;
    }



    private DeliveryRequestDto createDeliveryRequestDto(Map<String, Object> deliveryData, String patientUuid) {

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        DeliveryRequestDto dto = mapper.convertValue(deliveryData, DeliveryRequestDto.class);

        dto.setAncNo(getString(deliveryData, "ancNo"));
        dto.setDateOfDelivery(parseLocalDate(getString(deliveryData, "dateOfDelivery")));
        dto.setHBStatus(getString(deliveryData, "hbstatus"));
        dto.setHCStatus(getString(deliveryData, "hcstatus"));
        dto.setPersonUuid(patientUuid);
        dto.setBookingStatus(getString(deliveryData, "bookingStatus"));
        dto.setGAWeeks(getInt(deliveryData, "gaweeks"));
        dto.setRomDeliveryInterval(getString(deliveryData, "romDeliveryInterval"));
        dto.setModeOfDelivery(getString(deliveryData, "modeOfDelivery"));
        dto.setEpisiotomy(getString(deliveryData, "episiotomy"));
        dto.setVaginalTear(getString(deliveryData, "vaginalTear"));
        dto.setFeedingDecision(getString(deliveryData, "feedingDecision"));
        dto.setMaternalOutcome(getString(deliveryData, "maternalOutcome"));
        dto.setChildGivenArvWithin72(getString(deliveryData, "childGivenArvWithin72"));
        dto.setChildStatus(getString(deliveryData, "childStatus"));
        dto.setHivExposedInfantGivenHbWithin24hrs(getString(deliveryData, "hivExposedInfantGivenHbWithin24hrs"));
        dto.setNonHbvExposedInfantGivenHbWithin24hrs(getString(deliveryData, "nonHbvExposedInfantGivenHbWithin24hrs"));
        dto.setDeliveryTime(getString(deliveryData, "deliveryTime"));
        dto.setOnArt(getString(deliveryData, "onArt"));
        dto.setArtStartedLdWard(getString(deliveryData, "artStartedLdWard"));
        dto.setReferalSource(getString(deliveryData, "referralSource"));
        dto.setNumberOfInfantsAlive(getInt(deliveryData, "numberOfInfantsAlive"));
        dto.setNumberOfInfantsDead(getInt(deliveryData, "numberOfInfantsDead"));
        dto.setPlaceOfDelivery(getString(deliveryData, "placeOfDelivery"));

        return dto;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInt(Map<String, Object> map, String key) {
        try {
            Object value = map.get(key);
            return value != null ? Integer.parseInt(value.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private PartnerInformation createPartnerInformation(Map<String, Object> partnerData) {
        PartnerInformation info = new PartnerInformation();

        info.setFullName(getString(partnerData.get("fullName")));
        info.setDateOfBirth(parseDate(partnerData.get("dateOfBirth")));
        info.setPreTestCounseled(getString(partnerData.get("preTestCounseled")));
        info.setAcceptHivTest(getString(partnerData.get("acceptHivTest")));
        info.setPostTestCounseled(getString(partnerData.get("postTestCounseled")));
        info.setHbStatus(getString(partnerData.get("hbStatus")));
        info.setHcStatus(getString(partnerData.get("hcStatus")));
        info.setSyphillisStatus(getString(partnerData.get("syphillisStatus")));
        info.setReferredTo(getString(partnerData.get("referredTo")));
        info.setReferredToOthers(getString(partnerData.get("referredToOthers")));
        info.setAge(getInteger(partnerData.get("age")));
        info.setHivStatus(getString(partnerData.get("hivStatus")));
        info.setDateConfirmedHivTest(parseDate(partnerData.get("dateConfirmedHivTest")));

        return info;
    }
    private String getString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private InfantDto createInfantDto(Map<String, Object> infantData, String patientUuid) {
        return InfantDto.builder()
                .dateOfDelivery(parseDate(infantData.get("dateOfDelivery")))
                .firstName(safeString(infantData.get("firstName")))
                .middleName(safeString(infantData.get("middleName")))
                .surname(safeString(infantData.get("surname")))
                .sex(safeString(infantData.get("sex")))
                .nin(safeString(infantData.get("nin")))
                .id(safeParseLong(infantData.get("id")))
                .hospitalNumber(safeString(infantData.get("hospitalNumber")))
                .uuid(safeString(infantData.get("uuid")))
                .ancNo(safeString(infantData.get("ancNo")))
                .infantOutcomeAt18Months(safeString(infantData.get("infantOutcomeAt18Months")))
                .personUuid(patientUuid)
                .bodyWeight(safeParseDouble(infantData.get("bodyWeight")))
                .ctxStatus(safeString(infantData.get("ctxStatus")))
                .infantArvDto(objectMapper.convertValue(infantData.get("infantArvDto"),InfantArvDto.class))
                .infantPCRTestDto(objectMapper.convertValue(infantData.get("infantPCRTestDto"),InfantPCRTestDto.class))
                .build();
    }
    private String safeString(Object value) {
        return value != null ? String.valueOf(value).trim() : null;
    }

    private Long safeParseLong(Object value) {
        if (value == null) return null;
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) return null;
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            System.out.println("Failed to parse long from: " + str);
            return null;
        }
    }

    private Double safeParseDouble(Object value) {
        if (value == null) return null;
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) return null;
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            System.out.println("Failed to parse double from: " + str);
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            // Optionally log the error or handle it
            return null;
        }
    }

    private InfantVisitationConsolidatedDto createChildFollowup(Map<String, Object> data) {
        InfantVisitationConsolidatedDto dto = new InfantVisitationConsolidatedDto();
        Map<String, Object> infantMotherArtData = (Map<String, Object>) data.get("infantMotherArtDto");
//        if (infantMotherArtData != null) {
//            Object regimenIdObj = infantMotherArtData.get("regimenId");
//            Object regimenTypeIdObj = infantMotherArtData.get("regimenTypeId");
//
//            if (regimenIdObj instanceof String && regimenTypeIdObj != null) {
//                String description = (String) regimenIdObj;
//                Long regimenTypeId = convertToLong(regimenTypeIdObj);
//                if (regimenTypeId != null) {
//                    Long actualRegimenId = regimenRepository
//                            .findByRegimenTypeIdAndDescription(regimenTypeId, description)
//                            .map(Regimen::getId)
//                            .orElse(null);
//                    infantMotherArtData.put("regimenId", actualRegimenId);
//                }
//            }
//        }
        dto.setInfantVisitRequestDto(objectMapper.convertValue(data.get("infantVisitRequestDto"), InfantVisitRequestDto.class));
        dto.setInfantMotherArtDto(objectMapper.convertValue(infantMotherArtData, InfantMotherArtDto.class));
        dto.setInfantArvDto(objectMapper.convertValue(data.get("infantArvDto"), InfantArvDto.class));
        dto.setInfantPCRTestDto(objectMapper.convertValue(data.get("infantPCRTestDto"), InfantPCRTestDto.class));
        dto.setInfantRapidAntiBodyTestDto(objectMapper.convertValue(data.get("infantRapidAntiBodyTestDto"), InfantRapidAntiBodyTestDto.class));
        return dto;
    }
    private PmtctVisitRequestDto createPmtctVisitRequestDto(Map<String, Object> visitData) {
        return PmtctVisitRequestDto.builder()
                .id((Long) visitData.get("id"))
                .ancNo((String) visitData.get("ancNo"))
                .enteryPoint((String) visitData.get("enteryPoint"))
                .dateOfInitialVisit(parseDate(visitData.get("dateOfInitialVisit")))
                .dateOfVisit(parseDate(visitData.get("dateOfVisit")))
                .dateOfDelivery(parseDate(visitData.get("dateOfDelivery")))
                .fpCounseling((String) visitData.get("fpCounseling"))
                .fpMethod((String) visitData.get("fpMethod"))
                .timeOfViralLoad((String) visitData.get("timeOfViralLoad"))
                .dateOfViralLoad(parseDate(visitData.get("dateOfViralLoad")))
                .gaOfViralLoad((Integer) visitData.get("gaOfViralLoad"))
                .resultOfViralLoad(Long.valueOf((Integer) visitData.get("resultOfViralLoad")))
                .dsd((String) visitData.get("dsd"))
                .dsdOption((String) visitData.get("dsdOption"))
                .dsdModel((String) visitData.get("dsdModel"))
                .maternalOutcome((String) visitData.get("maternalOutcome"))
                .dateOfmeternalOutcome(parseDate(visitData.get("dateOfmeternalOutcome")))
                .visitStatus((String) visitData.get("visitStatus"))
                .transferTo((String) visitData.get("transferTo"))
                .nextAppointmentDate(parseDate(visitData.get("nextAppointmentDate")))
                .personUuid((String) visitData.get("personUuid"))
                .build();
    }
    public InfantRapidAntiBodyTestDto createInfantRapidAntibodyTest(Map<String, Object>  data) {
        InfantRapidAntiBodyTestDto dto = new InfantRapidAntiBodyTestDto();
        dto.setRapidTestType(String.valueOf( data.get("rapidTestType")));
        dto.setAncNumber(String.valueOf(data.get("ancNo")));
        dto.setAgeAtTest(String.valueOf(data.get("ageAtTest")));
        dto.setDateOfTest(LocalDate.parse(String.valueOf(data.get("ateOfTest"))));
        return dto;
    }

    private LocalDate parseDate(Object dateObj) {
        if (dateObj == null) {
            return null;
        }
        String dateStr = dateObj.toString().trim();
        if (dateStr.isEmpty()) {
            return null;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }


    private Integer convertToInteger(Object obj) {
        try {
            return obj != null ? Integer.parseInt(obj.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double convertToDouble(Object obj) {
        try {
            return obj != null ? Double.parseDouble(obj.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }


    @NotNull
    private QuickSyncHistoryDTO getQuickSyncHistoryDTO(MultipartFile file, OrganisationUnit facility, int filesize,int recordsCount, String tableName) {
        QuickSyncHistoryDTO historyDTO = QuickSyncHistoryDTO.builder()
                .status("completed")
                .filename(file.getOriginalFilename())
                .facilityName(facility.getName())
                .tableName(tableName)
                .fileSize(filesize)
                .dateUpdated(LocalDateTime.now())
                .recordsCount(recordsCount)
                .build();
        QuickSyncHistory quickSyncHistory = new QuickSyncHistory();
        quickSyncHistory.setFilename(historyDTO.getFilename());
        quickSyncHistory.setStatus("completed");
        quickSyncHistory.setTableName(historyDTO.getTableName());
        quickSyncHistory.setFileSize(historyDTO.getFileSize());
        quickSyncHistory.setFilename(file.getOriginalFilename());
        quickSyncHistory.setFacilityName(historyDTO.getFacilityName());
        quickSyncHistory.setDateCreated(historyDTO.getDateUpdated());
        quickSyncHistory.setRecordsCount(recordsCount);
        quickSyncHistoryRepository.save(quickSyncHistory);
        return historyDTO;
    }

    /**
     * Helper method to get or create a record result tracker for a given hospital number and risk stratification code
     */
    private SyncRecordResult getOrCreateRecordResult(
            Map<String, SyncRecordResult> recordResults,
            String hospitalNumber,
            String riskStratificationCode) {
        String key = hospitalNumber + "|" + (riskStratificationCode != null ? riskStratificationCode : "");
        return recordResults.computeIfAbsent(key, k -> {
            SyncRecordResult result = new SyncRecordResult();
            result.setHospitalNumber(hospitalNumber);
            result.setRiskStratificationCode(riskStratificationCode);
            result.setSuccessfulComponents(new ArrayList<>());
            result.setFailedComponents(new ArrayList<>());
            return result;
        });
    }

    /**
     * REFACTORED METHOD FOR QRReaderService.java
     *
     * This file contains the complete refactored processZipFileWithUpdateLogic method
     * with component-level tracking and detailed error handling.
     *
     * TO INTEGRATE:
     * 1. Review this implementation
     * 2. Replace the existing processZipFileWithUpdateLogic method (lines 1277-1540 approximately)
     * 3. Test with small dataset first
     * 4. Deploy to production
     */

    public BatchSyncResponse processZipFileWithUpdateLogic(Long facilityId, MultipartFile multipartFile) throws IOException {
        long startTime = System.currentTimeMillis();

        // Initialize tracking structures
        Map<String, SyncRecordResult> recordResults = new HashMap<>();

        // Initialize counters (mutable approach for Java 8)
        Map<String, Integer> counters = new HashMap<>();
        counters.put("personCreated", 0);
        counters.put("personUpdated", 0);
        counters.put("personFailed", 0);
        counters.put("htsClientCreated", 0);
        counters.put("htsClientUpdated", 0);
        counters.put("htsClientFailed", 0);
        counters.put("preTestCreated", 0);
        counters.put("preTestFailed", 0);
        counters.put("postTestCreated", 0);
        counters.put("postTestFailed", 0);
        counters.put("recencyCreated", 0);
        counters.put("recencyFailed", 0);
        counters.put("familyIndexCreated", 0);
        counters.put("familyIndexSkipped", 0);
        counters.put("familyIndexFailed", 0);
        counters.put("partnerNotificationCreated", 0);
        counters.put("partnerNotificationSkipped", 0);
        counters.put("partnerNotificationFailed", 0);
        counters.put("clientReferralCreated", 0);
        counters.put("clientReferralSkipped", 0);
        counters.put("clientReferralFailed", 0);

        int totalRecords = 0;
        int completelySuccessful = 0;
        int partiallySuccessful = 0;
        int completelyFailed = 0;
        int skippedRecords = 0;

        OrganisationUnit facility = organisationUnitRepository.getOne(facilityId);
        String fileName = multipartFile.getOriginalFilename();
        int fileSizeInMB = (int) Math.ceil(multipartFile.getSize()/(1024.0 * 1024.0));

        // Check if the filename exists in quickSync history
        Boolean fileExists = quickSyncHistoryRepository.existsByFilename(fileName);
        if(fileExists){
            throw new IllegalArgumentException("This file has already been uploaded and processed. Please upload a different file.");
        }

        byte[] fileBytes = multipartFile.getBytes();

        // Convert the byte array to a ZipInputStream and process records one by one
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
             ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Check if the entry is a file
                if (!entry.isDirectory()) {
                    String base64CompressedData = readZipEntry(zipInputStream);
                    String decompressedData = decompressAndDecode(base64CompressedData);
                    // Convert decompressed JSON data to a Map
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> result = objectMapper.readValue(decompressedData, new TypeReference<Map<String, Object>>() {
                    });

                    // Process this record immediately instead of accumulating
                    totalRecords++;

                    if (result.containsKey("person")) {
                        Object personField = result.get("person");
                        Object clientIntakeField = result.get("clientIntake");
                        Object riskStratificationField = result.get("riskStratification");
                        Object preTestField = result.get("preTest");
                        Object requestResultField = result.get("RequestResult");
                        Object postTestField = result.get("postTest");
                        Object recencyField = result.get("recency");
                        Object familyIndexTestingField = result.get("familyIndexTesting");
                        Object htsClientReferralField = result.get("htsClientReferral");
                        Object partnerNotificationServicesField = result.get("partnerNotificationServices");

                        // Safely cast fields to their expected types
                        Map<String, Object> personData = (Map<String, Object>) personField;
                        Map<String, Object> clientIntakeData = (Map<String, Object>) clientIntakeField;
                        Map<String, Object> riskStratificationData = (Map<String, Object>) riskStratificationField;
                        Map<String, Object> preTestData = (Map<String, Object>) preTestField;
                        Map<String, Object> requestResultData = (Map<String, Object>) requestResultField;
                        Map<String, Object> postTestData = (Map<String, Object>) postTestField;
                        Map<String, Object> recencyData = (Map<String, Object>) recencyField;
                        Map<String, Object> familyIndexTestingData = (Map<String, Object>) familyIndexTestingField;
                        Map<String, Object> htsClientReferralData = (Map<String, Object>) htsClientReferralField;
                        Map<String, Object> partnerNotificationServicesData = (Map<String, Object>) partnerNotificationServicesField;

                        if (personField instanceof Map) {
                            // Validate that the person's facilityId matches the input facilityId
                            Object personFacilityIdObj = personData.get("facilityId");
                            if (personFacilityIdObj != null) {
                                Long personFacilityId = ((Number) personFacilityIdObj).longValue();
                                if (!personFacilityId.equals(facilityId)) {
                                    throw new IllegalArgumentException(
                                            "Upload failed: This file belongs to a different facility. " +
                                                    "Please ensure you are uploading the correct file for your assigned facility."
                                    );
                                }
                            }

                            String hospitalNumber = (String) clientIntakeData.get("hospitalNumber");
                            String riskStratificationCode = (String) riskStratificationData.get("code");

                            // Get or create record result tracker
                            SyncRecordResult recordResult =
                                    getOrCreateRecordResult(recordResults, hospitalNumber, riskStratificationCode);

                            Long patientId = null;
                            String patientUuid = null;
                            boolean createNewPerson = true;

                            // 1. PROCESS PERSON with try-catch
                            try {
                                // Check if hospital number already exists
                                if (hospitalNumber != null && !hospitalNumber.trim().isEmpty()) {
                                    Optional<Person> existingPerson = personRepository.getPersonByHospitalNumberAndFacilityId(hospitalNumber, facilityId);
                                    if (existingPerson.isPresent()) {
                                        createNewPerson = false;
                                        patientId = existingPerson.get().getId();
                                        patientUuid = existingPerson.get().getUuid();
                                        recordResult.getSuccessfulComponents().add("person (existing)");
                                        counters.put("personUpdated", counters.get("personUpdated") + 1);
                                    }
                                }

                                // Create new person if hospital number doesn't exist
                                if (createNewPerson) {
                                    PersonDto personDto = convertToPersonDto(personData);
                                    PersonResponseDto personResponseDto = personService.createPerson(personDto);

                                    if (personResponseDto != null) {
                                        patientId = personResponseDto.getId();
                                        patientUuid = String.valueOf(personResponseDto.getUuid());
                                        recordResult.getSuccessfulComponents().add("person (created)");
                                        counters.put("personCreated", counters.get("personCreated") + 1);
                                    }
                                }
                            } catch (Exception e) {
                                recordResult.getFailedComponents().add(
                                        SyncRecordResult.ComponentFailure.builder()
                                                .component("person")
                                                .identifier(hospitalNumber)
                                                .reason(e.getMessage())
                                                .errorCode("DATABASE_ERROR")
                                                .build()
                                );
                                counters.put("personFailed", counters.get("personFailed") + 1);
                            }

                            // Continue only if we have patientId and patientUuid
                            if (patientId != null && patientUuid != null) {
                                Long clientId = null;
                                boolean createNewRiskStratification = true;
                                boolean createNewHtsClient = true;

                                // 2. PROCESS RISK STRATIFICATION with try-catch
                                try {
                                    // Check if risk stratification code already exists
                                    if (riskStratificationCode != null && !riskStratificationCode.trim().isEmpty()) {
                                        Optional<org.lamisplus.modules.hts.domain.entity.RiskStratification> existingRiskStratification =
                                                riskStratificationRepository.findByCode(riskStratificationCode);
                                        if (existingRiskStratification.isPresent()) {
                                            createNewRiskStratification = false;
                                            riskStratificationCode = existingRiskStratification.get().getCode();
                                        }
                                    }

                                    // Create new risk stratification if code doesn't exist
                                    if (createNewRiskStratification) {
                                        RiskStratificationDto riskStratificationDto = createRiskStratification(riskStratificationData);
                                        riskStratificationDto.setPersonId(patientId);

                                        final Long finalPatientId = patientId;
                                        Person person = personRepository.findById(patientId)
                                                .orElseThrow(() -> new IllegalArgumentException("Person not found with ID: " + finalPatientId));

                                        if (person.getUuid() == null || person.getUuid().isEmpty()) {
                                            String generatedUuid = UUID.randomUUID().toString();
                                            person.setUuid(generatedUuid);
                                            personRepository.save(person);
                                            patientUuid = generatedUuid;
                                        }

                                        RiskStratificationResponseDto riskStratificationResponseDto = riskStratificationService.save(riskStratificationDto);
                                        if (riskStratificationResponseDto != null && riskStratificationResponseDto.getCode() != null) {
                                            riskStratificationCode = riskStratificationResponseDto.getCode();
                                            recordResult.getSuccessfulComponents().add("riskStratification");
                                        }
                                    }
                                } catch (Exception e) {
                                    recordResult.getFailedComponents().add(
                                            SyncRecordResult.ComponentFailure.builder()
                                                    .component("riskStratification")
                                                    .identifier(riskStratificationCode)
                                                    .reason(e.getMessage())
                                                    .errorCode("DATABASE_ERROR")
                                                    .build()
                                    );
                                }

                                // 3. PROCESS HTS CLIENT with try-catch
                                try {
                                    // Check if client intake with risk stratification code already exists
                                    if (riskStratificationCode != null && !riskStratificationCode.trim().isEmpty()) {
                                        Optional<HtsClient> existingHtsClient =
                                                htsClientRepository.findFirstByRiskStratificationCode(riskStratificationCode);
                                        if (existingHtsClient.isPresent()) {
                                            createNewHtsClient = false;
                                            clientId = existingHtsClient.get().getId();
                                            recordResult.getSuccessfulComponents().add("htsClient (existing)");
                                            counters.put("htsClientUpdated", counters.get("htsClientUpdated") + 1);
                                        }
                                    }

                                    // Create new HTS client if it doesn't exist
                                    if (createNewHtsClient && riskStratificationCode != null) {
                                        PersonDto personDto = convertToPersonDto(personData);
                                        HtsClientRequestDto htsClientRequestDto = createHtsClientRequestDto(
                                                new PersonResponseDto(), clientIntakeData, patientId, riskStratificationCode);
                                        htsClientRequestDto.setPersonId(patientId);
                                        htsClientRequestDto.setPersonDto(personDto);

                                        HtsClientDto htsClientDto = htsClientService.save(htsClientRequestDto);
                                        if (htsClientDto != null) {
                                            clientId = htsClientDto.getId();
                                            recordResult.getSuccessfulComponents().add("htsClient (created)");
                                            counters.put("htsClientCreated", counters.get("htsClientCreated") + 1);
                                        }
                                    }
                                } catch (Exception e) {
                                    recordResult.getFailedComponents().add(
                                            SyncRecordResult.ComponentFailure.builder()
                                                    .component("htsClient")
                                                    .identifier(riskStratificationCode)
                                                    .reason(e.getMessage())
                                                    .errorCode("DATABASE_ERROR")
                                                    .build()
                                    );
                                    counters.put("htsClientFailed", counters.get("htsClientFailed") + 1);
                                }

                                // 4. PROCESS PRE-TEST with try-catch
                                if (clientId != null && preTestField != null) {
                                    try {
                                        HtsPreTestCounselingDto preTestDto = createPreTestCounseling(preTestData, clientId, patientId);
                                        htsClientService.updatePreTestCounseling(clientId, preTestDto);
                                        recordResult.getSuccessfulComponents().add("preTest");
                                        counters.put("preTestCreated", counters.get("preTestCreated") + 1);
                                    } catch (Exception e) {
                                        recordResult.getFailedComponents().add(
                                                SyncRecordResult.ComponentFailure.builder()
                                                        .component("preTest")
                                                        .identifier(riskStratificationCode)
                                                        .reason(e.getMessage())
                                                        .errorCode("DATABASE_ERROR")
                                                        .build()
                                        );
                                        counters.put("preTestFailed", counters.get("preTestFailed") + 1);
                                    }
                                }

                                // 5. PROCESS REQUEST/RESULT with try-catch
                                if (clientId != null && requestResultField != null) {
                                    try {
                                        HtsRequestResultDto requestResultDto = createRequestResult(requestResultData, clientId, patientId);
                                        htsClientService.updateRequestResult(clientId, requestResultDto);
                                        recordResult.getSuccessfulComponents().add("requestResult");
                                    } catch (Exception e) {
                                        recordResult.getFailedComponents().add(
                                                SyncRecordResult.ComponentFailure.builder()
                                                        .component("requestResult")
                                                        .identifier(riskStratificationCode)
                                                        .reason(e.getMessage())
                                                        .errorCode("DATABASE_ERROR")
                                                        .build()
                                        );
                                    }
                                }

                                // 6. PROCESS POST-TEST with try-catch
                                if (clientId != null && postTestField != null) {
                                    try {
                                        PostTestCounselingDto postTestDto = createPostTestCounseling(postTestData, clientId, patientId);
                                        htsClientService.updatePostTestCounselingKnowledgeAssessment(clientId, postTestDto);
                                        recordResult.getSuccessfulComponents().add("postTest");
                                        counters.put("postTestCreated", counters.get("postTestCreated") + 1);
                                    } catch (Exception e) {
                                        recordResult.getFailedComponents().add(
                                                SyncRecordResult.ComponentFailure.builder()
                                                        .component("postTest")
                                                        .identifier(riskStratificationCode)
                                                        .reason(e.getMessage())
                                                        .errorCode("DATABASE_ERROR")
                                                        .build()
                                        );
                                        counters.put("postTestFailed", counters.get("postTestFailed") + 1);
                                    }
                                }

                                // 7. PROCESS RECENCY with try-catch
                                if (clientId != null && recencyField != null) {
                                    try {
                                        HtsRecencyDto recencyDto = createRecency(recencyData, clientId, patientId);
                                        htsClientService.updateRecency(clientId, recencyDto);
                                        recordResult.getSuccessfulComponents().add("recency");
                                        counters.put("recencyCreated", counters.get("recencyCreated") + 1);
                                    } catch (Exception e) {
                                        recordResult.getFailedComponents().add(
                                                SyncRecordResult.ComponentFailure.builder()
                                                        .component("recency")
                                                        .identifier(riskStratificationCode)
                                                        .reason(e.getMessage())
                                                        .errorCode("DATABASE_ERROR")
                                                        .build()
                                        );
                                        counters.put("recencyFailed", counters.get("recencyFailed") + 1);
                                    }
                                }

                                // 8. PROCESS FAMILY INDEX TESTING with try-catch
                                if (clientId != null && familyIndexTestingField != null) {
                                    // Get contactId from nested familyIndexRequestDto object
//                                    String contactId = (String) familyIndexTestingData.get("contactId");

                                    String contactId = null;
                                    Object familyIndexRequestDtoObj = familyIndexTestingData.get("familyIndexRequestDto");
                                    if (familyIndexRequestDtoObj instanceof Map) {
                                        Map<String, Object> familyIndexRequestDto = (Map<String, Object>) familyIndexRequestDtoObj;
                                        contactId = (String) familyIndexRequestDto.get("contactId");
                                    }
                                    boolean shouldCreateFamilyIndex = true;

                                    if (contactId != null && !contactId.trim().isEmpty()) {
                                        if (familyIndexRepository.existsByContactId(contactId)) {
                                            shouldCreateFamilyIndex = false;
                                            recordResult.getSuccessfulComponents().add("familyIndexTesting (skipped - duplicate)");
                                            counters.put("familyIndexSkipped", counters.get("familyIndexSkipped") + 1);
                                        }
                                    }

                                    if (shouldCreateFamilyIndex) {
                                        try {
                                            HtsClientDto htsClientDto = htsClientService.getClientById(clientId);
                                            String clientUuid = htsClientDto != null ? htsClientDto.getHtsClientUUid() : null;
                                            if (clientUuid != null) {
                                                FamilyIndexTestingRequestDTO dto = createFamilyIndexTesting(familyIndexTestingData, clientUuid, clientId);
                                                familyIndexTestingService.save(dto);
                                                recordResult.getSuccessfulComponents().add("familyIndexTesting");
                                                counters.put("familyIndexCreated", counters.get("familyIndexCreated") + 1);
                                            }
                                        } catch (Exception e) {
                                            recordResult.getFailedComponents().add(
                                                    SyncRecordResult.ComponentFailure.builder()
                                                            .component("familyIndexTesting")
                                                            .identifier(contactId)
                                                            .reason(e.getMessage())
                                                            .errorCode("DATABASE_ERROR")
                                                            .build()
                                            );
                                            counters.put("familyIndexFailed", counters.get("familyIndexFailed") + 1);
                                        }
                                    }
                                }

                                // 9. PROCESS PARTNER NOTIFICATION SERVICES with try-catch
                                if (clientId != null && partnerNotificationServicesField != null) {
                                    String partnerIdStr = (String) partnerNotificationServicesData.get("partnerId");
                                    boolean shouldCreatePNS = true;
                                    System.out.println("PNS PARTNER ID "+ partnerIdStr);
                                    if (partnerIdStr != null && !partnerIdStr.trim().isEmpty()) {
                                        try {
//                                            String partnerId = Long.parseLong(partnerIdStr);
                                            if (partnerNotificationRepository.existsByPartnerId(partnerIdStr)) {
                                                shouldCreatePNS = false;
                                                recordResult.getSuccessfulComponents().add("partnerNotificationServices (skipped - duplicate)");
                                                counters.put("partnerNotificationSkipped", counters.get("partnerNotificationSkipped") + 1);
                                            }
                                        } catch (NumberFormatException e) {
                                            // If partnerId is not a valid Long, proceed with creation
                                        }
                                    }

                                    if (shouldCreatePNS) {
                                        try {
                                            HtsClientDto htsClientDto = htsClientService.getClientById(clientId);
                                            String clientUuid = htsClientDto != null ? htsClientDto.getHtsClientUUid() : null;
                                            if (clientUuid != null) {
                                                PersonalNotificationServiceRequestDTO dto = createPartnerNotificationServices(partnerNotificationServicesData, clientUuid, clientId);
                                                pnsService.save(dto);
                                                recordResult.getSuccessfulComponents().add("partnerNotificationServices");
                                                counters.put("partnerNotificationCreated", counters.get("partnerNotificationCreated") + 1);
                                            }
                                        } catch (Exception e) {
                                            recordResult.getFailedComponents().add(
                                                    SyncRecordResult.ComponentFailure.builder()
                                                            .component("partnerNotificationServices")
                                                            .identifier(partnerIdStr)
                                                            .reason(e.getMessage())
                                                            .errorCode("DATABASE_ERROR")
                                                            .build()
                                            );
                                            counters.put("partnerNotificationFailed", counters.get("partnerNotificationFailed") + 1);
                                        }
                                    }
                                }

                                // 10. PROCESS HTS CLIENT REFERRAL with try-catch
                                if (clientId != null && htsClientReferralField != null && patientUuid != null) {
                                    String nameOfContactPerson = (String) htsClientReferralData.get("nameOfContactPerson");
                                    String nameOfPersonReferringClient = (String) htsClientReferralData.get("nameOfPersonReferringClient");
                                    boolean shouldCreateReferral = true;

                                    try {
                                        // Wrap duplicate check in try-catch to prevent batch termination
                                        if (hospitalNumber != null && !hospitalNumber.trim().isEmpty() &&
                                                nameOfContactPerson != null && nameOfPersonReferringClient != null) {
                                            if (clientReferralRepository.existsByHtsClient_UuidAndNameOfPersonReferringClientAndNameOfContactPerson(
                                                    patientUuid, nameOfPersonReferringClient, nameOfContactPerson)) {
                                                shouldCreateReferral = false;
                                                recordResult.getSuccessfulComponents().add("htsClientReferral (skipped - duplicate)");
                                                counters.put("clientReferralSkipped", counters.get("clientReferralSkipped") + 1);
                                            }
                                        }

                                        if (shouldCreateReferral) {
                                            HtsClientDto htsClientDto = htsClientService.getClientById(clientId);
                                            String clientUuid = htsClientDto != null ? htsClientDto.getHtsClientUUid() : null;
                                            if (clientUuid != null) {
                                                HtsClientReferralRequestDTO dto = createHtsClientReferral(htsClientReferralData, clientUuid, clientId);
                                                clientReferralService.registerClientReferralForm(dto);
                                                recordResult.getSuccessfulComponents().add("htsClientReferral");
                                                counters.put("clientReferralCreated", counters.get("clientReferralCreated") + 1);
                                            }
                                        }
                                    } catch (Exception e) {
                                        recordResult.getFailedComponents().add(
                                                SyncRecordResult.ComponentFailure.builder()
                                                        .component("htsClientReferral")
                                                        .identifier(hospitalNumber)
                                                        .reason(e.getMessage())
                                                        .errorCode("DATABASE_ERROR")
                                                        .build()
                                        );
                                        counters.put("clientReferralFailed", counters.get("clientReferralFailed") + 1);
                                    }
                                }
                            }

                            // Determine overall status for this record
                            List<String> successfulComponents = recordResult.getSuccessfulComponents();
                            List<SyncRecordResult.ComponentFailure> failedComponents = recordResult.getFailedComponents();

                            if (!failedComponents.isEmpty() && successfulComponents.isEmpty()) {
                                recordResult.setStatus("COMPLETELY_FAILED");
                                recordResult.setMessage("All components failed to sync");
                                completelyFailed++;
                            } else if (!failedComponents.isEmpty() && !successfulComponents.isEmpty()) {
                                recordResult.setStatus("PARTIALLY_SUCCESSFUL");
                                recordResult.setMessage("Some components failed to sync");
                                partiallySuccessful++;
                            } else if (!successfulComponents.isEmpty()) {
                                recordResult.setStatus("COMPLETELY_SUCCESSFUL");
                                recordResult.setMessage("All components synced successfully");
                                completelySuccessful++;
                            } else {
                                recordResult.setStatus("SKIPPED");
                                recordResult.setMessage("No components processed");
                                skippedRecords++;
                            }
                        }
                    }
                }
            }

            // Save quick sync history
            getQuickSyncHistoryDTO(multipartFile, facility, fileSizeInMB, totalRecords, "HTS_UPDATE");
        }

        // Build summary
        SyncSummaryByType summary = SyncSummaryByType.builder()
                .personCreated(counters.get("personCreated"))
                .personUpdated(counters.get("personUpdated"))
                .personFailed(counters.get("personFailed"))
                .htsClientCreated(counters.get("htsClientCreated"))
                .htsClientUpdated(counters.get("htsClientUpdated"))
                .htsClientFailed(counters.get("htsClientFailed"))
                .preTestCreated(counters.get("preTestCreated"))
                .preTestFailed(counters.get("preTestFailed"))
                .postTestCreated(counters.get("postTestCreated"))
                .postTestFailed(counters.get("postTestFailed"))
                .recencyCreated(counters.get("recencyCreated"))
                .recencyFailed(counters.get("recencyFailed"))
                .familyIndexCreated(counters.get("familyIndexCreated"))
                .familyIndexSkipped(counters.get("familyIndexSkipped"))
                .familyIndexFailed(counters.get("familyIndexFailed"))
                .partnerNotificationCreated(counters.get("partnerNotificationCreated"))
                .partnerNotificationSkipped(counters.get("partnerNotificationSkipped"))
                .partnerNotificationFailed(counters.get("partnerNotificationFailed"))
                .clientReferralCreated(counters.get("clientReferralCreated"))
                .clientReferralSkipped(counters.get("clientReferralSkipped"))
                .clientReferralFailed(counters.get("clientReferralFailed"))
                .build();

        // Collect only problem records (not completely successful)
        List<SyncRecordResult> problemRecords = recordResults.values().stream()
                .filter(record -> !record.getStatus().equals("COMPLETELY_SUCCESSFUL"))
                .collect(Collectors.toList());

        // Build and return response
        long processingTime = System.currentTimeMillis() - startTime;

        return BatchSyncResponse.builder()
                .fileName(fileName)
                .facilityName(facility.getName())
                .processedAt(LocalDateTime.now())
                .totalRecords(totalRecords)
                .completelySuccessful(completelySuccessful)
                .partiallySuccessful(partiallySuccessful)
                .completelyFailed(completelyFailed)
                .skippedRecords(skippedRecords)
                .summary(summary)
                .problemRecords(problemRecords)
                .processingTimeMs(processingTime)
                .build();
    }

}