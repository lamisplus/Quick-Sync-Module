package org.lamisplus.modules.sync.service;//package org.lamisplus.modules.sync.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.lamisplus.modules.hts.domain.dto.HtsClientRequestDto;
import org.lamisplus.modules.hts.service.HtsClientService;
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

//        for (Map<String, Object> result : resultList) {
//            if (result.containsKey("person")) {
//                Map<String, Object> personData = (Map<String, Object>) result.get("person");
//                if (personData != null) {
//                    System.out.println("Person field exists and is not null: " + personData);
//                    PersonDto personDto = convertToPersonDto(personData);
//                    PersonResponseDto personResponseDto = personService.createPerson(personDto);
//                    System.out.println("Created person: " + personResponseDto);
//                } else {
//                    System.out.println("Person field is null.");
//                }
//            } else {
//                System.out.println("No 'person' field found in this result.");
//            }
//        }

        for (Map<String, Object> result : resultList) {
            if (result.containsKey("person")) {
                Object personField = result.get("person");
//                Object clientIntakeField = result.get("clientIntake");
                if (personField instanceof Map) {
                    // Single person data
                    Map<String, Object> personData = (Map<String, Object>) personField;
                    System.out.println("Processing single person data: " + personData);
                    PersonDto personDto = convertToPersonDto(personData);
                    PersonResponseDto personResponseDto = personService.createPerson(personDto);
                    System.out.println("Created person: " + personResponseDto);

                } else if (personField instanceof List) {
                    // List of person data
                    List<Map<String, Object>> personList = (List<Map<String, Object>>) personField;
                    for (Map<String, Object> personData : personList) {
                        System.out.println("Processing person data from list: " + personData);
                        PersonDto personDto = convertToPersonDto(personData);
                        PersonResponseDto personResponseDto = personService.createPerson(personDto);
                        System.out.println("Created person: " + personResponseDto);

                    }
                } else {
                    System.out.println("Unexpected type for 'person' field.");
                }
            } else {
                System.out.println("No 'person' field found in this result.");

            }
        }
        return resultList;
    }

    // Helper method to convert Integer to Long
    private Long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
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
//    private HtsClientRequestDto createHtsClientRequestDto(PersonResponseDto personResponseDto, Map<String, Object> jsonData) {
//        // Construct the HtsClientRequestDto with the required fields
//        return new HtsClientRequestDto(
//                (String) jsonData.get("targetGroup"),
//                (String) jsonData.get("clientCode"),
//                LocalDate.parse((String) jsonData.get("dateVisit")),
//                jsonData.get("referredFrom") != null ? Long.valueOf((Integer) jsonData.get("referredFrom")) : null,
//                (String) jsonData.get("testingSetting"),
//                (String) jsonData.get("riskStratificationCode"),
//                (Boolean) jsonData.get("firstTimeVisit"),
//                jsonData.get("numChildren") != null ? (Integer) jsonData.get("numChildren") : null,
//                jsonData.get("numWives") != null ? (Integer) jsonData.get("numWives") : null,
//                jsonData.get("typeCounseling") != null ? Long.valueOf((Integer) jsonData.get("typeCounseling")) : null,
//                (Boolean) jsonData.get("indexClient"),
//                (String) jsonData.get("indexClientCode"),
//                (Boolean) jsonData.get("previouslyTested"),
//                jsonData.get("extra"),
//                jsonData.get("pregnant") != null ? Long.valueOf((Integer) jsonData.get("pregnant")) : null,
//                (Boolean) jsonData.get("breastFeeding"),
//                jsonData.get("relationWithIndexClient") != null ? Long.valueOf((Integer) jsonData.get("relationWithIndexClient")) :null
//       );
//    }




}