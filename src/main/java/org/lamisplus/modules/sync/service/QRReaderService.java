package org.lamisplus.modules.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.lamisplus.modules.hts.domain.dto.*;
import org.lamisplus.modules.hts.service.HtsClientService;
import org.lamisplus.modules.hts.service.IndexElicitationService;
import org.lamisplus.modules.hts.service.RiskStratificationService;
import org.lamisplus.modules.patient.domain.dto.*;
import org.lamisplus.modules.patient.domain.entity.Person;

import org.lamisplus.modules.patient.service.PersonService;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public List<Map<String, Object>> processZipFile(byte[] fileBytes) throws IOException {
        List<Map<String, Object>> resultList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        // Convert the byte array to a ZipInputStream
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

                    // Safely cast fields to their expected types
                    Map<String, Object> clientIntakeData = (Map<String, Object>) clientIntakeField;
                    Map<String, Object> riskStratificationData = (Map<String, Object>) riskStratificationField;
                    Map<String, Object> preTestData = (Map<String, Object>) preTestField;
                    Map<String, Object> requestResultData = (Map<String, Object>) requestResultField;
                    Map<String, Object> postTestData = (Map<String, Object>) postTestField;
                    Map<String, Object> recencyData = (Map<String, Object>) recencyField;
                    Map<String, Object> elicitationData = (Map<String, Object>) elicitationField;

                    if (personField instanceof Map) {
                        // Process single person data
                        Map<String, Object> personData = (Map<String, Object>) personField;
                        PersonDto personDto = convertToPersonDto(personData);
                        PersonResponseDto personResponseDto = personService.createPerson(personDto);

                        if (personResponseDto != null) {
                            Long patientId = personResponseDto.getId();

                            // Create and save the RiskStratificationDto
                            RiskStratificationDto riskStratificationDto = createRiskStratification(riskStratificationData);
                            riskStratificationDto.setPersonId(patientId);
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
                            }
                        }
                    }
                }
            }
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
//        Boolean prepOffered = (Boolean) requestResultData.getOrDefault("prepOffered", null);
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

}