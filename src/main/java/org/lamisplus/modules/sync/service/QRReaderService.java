package org.lamisplus.modules.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.lamisplus.modules.base.domain.entities.OrganisationUnit;
import org.lamisplus.modules.hiv.domain.entity.Regimen;
import org.lamisplus.modules.base.domain.repositories.OrganisationUnitRepository;
import org.lamisplus.modules.hiv.repositories.RegimenRepository;
import org.lamisplus.modules.hts.domain.dto.*;
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
    private final PmtctVisitService pmtctVisitService;
    private final ANCRepository ancRepository;
    private final PersonRepository personRepository;
    private final QuickSyncHistoryRepository quickSyncHistoryRepository;
    private final OrganisationUnitRepository organisationUnitRepository;
    private final RegimenRepository regimenRepository;

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
            throw new IllegalArgumentException("File with the name " + fileName + " has already been processed");
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

                    if (personField instanceof Map) {
                        // Process single person data
                        Map<String, Object> personData = (Map<String, Object>) personField;
                        PersonDto personDto = convertToPersonDto(personData);
                        PersonResponseDto personResponseDto = personService.createPerson(personDto);

                        if (personResponseDto != null) {
                            Long patientId = personResponseDto.getId();
                            String patientUuid = String.valueOf(personResponseDto.getUuid());
                            System.out.println(" PersonResponseDto : "+ patientId);
                            // Create and save the RiskStratificationDto
                            RiskStratificationDto riskStratificationDto = createRiskStratification(riskStratificationData);
                            riskStratificationDto.setPersonId(patientId);
                            System.out.println("riskStratificationDto : "+ riskStratificationDto);
                            RiskStratificationResponseDto riskStratificationResponseDto = riskStratificationService.save(riskStratificationDto);
                            // Sync HtsClient if RiskStratificationResponseDto is not null
                            if (riskStratificationResponseDto != null && riskStratificationResponseDto.getCode() != null) {
                                HtsClientRequestDto htsClientRequestDto = createHtsClientRequestDto(personResponseDto, clientIntakeData, patientId, riskStratificationResponseDto.getCode());
                                htsClientRequestDto.setPersonId(patientId);
                                htsClientRequestDto.setPersonDto(personDto);

                                HtsClientDto htsClientDto = htsClientService.save(htsClientRequestDto);

                                if (htsClientDto != null) {
                                    Long clientId = htsClientDto.getId();
                                    String clientUuid = htsClientDto.getHtsClientUUid();
                                    Map<Object, Runnable> fieldActions = new HashMap<>();
                                    if (preTestField != null) {
                                        fieldActions.put(preTestField, () -> {
                                            HtsPreTestCounselingDto dto = createPreTestCounseling(preTestData, clientId, patientId);
                                            htsClientService.updatePreTestCounseling(clientId, dto);
                                        });
                                    }
                                    if (requestResultField != null) {
                                        fieldActions.put(requestResultField, () -> {
                                            HtsRequestResultDto dto = createRequestResult(requestResultData, clientId, patientId);
                                            htsClientService.updateRequestResult(clientId, dto);
                                        });
                                    }
                                    if (postTestField != null) {
                                        fieldActions.put(postTestField, () -> {
                                            PostTestCounselingDto dto = createPostTestCounseling(postTestData, clientId, patientId);
                                            htsClientService.updatePostTestCounselingKnowledgeAssessment(clientId, dto);
                                        });
                                    }

                                    if (recencyField != null) {
                                        fieldActions.put(recencyField, () -> {
                                            HtsRecencyDto dto = createRecency(recencyData, clientId, patientId);
                                            htsClientService.updateRecency(clientId, dto);
                                        });
                                    }
                                    if (elicitationField != null) {
                                        fieldActions.put(elicitationField, () -> {
                                            IndexElicitationDto dto = createIndexElicitation(elicitationData, clientId);
                                            indexElicitationService.save(dto);
                                        });
                                    }

                                    if (familyIndexTestingField != null) {
                                        fieldActions.put(familyIndexTestingField, () -> {
                                            FamilyIndexTestingRequestDTO dto = createFamilyIndexTesting(familyIndexTestingData, clientUuid, clientId);
                                            familyIndexTestingService.save(dto);
                                        });
                                    }
                                    if (htsClientReferralField != null) {
                                        fieldActions.put(htsClientReferralField, () -> {
                                            HtsClientReferralRequestDTO dto = createHtsClientReferral(htsClientReferralData, clientUuid, clientId);
                                            clientReferralService.registerClientReferralForm(dto);
                                        });
                                    }
                                    if (partnerNotificationServicesField != null) {
                                        fieldActions.put(partnerNotificationServicesField, () -> {
                                            PersonalNotificationServiceRequestDTO dto = createPartnerNotificationServices(partnerNotificationServicesData, clientUuid, clientId);
                                            pnsService.save(dto);
                                        });
                                    }
                                    if (ancField != null) {
                                        fieldActions.put(ancField, () -> {
                                            ANCRequestDto dto = createAnc(ancData, patientUuid,patientId,personDto);
                                            ancService.save(dto);
                                        });
                                    }
                                    if (childFollowupVisitField != null) {
                                        fieldActions.put(childFollowupVisitField, () -> {
                                            InfantVisitationConsolidatedDto dto = createChildFollowup(childFollowupVisitData);
                                            infantVisitService.saveConsolidation(dto,dto.getInfantRapidAntiBodyTestDto());
                                        });
                                    }
                                    if (labourDeliveryField != null) {
                                        fieldActions.put(labourDeliveryField, () -> {
                                            DeliveryRequestDto dto = createDeliveryRequestDto(labourDeliveryData,patientUuid);
                                            deliveryService.save(dto);
                                        });
                                    }
                                    if (motherFollowupVisitField != null) {
                                        fieldActions.put(motherFollowupVisitField, () -> {
                                            pmtctVisitService.save(objectMapper.convertValue(motherFollowupVisitData,PmtctVisitRequestDto.class));
                                        });
                                    }
                                    if (partnerRegistrationField != null) {
                                        fieldActions.put(partnerRegistrationField, () -> {
                                            PartnerInformation dto = createPartnerInformation(partnerRegistrationData);
                                           Optional<ANC> anc = ancRepository.findANCByPersonUuid(patientUuid);

                                            System.out.println("pmtct anc: "+anc);

                                            if(anc.isPresent()){
                                               ancService.updateAncWithPartnerInfo(anc.get().getId(), dto);
                                           }
                                        });
                                    }
                                    if (pmtctEnrollmentField != null) {
                                        fieldActions.put(pmtctEnrollmentField, () -> {
                                            PMTCTEnrollmentRequestDto dto = createPmtctEnrollmentDto(pmtctEnrollmentData,personDto,patientUuid);
                                            System.out.println("DTO to save pmtct enrolmnt: "+dto);
                                            pmtctService.save(dto);
                                        });
                                    }
                                    // Execute all actions
                                    fieldActions.forEach((field, action) -> {
                                        if (field != null) {
                                            action.run();
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
            getQuickSyncHistoryDTO(multipartFile, facility, fileSizeInMB, "");
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
//                .latitude((Double) personData.get("latitude"))
//                .longitude((Double) personData.get("longitude"))
                .maritalStatusId(((Number) personData.get("maritalStatusId")).longValue())
                .organizationId(((Number) personData.get("organizationId")).longValue())
                .sexId(((Number) personData.get("sexId")).longValue())
//                .source((String) personData.get("source"))
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
//        Long organisationUnitId = ((Number) addressData.get("organisationUnitId")).longValue();
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
        String dobStr = (String) riskstratificationData.get("dob");
        LocalDate dob = dobStr != null ? LocalDate.parse(dobStr) : null;

        String visitDateStr = (String) riskstratificationData.get("visitDate");
        LocalDate visitDate = visitDateStr != null ? LocalDate.parse(visitDateStr) : null;
        return RiskStratificationDto.builder()
                .age((Integer) riskstratificationData.get("age"))
                .entryPoint((String) riskstratificationData.get("entryPoint"))
                .testingSetting((String) riskstratificationData.get("testingSetting"))
                .modality((String) riskstratificationData.get("modality"))
                .targetGroup((String) riskstratificationData.get("targetGroup"))
                .visitDate(visitDate)
                .dob(dob)
                .code((String) riskstratificationData.get("code"))
//                .personId(Long.valueOf((Integer) riskstratificationData.get("personId")))
                .source((String) riskstratificationData.get("source"))
                .riskAssessment(riskstratificationData.get("riskAssessment"))
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
        FamilyIndexRequestDto familyIndexRequestDto = objectMapper.convertValue(
                familyIndexTestingData.get("familyIndexRequestDto"),
                FamilyIndexRequestDto.class
        );

        // 🛡️ Sanitize FamilyTestingTrackerRequestDTOs — remove entries with null facilityId
        if (familyIndexRequestDto.getFamilyTestingTrackerRequestDTOs() != null) {
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
                .familyIndexRequestDto(familyIndexRequestDto) // now sanitized
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
        System.out.println("params: "+pnsData+" "+htsClientUuid+" "+htsClientId);
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
        System.out.println("personDtossss: " + pmtctData + personDto + patientUuid);
        Optional<Person> person =  personRepository.findByUuid(patientUuid);
        System.out.println("person::: "+person);

        Long id = null;
        Object idObj = pmtctData.get("id");
        if (idObj instanceof String) {
            String idStr = (String) idObj;
            if (idStr != null && !idStr.trim().isEmpty()) {
                try {
                    id = Long.valueOf(idStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid ID format: {}"+idStr);
                }
            }
        } else if (idObj instanceof Number) {
            id = ((Number) idObj).longValue();
        }

        Long regimenTypeId = null;
        Object regimenTypeIdObj = pmtctData.get("regimenTypeId");
        if (regimenTypeIdObj instanceof String) {
            String regimenStr = (String) regimenTypeIdObj;
            if (!regimenStr.trim().isEmpty()) {
                try {
                    regimenTypeId = Long.valueOf(regimenStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid regimenTypeId format: {}"+ regimenStr);
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
                .ancNo((String) pmtctData.get("ancNo"))
                .pmtctEnrollmentDate(parseDate(pmtctData.get("pmtctEnrollmentDate")))
                .gravida(convertToInteger(pmtctData.get("gravida")))
                .gAWeeks(convertToInteger(pmtctData.get("gAWeeks")))
                .entryPoint((String) pmtctData.get("entryPoint"))
                .artStartDate(parseDate(pmtctData.get("artStartDate")))
                .artStartTime((String) pmtctData.get("artStartTime"))
                .tbStatus((String) pmtctData.get("tbStatus"))
                .personDto(personDto)
                .pmtctType((String) pmtctData.get("pmtctType"))
                .personUuid(patientUuid)
                .hivStatus((String) pmtctData.get("hivStatus"))
                .lmp(parseDate(pmtctData.get("lmp")))
                .motherArtInitiationTime((String) pmtctData.get("motherArtInitiationTime"))
                .regimenTypeId(regimenTypeId)
                .regimenId((String) pmtctData.get("regimenId"))
                .hepatitisB((String) pmtctData.get("hepatitisB"))
                .urinalysis((String) pmtctData.get("urinalysis"))
                .timeOfHivDiagnosis((String) pmtctData.get("timeOfHivDiagnosis"))
                .dateOfDelivery((String) pmtctData.get("dateOfDelivery"))
                .expectedDeliveryDate((String) pmtctData.get("expectedDeliveryDate"))
                .build();
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

    private ANCRequestDto createAnc(Map<String, Object> ancData, String personUuid,Long patientId,PersonDto personDto) {
        System.out.println("createAnc params: "+personUuid+"-"+patientId+"-"+personDto);
        System.out.println("ANC Data: "+ancData);
        if (ancData == null) {
            throw new IllegalArgumentException("ancData cannot be null");
        }
        if (personUuid == null || personUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("personUuid is required");
        }

        ANCRequestDto dto = new ANCRequestDto();

        // --- Set person_uuid FIRST ---
        dto.setPerson_uuid(personUuid);

        // ✅ CRITICAL: Fetch and set PersonDto to avoid NPE in ANCService.getHospitalNumber(...)
        try {
             PersonResponseDto personResponseDto = personService.getPersonById(patientId);
            if (personResponseDto == null ) {
                throw new IllegalArgumentException("Person not found for ID: " + patientId);
            }
            dto.setPersonDto(personDto);
        } catch (Exception e) {
            throw new IllegalStateException("Could not retrieve person data for ANC sync", e);
        }

        // --- Map all other fields ---
        dto.setAncNo((String) ancData.get("ancNo"));
        dto.setAncSetting((String) ancData.get("ancSetting"));
        dto.setCommunitySetting((String) ancData.get("communitySetting"));
        dto.setFirstAncDate(parseDate(ancData.get("enrollmentDate"))); // maps to firstAncDate
        dto.setGravida(convertToInteger(ancData.get("gravida")));
        dto.setParity(convertToInteger(ancData.get("parity")));
        dto.setLMP(parseDate(ancData.get("lmp")));
        dto.setExpectedDeliveryDate(parseDate(ancData.get("expectedDeliveryDate")));
        dto.setGAWeeks(convertToInteger(ancData.get("gAWeeks")));
        dto.setHivDiognosicTime((String) ancData.get("hivDiognosicTime"));
        dto.setStaticHivStatus((String) ancData.get("staticHivStatus"));
        dto.setTestedSyphilis((String) ancData.get("testedSyphilis"));
        dto.setTestResultSyphilis((String) ancData.get("testResultSyphilis"));
        dto.setTreatedSyphilis((String) ancData.get("treatedSyphilis"));
        dto.setReferredSyphilisTreatment((String) ancData.get("referredSyphilisTreatment"));

        // ✅ Safely convert nested objects
        dto.setPmtctHtsInfo(convertAncToDto(ancData.get("pmtctHtsInfo"), PmtctHtsInfo.class));
        dto.setPartnerNotification(convertAncToDto(ancData.get("partnerNotification"), PartnerNotification.class));

        // --- Remaining fields ---
        dto.setPreviouslyKnownHivStatus((String) ancData.get("previouslyKnownHivStatus"));
        dto.setCurrentlyOnArt((String) ancData.get("currentlyOnArt"));

        dto.setDateOfHepatitisB(parseDate(ancData.get("dateOfHepatitisB")));
        dto.setHepatitisB((String) ancData.get("hepatitisB"));
        dto.setTestedHepatitisB((String) ancData.get("testedHepatitisB"));
        dto.setTreatedHepatitisB((String) ancData.get("treatedHepatitisB"));
        dto.setReferredHepatitisB((String) ancData.get("referredHepatitisB"));

        dto.setDateOfHepatitisC(parseDate(ancData.get("dateOfHepatitisC")));
        dto.setHepatitisC((String) ancData.get("hepatitisC"));
        dto.setTestedHepatitisC((String) ancData.get("testedHepatitisC"));
        dto.setTreatedHepatitisC((String) ancData.get("treatedHepatitisC"));
        dto.setReferredHepatitisC((String) ancData.get("referredHepatitisC"));

        dto.setFacilityEnrolledIn((String) ancData.get("facilityEnrolledIn"));

        return dto;
    }
    private DeliveryRequestDto createDeliveryRequestDto(Map<String, Object> deliveryData, String patientUuid) {
        System.out.println("Delivery: "+deliveryData+"-"+patientUuid);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Support for Java 8 date/time types
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Convert basic fields using ObjectMapper
        DeliveryRequestDto dto = mapper.convertValue(deliveryData, DeliveryRequestDto.class);

        // Override or handle fields that need special treatment
        dto.setAncNo(String.valueOf(deliveryData.get("ancNo")));
        dto.setDateOfDelivery(parseLocalDate(String.valueOf(deliveryData.get("dateOfDelivery")))); // Custom parser
        dto.setBookingStatus(String.valueOf(deliveryData.get("bookingStatus")));
        dto.setGAWeeks((Integer) deliveryData.get("gAWeeks"));
        dto.setRomDeliveryInterval(String.valueOf(deliveryData.get("romDeliveryInterval")));
        dto.setModeOfDelivery(String.valueOf(deliveryData.get("modeOfDelivery")));
        dto.setEpisiotomy(String.valueOf(deliveryData.get("episiotomy")));
        dto.setVaginalTear(String.valueOf(deliveryData.get("vaginalTear")));
        dto.setFeedingDecision(String.valueOf(deliveryData.get("feedingDecision")));
        dto.setMaternalOutcome(String.valueOf(deliveryData.get("maternalOutcome")));
        dto.setChildGivenArvWithin72(String.valueOf(deliveryData.get("childGivenArvWithin72")));
        dto.setChildStatus(String.valueOf(deliveryData.get("childStatus")));
        dto.setHivExposedInfantGivenHbWithin24hrs(String.valueOf(deliveryData.get("hivExposedInfantGivenHbWithin24hrs")));
        dto.setNonHbvExposedInfantGivenHbWithin24hrs(String.valueOf(deliveryData.get("nonHbvExposedInfantGivenHbWithin24hrs")));
        dto.setDeliveryTime(String.valueOf(deliveryData.get("deliveryTime")));
        dto.setOnArt(String.valueOf(deliveryData.get("onArt")));
        dto.setArtStartedLdWard(String.valueOf(deliveryData.get("artStartedLdWard")));
        dto.setHBStatus(String.valueOf(deliveryData.get("HBStatus")));
        dto.setHCStatus(String.valueOf(deliveryData.get("HCStatus")));
        dto.setReferalSource(String.valueOf(deliveryData.get("referalSource")));
        dto.setNumberOfInfantsAlive(Integer.parseInt(String.valueOf(deliveryData.get("numberOfInfantsAlive"))));
        dto.setNumberOfInfantsDead(Integer.parseInt(String.valueOf(deliveryData.get("numberOfInfantsDead"))));
        dto.setPersonUuid(String.valueOf(patientUuid));
        dto.setPlaceOfDelivery(String.valueOf(deliveryData.get("placeOfDelivery")));

        return dto;
    }

    private PartnerInformation createPartnerInformation(Map<String, Object> partnerData) {
        System.out.println("Partner: "+partnerData);

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

    private InfantDto createInfantDto(Map<String, Object> infantData) {
        return InfantDto.builder()
                .dateOfDelivery(parseDate(infantData.get("dateOfDelivery")))
                .firstName((String) infantData.get("firstName"))
                .middleName((String) infantData.get("middleName"))
                .surname((String) infantData.get("surname"))
                .sex((String) infantData.get("sex"))
                .nin((String) infantData.get("nin"))
                .id((Long) infantData.get("id"))
                .hospitalNumber((String) infantData.get("hospitalNumber"))
                .uuid((String) infantData.get("uuid"))
                .ancNo((String) infantData.get("ancNo"))
                .infantOutcomeAt18Months((String) infantData.get("infantOutcomeAt18Months"))
                .personUuid((String) infantData.get("personUuid"))
                .bodyWeight((Double) infantData.get("bodyWeight"))
                .ctxStatus((String) infantData.get("ctxStatus"))
                .infantArvDto((InfantArvDto) infantData.get("infantArvDto"))
                .infantPCRTestDto((InfantPCRTestDto) infantData.get("infantPCRTestDto"))
                .build();
    }
    private InfantVisitationConsolidatedDto createChildFollowup(Map<String, Object> data) {
        System.out.println("child followup: "+data);
        InfantVisitationConsolidatedDto dto = new InfantVisitationConsolidatedDto();
        Map<String, Object> infantMotherArtData = (Map<String, Object>) data.get("infantMotherArtDto");
        if (infantMotherArtData != null) {
            Object regimenIdObj = infantMotherArtData.get("regimenId");
            Object regimenTypeIdObj = infantMotherArtData.get("regimenTypeId");

            if (regimenIdObj instanceof String && regimenTypeIdObj != null) {
                String description = (String) regimenIdObj;
                Long regimenTypeId = convertToLong(regimenTypeIdObj);
                System.out.println("description: "+description+"-regimenTypeId-"+regimenTypeIdObj);
                if (regimenTypeId != null) {
                    Long actualRegimenId = regimenRepository
                            .findByRegimenTypeIdAndDescription(regimenTypeId, description)
                            .map(Regimen::getId)
                            .orElse(null);
                    System.out.println("Actual regimenId"+actualRegimenId);
                    infantMotherArtData.put("regimenId", actualRegimenId);
                    System.out.println("infantMotherArtData After updating Actual regimenId"+infantMotherArtData);

                }
            }
        }
        dto.setInfantVisitRequestDto(objectMapper.convertValue(data.get("infantVisitRequestDto"), InfantVisitRequestDto.class));
        dto.setInfantMotherArtDto(objectMapper.convertValue(infantMotherArtData, InfantMotherArtDto.class));
        dto.setInfantArvDto(objectMapper.convertValue(data.get("infantArvDto"), InfantArvDto.class));
        dto.setInfantPCRTestDto(objectMapper.convertValue(data.get("infantPCRTestDto"), InfantPCRTestDto.class));
        dto.setInfantRapidAntiBodyTestDto(objectMapper.convertValue(data.get("infantRapidTestDTO"), InfantRapidAntiBodyTestDto.class));

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
                .resultOfViralLoad((Integer) visitData.get("resultOfViralLoad"))
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