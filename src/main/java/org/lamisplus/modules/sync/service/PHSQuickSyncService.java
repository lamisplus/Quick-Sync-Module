package org.lamisplus.modules.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.modules.base.domain.entities.OrganisationUnit;
import org.lamisplus.modules.base.domain.repositories.OrganisationUnitRepository;
import org.lamisplus.modules.hts.domain.dto.HtsEncounterRequestDTO;
import org.lamisplus.modules.hts.domain.dto.HtsEncounterResponse;
import org.lamisplus.modules.hts.domain.dto.IctEncounterRequest;
import org.lamisplus.modules.hts.domain.dto.IctEncounterResponse;
import org.lamisplus.modules.hts.service.HtsEncounterService;
import org.lamisplus.modules.hts.service.IctEncounterService;
import org.lamisplus.modules.patient.domain.dto.PersonDto;
import org.lamisplus.modules.patient.domain.dto.PersonResponseDto;
import org.lamisplus.modules.patient.domain.entity.Person;
import org.lamisplus.modules.patient.repository.PersonRepository;
import org.lamisplus.modules.patient.service.PersonService;
import org.lamisplus.modules.sync.domain.QuickSyncHistory;
import org.lamisplus.modules.sync.domain.dto.PhsSyncRecordResult;
import org.lamisplus.modules.sync.domain.dto.PhsSyncResponse;
import org.lamisplus.modules.sync.domain.dto.PhsSyncSummary;
import org.lamisplus.modules.sync.repository.QuickSyncHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Processes PHS (Person -> HTS -> ICT) quick-sync zip bundles.
 * <p>
 * Structurally this mirrors {@link QRReaderService}: a zip file made up of one entry per
 * record, each entry being a base64-encoded, gzip-compressed JSON payload. The difference
 * is the shape of the payload — instead of the HtsClient/RiskStratification/... structure
 * that QRReaderService expects, each record here is a flat bundle of three optional keys:
 * <pre>
 * {
 *   "person": { ... PersonDto-shaped fields ... },
 *   "hts":    { ... HtsEncounterRequestDTO-shaped fields ... },
 *   "ict":    { ... IctEncounterRequest-shaped fields, including "contacts" ... }
 * }
 * </pre>
 * For each record: the person is created (or matched to an existing person on the same
 * facility by hospital number), then — if present — an HTS encounter is saved against that
 * person, then — if present — an ICT encounter is saved against that person and auto-linked
 * to the HTS encounter that was just created/updated in the same record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PHSQuickSyncService {

    private final PersonService personService;
    private final PersonRepository personRepository;
    private final HtsEncounterService htsEncounterService;
    private final IctEncounterService ictEncounterService;
    private final OrganisationUnitRepository organisationUnitRepository;
    private final QuickSyncHistoryRepository quickSyncHistoryRepository;
    private final QRReaderService qrReaderService;
    private final ObjectMapper objectMapper;

    private static final String TABLE_NAME = "PHS_SYNC";

    public PhsSyncResponse processZipFile(Long facilityId, MultipartFile multipartFile) throws IOException {
        long startTime = System.currentTimeMillis();

        String fileName = multipartFile.getOriginalFilename();
        OrganisationUnit facility = organisationUnitRepository.getOne(facilityId);
        int fileSizeInMB = (int) Math.ceil(multipartFile.getSize() / (1024.0 * 1024.0));

        if (Boolean.TRUE.equals(quickSyncHistoryRepository.existsByFilename(fileName))) {
            throw new IllegalArgumentException(
                    "This file has already been uploaded and processed. Please upload a different file.");
        }

        int totalRecords = 0;
        int completelySuccessful = 0;
        int partiallySuccessful = 0;
        int completelyFailed = 0;
        int skippedRecords = 0;

        int personCreated = 0, personUpdated = 0, personFailed = 0;
        int htsCreated = 0, htsSkipped = 0, htsFailed = 0;
        int ictCreated = 0, ictSkipped = 0, ictFailed = 0;

        List<PhsSyncRecordResult> allResults = new ArrayList<>();

        byte[] fileBytes = multipartFile.getBytes();

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
             ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                totalRecords++;

                String base64CompressedData = readZipEntry(zipInputStream);
                String decompressedData = decompressAndDecode(base64CompressedData);
                Map<String, Object> record = objectMapper.readValue(
                        decompressedData, new TypeReference<Map<String, Object>>() {});

                PhsSyncRecordResult.PhsSyncRecordResultBuilder resultBuilder = PhsSyncRecordResult.builder();
                List<String> successfulComponents = new ArrayList<>();
                List<String> failedComponents = new ArrayList<>();

                Object personField = record.get("person");
                Object htsField = record.get("hts");
                Object ictField = record.get("ict");

                if (!(personField instanceof Map)) {
                    // Nothing to anchor this record to — skip it.
                    skippedRecords++;
                    allResults.add(resultBuilder
                            .status("SKIPPED")
                            .message("Record has no 'person' object")
                            .successfulComponents(successfulComponents)
                            .failedComponents(failedComponents)
                            .build());
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> personData = (Map<String, Object>) personField;

                // Facility guard — same behaviour as QRReaderService: a mismatched facility
                // aborts the whole batch rather than silently importing into the wrong place.
                Object personFacilityIdObj = personData.get("facilityId");
                if (personFacilityIdObj != null) {
                    Long personFacilityId = ((Number) personFacilityIdObj).longValue();
                    if (!personFacilityId.equals(facilityId)) {
                        throw new IllegalArgumentException(
                                "Upload failed: This file belongs to a different facility. " +
                                        "Please ensure you are uploading the correct file for your assigned facility.");
                    }
                }

                String hospitalNumber = extractHospitalNumberFromPerson(personData);
                resultBuilder.hospitalNumber(hospitalNumber);

                Long patientId = null;

                // 1. PERSON ------------------------------------------------------------
                try {
                    Optional<Person> existingPerson = (hospitalNumber != null && !hospitalNumber.trim().isEmpty())
                            ? personRepository.getPersonByHospitalNumberAndFacilityId(hospitalNumber, facilityId)
                            : Optional.empty();

                    if (existingPerson.isPresent()) {
                        patientId = existingPerson.get().getId();
                        successfulComponents.add("person (existing)");
                        personUpdated++;
                    } else {
                        PersonDto personDto = qrReaderService.convertToPersonDto(personData);
                        PersonResponseDto personResponseDto = personService.createPerson(personDto);
                        patientId = personResponseDto.getId();
                        successfulComponents.add("person (created)");
                        personCreated++;
                    }
                    resultBuilder.personId(patientId);
                } catch (Exception e) {
                    System.err.println("PHS sync: failed to process person for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                    failedComponents.add("person: " + e.getMessage());
                    personFailed++;
                    // No person => nothing downstream can be linked; close out this record.
                    allResults.add(resultBuilder
                            .status("COMPLETELY_FAILED")
                            .message("Person could not be created/matched")
                            .successfulComponents(successfulComponents)
                            .failedComponents(failedComponents)
                            .build());
                    completelyFailed++;
                    continue;
                }

                // 2. HTS -----------------------------------------------------------------
                Long htsEncounterId = null;
                if (htsField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> htsData = (Map<String, Object>) htsField;

                        HtsEncounterRequestDTO htsRequest =
                                objectMapper.convertValue(htsData, HtsEncounterRequestDTO.class);
                        htsRequest.setPatientId(patientId);
                        if (htsRequest.getFacilityId() == null) {
                            htsRequest.setFacilityId(facilityId);
                        }

                        HtsEncounterResponse htsResponse = htsEncounterService.save(htsRequest);
                        htsEncounterId = htsResponse.getId();
                        resultBuilder.htsEncounterId(htsEncounterId);
                        resultBuilder.clientCode(htsRequest.getClientCode());
                        successfulComponents.add("hts");
                        htsCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process hts encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());                        failedComponents.add("hts: " + e.getMessage());
                        htsFailed++;
                    }
                } else {
                    htsSkipped++;
                }

                // 3. ICT -------------------------------------------------------------------
                if (ictField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ictData = (Map<String, Object>) ictField;

                        IctEncounterRequest ictRequest =
                                objectMapper.convertValue(ictData, IctEncounterRequest.class);
                        ictRequest.setPatientId(patientId);
                        if (ictRequest.getFacilityId() == null) {
                            ictRequest.setFacilityId(facilityId);
                        }
                        // Auto-link to the HTS encounter just saved for this same record,
                        // unless the payload already specified one explicitly.
                        if (ictRequest.getHtsEncounterId() == null && htsEncounterId != null) {
                            ictRequest.setHtsEncounterId(htsEncounterId);
                        }

                        IctEncounterResponse ictResponse = ictEncounterService.save(ictRequest);
                        resultBuilder.ictEncounterId(ictResponse.getId());
                        successfulComponents.add("ict");
                        ictCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process hts encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("ict: " + e.getMessage());
                        ictFailed++;
                    }
                } else {
                    ictSkipped++;
                }

                // Roll up this record's status ---------------------------------------------
                String status;
                String message;
                if (!failedComponents.isEmpty() && successfulComponents.size() <= 1) {
                    // only "person" (or nothing) succeeded
                    status = "COMPLETELY_FAILED";
                    message = "Downstream components failed to sync";
                    completelyFailed++;
                } else if (!failedComponents.isEmpty()) {
                    status = "PARTIALLY_SUCCESSFUL";
                    message = "Some components failed to sync";
                    partiallySuccessful++;
                } else {
                    status = "COMPLETELY_SUCCESSFUL";
                    message = "All components synced successfully";
                    completelySuccessful++;
                }

                allResults.add(resultBuilder
                        .status(status)
                        .message(message)
                        .successfulComponents(successfulComponents)
                        .failedComponents(failedComponents)
                        .build());
            }
        }

        saveQuickSyncHistory(fileName, facility, fileSizeInMB, totalRecords);

        PhsSyncSummary summary = PhsSyncSummary.builder()
                .personCreated(personCreated)
                .personUpdated(personUpdated)
                .personFailed(personFailed)
                .htsCreated(htsCreated)
                .htsSkipped(htsSkipped)
                .htsFailed(htsFailed)
                .ictCreated(ictCreated)
                .ictSkipped(ictSkipped)
                .ictFailed(ictFailed)
                .build();

        List<PhsSyncRecordResult> problemRecords = new ArrayList<>();
        List<PhsSyncRecordResult> successfulRecords = new ArrayList<>();
        for (PhsSyncRecordResult r : allResults) {
            if ("COMPLETELY_SUCCESSFUL".equals(r.getStatus())) {
                successfulRecords.add(r);
            } else {
                problemRecords.add(r);
            }
        }

        long processingTime = System.currentTimeMillis() - startTime;

        return PhsSyncResponse.builder()
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
                .successfulRecords(successfulRecords)
                .processingTimeMs(processingTime)
                .build();
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private void saveQuickSyncHistory(String fileName, OrganisationUnit facility, int fileSizeInMB, int recordsCount) {
        QuickSyncHistory history = new QuickSyncHistory();
        history.setFilename(fileName);
        history.setStatus("completed");
        history.setTableName(TABLE_NAME);
        history.setFileSize(fileSizeInMB);
        history.setFacilityName(facility.getName());
        history.setDateCreated(LocalDateTime.now());
        history.setRecordsCount(recordsCount);
        quickSyncHistoryRepository.save(history);
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

    private String decompressAndDecode(String base64CompressedData) throws IOException {
        base64CompressedData = base64CompressedData.replaceAll("\\s", "");
        byte[] compressedData = Base64.getDecoder().decode(base64CompressedData);
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

    @SuppressWarnings("unchecked")
    private String extractHospitalNumberFromPerson(Map<String, Object> personData) {
        try {
            Object identifierObj = personData.get("identifier");
            if (identifierObj == null) {
                return null;
            }
            String identifierJson = identifierObj.toString();
            if (identifierJson.trim().isEmpty()) {
                return null;
            }
            List<Map<String, Object>> identifiers = objectMapper.readValue(
                    identifierJson, new TypeReference<List<Map<String, Object>>>() {});
            if (identifiers == null) {
                return null;
            }
            for (Map<String, Object> identifier : identifiers) {
                if (identifier != null && "HospitalNumber".equals(identifier.get("type"))) {
                    Object value = identifier.get("value");
                    return value != null ? value.toString() : null;
                }
            }
        } catch (Exception e) {
            System.err.println("PHS sync: could not extract hospital number from person data - " + e.getMessage());        }
        return null;
    }
}