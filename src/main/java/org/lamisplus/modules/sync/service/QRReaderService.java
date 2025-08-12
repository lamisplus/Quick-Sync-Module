package org.lamisplus.modules.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.IllegalSaslStateException;
import org.jetbrains.annotations.NotNull;
import org.lamisplus.modules.base.domain.entities.OrganisationUnit;
import org.lamisplus.modules.base.domain.repositories.OrganisationUnitRepository;
import org.lamisplus.modules.hts.domain.dto.*;
import org.lamisplus.modules.hts.service.*;
import org.lamisplus.modules.patient.domain.dto.*;
import org.lamisplus.modules.patient.domain.entity.Person;

import org.lamisplus.modules.patient.service.PersonService;
import org.lamisplus.modules.sync.domain.QuickSyncHistory;
import org.lamisplus.modules.sync.domain.dto.QuickSyncHistoryDTO;
import org.lamisplus.modules.sync.repository.QuickSyncHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import scala.Int;

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
    private final QuickSyncHistoryRepository quickSyncHistoryRepository;
    private final OrganisationUnitRepository organisationUnitRepository;

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

                    if (personField instanceof Map) {
                        // Process single person data
                        Map<String, Object> personData = (Map<String, Object>) personField;
                        PersonDto personDto = convertToPersonDto(personData);
                        PersonResponseDto personResponseDto = personService.createPerson(personDto);

                        if (personResponseDto != null) {
                            Long patientId = personResponseDto.getId();
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

                                if (htsClientDto != null && preTestField != null) {
                                    HtsPreTestCounselingDto htsPreTestCounselingDto = createPreTestCounseling(preTestData, htsClientDto.getId(), patientId);
                                    htsClientService.updatePreTestCounseling(htsClientDto.getId(), htsPreTestCounselingDto);
                                }

                                if (htsClientDto != null && requestResultField != null) {
                                    HtsRequestResultDto htsRequestResultDto = createRequestResult(requestResultData, htsClientDto.getId(), patientId);
                                    htsClientService.updateRequestResult(htsClientDto.getId(), htsRequestResultDto);
                                }

                                if (htsClientDto != null && postTestField != null) {
                                    PostTestCounselingDto postTestCounselingDto = createPostTestCounseling(postTestData, htsClientDto.getId(), patientId);
                                    htsClientService.updatePostTestCounselingKnowledgeAssessment(htsClientDto.getId(), postTestCounselingDto);
                                }

                                if (htsClientDto != null && recencyField != null) {
                                    HtsRecencyDto recencyDto = createRecency(recencyData, htsClientDto.getId(), patientId);
                                    htsClientService.updateRecency(htsClientDto.getId(), recencyDto);
                                }

                                if (htsClientDto != null && elicitationField != null) {
                                    IndexElicitationDto indexElicitationDto = createIndexElicitation(elicitationData, htsClientDto.getId());
                                    indexElicitationService.save(indexElicitationDto);
                                }
                                if (htsClientDto != null && familyIndexTestingField != null) {
                                    FamilyIndexTestingRequestDTO familyIndexTestingRequestDTO = createFamilyIndexTesting(familyIndexTestingData, htsClientDto.getHtsClientUUid(), htsClientDto.getId());
                                    familyIndexTestingService.save(familyIndexTestingRequestDTO);
                                }
                                if (htsClientDto != null && htsClientReferralField != null) {
                                    HtsClientReferralRequestDTO htsClientReferralRequestDTO  = createHtsClientReferral(htsClientReferralData, htsClientDto.getHtsClientUUid(), htsClientDto.getId());
                                    clientReferralService.registerClientReferralForm(htsClientReferralRequestDTO);
                                }
                                if (htsClientDto != null && partnerNotificationServicesField != null) {
                                    PersonalNotificationServiceRequestDTO personalNotificationServiceRequestDTO  = createPartnerNotificationServices(partnerNotificationServicesData, htsClientDto.getHtsClientUUid(), htsClientDto.getId());
                                    pnsService.save(personalNotificationServiceRequestDTO);
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
        String address = (String) familyIndexTestingData.get("address");
        Integer age = convertToInteger(familyIndexTestingData.get("age"));
        String alternatePhoneNumber = (String) familyIndexTestingData.get("alternatePhoneNumber");
        String contactId = (String) familyIndexTestingData.get("contactId");
        LocalDate dateClientEnrolledOnTreatment = parseDate(familyIndexTestingData.get("dateClientEnrolledOnTreatment"));
        LocalDate dateIndexClientConfirmedHivPositiveTestResult = parseDate(familyIndexTestingData.get("dateIndexClientConfirmedHivPositiveTestResult"));
        LocalDate dateOfBirth = parseDate(familyIndexTestingData.get("dateOfBirth"));
        String facilityName = (String) familyIndexTestingData.get("facilityName");
        String familyIndexClient = (String) familyIndexTestingData.get("familyIndexClient");
        String indexClientId = (String) familyIndexTestingData.get("indexClientId");
        String isClientCurrentlyOnHivTreatment =  (String) familyIndexTestingData.get("isClientCurrentlyOnHivTreatment");
        Double latitude = convertToDouble(familyIndexTestingData.get("latitude"));
        Double longitude = convertToDouble(familyIndexTestingData.get("longitude"));
        String lga = (String) familyIndexTestingData.get("lga");
        Long maritalStatus = convertToLong(familyIndexTestingData.get("maritalStatus"));
        String name = (String) familyIndexTestingData.get("name");
        String phoneNumber = (String) familyIndexTestingData.get("phoneNumber");
        String recencyTesting = (String) familyIndexTestingData.get("recencyTesting");
        String setting = (String) familyIndexTestingData.get("setting");
        Long sex = convertToLong(familyIndexTestingData.get("sex"));
        Long state = convertToLong(familyIndexTestingData.get("state"));
        Object extra = familyIndexTestingData.get("extra");
        String virallyUnSuppressed = (String)  familyIndexTestingData.get("virallyUnSuppressed");
        LocalDate visitDate = parseDate(familyIndexTestingData.get("visitDate"));
        String willingToHaveChildrenTestedElseWhere = (String) familyIndexTestingData.get("willingToHaveChildrenTestedElseWhere");
//        String source = (String) familyIndexTestingData.get("source");

        // You can expand this to map nested DTOs as needed

        return FamilyIndexTestingRequestDTO.builder()
                .htsClientId(htsClientId)
                .extra(extra)
                .htsClientUuid(htsClientUuid)
                .age(String.valueOf(age))
                .alternatePhoneNumber(alternatePhoneNumber)
//                .contactId(contactId)
                .dateClientEnrolledOnTreatment(String.valueOf(dateClientEnrolledOnTreatment))
                .dateIndexClientConfirmedHivPositiveTestResult(dateIndexClientConfirmedHivPositiveTestResult)
                .dateOfBirth(dateOfBirth)
                .facilityName(facilityName)
                .familyIndexClient(familyIndexClient)
                .indexClientId(indexClientId)
                .isClientCurrentlyOnHivTreatment(String.valueOf(isClientCurrentlyOnHivTreatment))
//                .latitude(latitude)
//                .longitude(longitude)
                .lga(lga)
                .maritalStatus(String.valueOf(maritalStatus))
                .name(name)
                .phoneNumber(phoneNumber)
                .recencyTesting(recencyTesting)
                .setting(setting)
                .sex(String.valueOf(sex))
                .state(String.valueOf(state))
                .virallyUnSuppressed(String.valueOf(virallyUnSuppressed))
                .visitDate(visitDate)
                .willingToHaveChildrenTestedElseWhere(String.valueOf(willingToHaveChildrenTestedElseWhere))
//                .source(source)
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