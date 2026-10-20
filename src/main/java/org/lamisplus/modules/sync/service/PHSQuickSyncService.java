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
import org.lamisplus.modules.pmtct.domain.dto.*;
import org.lamisplus.modules.pmtct.domain.entity.PmtctPregnancyCycle;
import org.lamisplus.modules.pmtct.repository.PmtctPregnancyCycleRepository;
import org.lamisplus.modules.pmtct.service.ANCService;
import org.lamisplus.modules.pmtct.service.DeliveryService;
import org.lamisplus.modules.pmtct.service.InfantService;
import org.lamisplus.modules.pmtct.service.InfantVisitService;
import org.lamisplus.modules.pmtct.service.PmtctVisitService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PHSQuickSyncService {

    private final PersonService personService;
    private final PersonRepository personRepository;
    private final HtsEncounterService htsEncounterService;
    private final ANCService ancService;
    private final DeliveryService deliveryService;
    private final PmtctVisitService pmtctVisitService;
    private final InfantVisitService infantVisitService;
    private final InfantService infantService;
    private final IctEncounterService ictEncounterService;
    private final PmtctPregnancyCycleRepository pmtctPregnancyCycleRepository;
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
        int pmtctHtsCreated = 0, pmtctHtsSkipped = 0, pmtctHtsFailed = 0;
        int pmtctHtsRetestingCreated = 0, pmtctHtsRetestingSkipped = 0, pmtctHtsRetestingFailed = 0;
        int pmtctEnrollmentAncCreated = 0, pmtctEnrollmentAncSkipped = 0, pmtctEnrollmentAncFailed = 0;
        int pmtctLabourDeliveryCreated = 0, pmtctLabourDeliverySkipped = 0, pmtctLabourDeliveryFailed = 0;
        int pmtctAncRevisitCreated = 0, pmtctAncRevisitSkipped = 0, pmtctAncRevisitFailed = 0;
        int pmtctMipCreated = 0, pmtctMipSkipped = 0, pmtctMipFailed = 0;
        int pmtctMotherFollowUpVisitCreated = 0, pmtctMotherFollowUpVisitSkipped = 0, pmtctMotherFollowUpVisitFailed = 0;
        int pmtctChildFollowUpVisitCreated = 0, pmtctChildFollowUpVisitSkipped = 0, pmtctChildFollowUpVisitFailed = 0;
        int pmtctInfantRegistrationCreated = 0, pmtctInfantRegistrationSkipped = 0, pmtctInfantRegistrationFailed = 0;

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
                Object pmtctEnrollmentAncField = record.get("anc");
                Object pmtctHtsField = record.get("pmtctHts");
                Object pmtctHtsRetestingField = record.get("pmtctRetesting");
                Object pmtctLabourDeliveryField = record.get("labourDelivery");
                Object pmtctInfantRegistrationField = record.get("infantRegistration");
                Object pmtctAncRevisitField = record.get("ancRevisit");
                Object pmtctMipField = record.get("motherInfantPairCard");
                Object pmtctChildFollowUpVisitField = record.get("childFollowupVisit");
                Object pmtctMotherFollowUpVisitField = record.get("motherFollowupVisit");

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
                String personUuid = null;
                try {
                    Optional<Person> existingPerson = (hospitalNumber != null && !hospitalNumber.trim().isEmpty())
                            ? personRepository.getPersonByHospitalNumberAndFacilityId(hospitalNumber, facilityId)
                            : Optional.empty();

                    if (existingPerson.isPresent()) {
                        patientId = existingPerson.get().getId();
                        personUuid = existingPerson.get().getUuid();
                        successfulComponents.add("person (existing)");
                        personUpdated++;
                    } else {
                        PersonDto personDto = qrReaderService.convertToPersonDto(personData);
                        PersonResponseDto personResponseDto = personService.createPerson(personDto);
                        patientId = personResponseDto.getId();
                        personUuid = personResponseDto.getUuid();
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

                // 1.5 ENSURE PREGNANCY CYCLE EXISTS (if any PMTCT forms are present) -------
                // Extract pmtctCycleUuid from any PMTCT section in the payload
                String pmtctCycleUuid = extractPmtctCycleUuid(record);
                boolean hasPmtctCycle = pmtctCycleUuid != null && !pmtctCycleUuid.isEmpty();

                if (hasPmtctCycle) {
                    try {
                        ensurePregnancyCycleExists(pmtctCycleUuid, personUuid, record, facilityId, hospitalNumber);
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to ensure pregnancy cycle exists for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        // Don't fail the entire record, but log the issue
                        // PMTCT forms will fail later with foreign key constraint if cycle creation failed
                    }
                } else {
                    // No pmtctCycleUuid found - check if any PMTCT forms are present
                    boolean hasPmtctForms = pmtctEnrollmentAncField != null || pmtctHtsField != null ||
                                           pmtctHtsRetestingField != null || pmtctAncRevisitField != null ||
                                           pmtctMipField != null || pmtctLabourDeliveryField != null ||
                                           pmtctInfantRegistrationField != null || pmtctChildFollowUpVisitField != null ||
                                           pmtctMotherFollowUpVisitField != null;

                    if (hasPmtctForms) {
                        System.err.println("PHS sync: PMTCT forms present but pmtctCycleUuid missing for hospitalNumber=" + hospitalNumber + " - skipping all PMTCT forms");
                    }
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
                        System.err.println("PHS sync: failed to process hts encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("hts: " + e.getMessage());
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

                // 4. PMTCT_ENROLLMENT_ANC -----------------------------------------------------------------
                Long pmtctEnrollmentAncEncounterId = null;
                if (hasPmtctCycle && pmtctEnrollmentAncField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ancData = (Map<String, Object>) pmtctEnrollmentAncField;

                        // Extract nested objects using ObjectMapper
                        VitalSignsDto vitalSigns = ancData.containsKey("vitalSigns") && ancData.get("vitalSigns") != null
                                ? objectMapper.convertValue(ancData.get("vitalSigns"), VitalSignsDto.class) : null;
                        CounsellingDto counselling = ancData.containsKey("counselling") && ancData.get("counselling") != null
                                ? objectMapper.convertValue(ancData.get("counselling"), CounsellingDto.class) : null;
                        SyphilisInfoDto syphilisInfo = ancData.containsKey("syphilisInfo") && ancData.get("syphilisInfo") != null
                                ? objectMapper.convertValue(ancData.get("syphilisInfo"), SyphilisInfoDto.class) : null;
                        HepatitisBDto hepatitisBInfo = ancData.containsKey("hepatitisBInfo") && ancData.get("hepatitisBInfo") != null
                                ? objectMapper.convertValue(ancData.get("hepatitisBInfo"), HepatitisBDto.class) : null;
                        HepatitisCDto hepatitisCInfo = ancData.containsKey("hepatitisCInfo") && ancData.get("hepatitisCInfo") != null
                                ? objectMapper.convertValue(ancData.get("hepatitisCInfo"), HepatitisCDto.class) : null;
                        UrinalysisDto urinalysis = ancData.containsKey("urinalysis") && ancData.get("urinalysis") != null
                                ? objectMapper.convertValue(ancData.get("urinalysis"), UrinalysisDto.class) : null;
                        LabTestDto labTest = ancData.containsKey("labTest") && ancData.get("labTest") != null
                                ? objectMapper.convertValue(ancData.get("labTest"), LabTestDto.class) : null;
                        InterventionsDto interventions = ancData.containsKey("interventions") && ancData.get("interventions") != null
                                ? objectMapper.convertValue(ancData.get("interventions"), InterventionsDto.class) : null;

                        // Use AllArgsConstructor with patient_uuid (final field)
                        ANCEnrollementRequestDto ancEnrollementRequestDto = new ANCEnrollementRequestDto(
                                (String) ancData.get("source"),
                                (String) ancData.get("id"),
                                (String) ancData.get("ancNo"),
                                (String) ancData.get("ancAttendance"),
                                (String) ancData.get("referredFromSpokesSite"),
                                ancData.containsKey("dateOfEnrollment") && ancData.get("dateOfEnrollment") != null
                                        ? LocalDate.parse(ancData.get("dateOfEnrollment").toString()) : null,
                                parseInteger(ancData.get("gravida")),
                                parseInteger(ancData.get("parity")),
                                ancData.containsKey("lmp") && ancData.get("lmp") != null
                                        ? LocalDate.parse(ancData.get("lmp").toString()) : null,
                                parseInteger(ancData.get("gaweeks")),
                                personUuid, // patient_uuid
                                (String) ancData.get("pmtctCycleUuid"),
                                vitalSigns,
                                counselling,
                                syphilisInfo,
                                hepatitisBInfo,
                                hepatitisCInfo,
                                urinalysis,
                                labTest,
                                interventions,
                                (String) ancData.get("outcomeOfVisit"),
                                (String) ancData.get("referralReason"),
                                (String) ancData.get("transportationOut")
                        );

                        ANCRespondDto ancRespondDto = ancService.ANCEnrollement(ancEnrollementRequestDto);
                        pmtctEnrollmentAncEncounterId = ancRespondDto.getId();
                        resultBuilder.pmtctEnrollmentAncEncounterId(pmtctEnrollmentAncEncounterId);
                        resultBuilder.ancNo(ancEnrollementRequestDto.getAncNo());
                        successfulComponents.add("anc");
                        pmtctEnrollmentAncCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct enrollment ANC encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("anc: " + e.getMessage());
                        pmtctEnrollmentAncFailed++;
                    }
                } else {
                    pmtctEnrollmentAncSkipped++;
                }

                // 5. PMTCT_HTS -----------------------------------------------------------------
                Long pmtctHtsEncounterId = null;
                if (hasPmtctCycle && pmtctHtsField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> htsData = (Map<String, Object>) pmtctHtsField;

                        HtsEncounterRequestDTO pmtctHtsRequest =
                                objectMapper.convertValue(htsData, HtsEncounterRequestDTO.class);
                        pmtctHtsRequest.setPatientId(patientId);
                        if (pmtctHtsRequest.getFacilityId() == null) {
                            pmtctHtsRequest.setFacilityId(facilityId);
                        }
                        HtsEncounterResponse pmtctHtsResponse = htsEncounterService.save(pmtctHtsRequest);
                        pmtctHtsEncounterId = pmtctHtsResponse.getId();
                        resultBuilder.pmtctHtsEncounterId(pmtctHtsEncounterId);
                        resultBuilder.clientCode(pmtctHtsRequest.getClientCode());
                        successfulComponents.add("pmtctHts");
                        pmtctHtsCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct-hts encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("pmtctHts: " + e.getMessage());
                        pmtctHtsFailed++;
                    }
                } else {
                    pmtctHtsSkipped++;
                }

                // 6. PMTCT_RETESTING -----------------------------------------------------------------
                Long pmtctHtsRetestingEncounterId = null;
                if (hasPmtctCycle && pmtctHtsRetestingField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pmtctHtsRetestingData = (Map<String, Object>) pmtctHtsRetestingField;

                        HtsEncounterRequestDTO pmtctHtsRequest =
                                objectMapper.convertValue(pmtctHtsRetestingData, HtsEncounterRequestDTO.class);
                        pmtctHtsRequest.setPatientId(patientId);
                        if (pmtctHtsRequest.getFacilityId() == null) {
                            pmtctHtsRequest.setFacilityId(facilityId);
                        }

                        HtsEncounterResponse pmtctHtsResponse = htsEncounterService.save(pmtctHtsRequest);
                        pmtctHtsRetestingEncounterId = pmtctHtsResponse.getId();
                        resultBuilder.pmtctHtsRetestingEncounterId(pmtctHtsRetestingEncounterId);
                        resultBuilder.clientCode(pmtctHtsRequest.getClientCode());
                        successfulComponents.add("pmtctRetesting");
                        pmtctHtsRetestingCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct-hts retesting encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("pmtctRetesting: " + e.getMessage());
                        pmtctHtsRetestingFailed++;
                    }
                } else {
                    pmtctHtsRetestingSkipped++;
                }

                //  7. PMTCT_REVISIT -----------------------------------------------------------------
                Long pmtctAncRevisitEncounterId = null;
                if (hasPmtctCycle && pmtctAncRevisitField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pmtctAncRevisitFieldData = (Map<String, Object>) pmtctAncRevisitField;

                        PmtctVisitRequestDto pmtctVisitRequestDto =
                                objectMapper.convertValue(pmtctAncRevisitFieldData, PmtctVisitRequestDto.class);

                        PmtctVisitResponseDto pmtctVisitResponseDto = pmtctVisitService.save(pmtctVisitRequestDto);
                        pmtctAncRevisitEncounterId = Long.valueOf(pmtctVisitResponseDto.getId());
                        resultBuilder.pmtctRevisitEncounterId(pmtctAncRevisitEncounterId);
//                        resultBuilder.pmtctCycleUuid(pmtctVisitResponseDto.getPmtctCycleUuid());
                        successfulComponents.add("ancRevisit");
                        pmtctAncRevisitCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct anc revisit encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("ancRevisit: " + e.getMessage());
                        pmtctAncRevisitFailed++;
                    }
                } else {
                    pmtctAncRevisitSkipped++;
                }

                // 8. PMTCT_MIP -----------------------------------------------------------------
                Long pmtctMipId = null;
                if (hasPmtctCycle && pmtctMipField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mipData = (Map<String, Object>) pmtctMipField;

                        // Extract nested objects using ObjectMapper
                        VitalSignsDto mipVitalSigns = mipData.containsKey("vitalSigns") && mipData.get("vitalSigns") != null
                                ? objectMapper.convertValue(mipData.get("vitalSigns"), VitalSignsDto.class) : null;
                        CounsellingDto mipCounselling = mipData.containsKey("counselling") && mipData.get("counselling") != null
                                ? objectMapper.convertValue(mipData.get("counselling"), CounsellingDto.class) : null;
                        SyphilisInfoDto mipSyphilisInfo = mipData.containsKey("syphilisInfo") && mipData.get("syphilisInfo") != null
                                ? objectMapper.convertValue(mipData.get("syphilisInfo"), SyphilisInfoDto.class) : null;
                        HepatitisBDto mipHepatitisBInfo = mipData.containsKey("hepatitisBInfo") && mipData.get("hepatitisBInfo") != null
                                ? objectMapper.convertValue(mipData.get("hepatitisBInfo"), HepatitisBDto.class) : null;
                        HepatitisCDto mipHepatitisCInfo = mipData.containsKey("hepatitisCInfo") && mipData.get("hepatitisCInfo") != null
                                ? objectMapper.convertValue(mipData.get("hepatitisCInfo"), HepatitisCDto.class) : null;
                        UrinalysisDto mipUrinalysis = mipData.containsKey("urinalysis") && mipData.get("urinalysis") != null
                                ? objectMapper.convertValue(mipData.get("urinalysis"), UrinalysisDto.class) : null;
                        LabTestDto mipLabTest = mipData.containsKey("labTest") && mipData.get("labTest") != null
                                ? objectMapper.convertValue(mipData.get("labTest"), LabTestDto.class) : null;
                        InterventionsDto mipInterventions = mipData.containsKey("interventions") && mipData.get("interventions") != null
                                ? objectMapper.convertValue(mipData.get("interventions"), InterventionsDto.class) : null;

                        // Use AllArgsConstructor with patient_uuid (final field)
                        ANCEnrollementRequestDto pmtctMipRequestDto = new ANCEnrollementRequestDto(
                                (String) mipData.get("source"),
                                (String) mipData.get("id"),
                                (String) mipData.get("ancNo"),
                                (String) mipData.get("ancAttendance"),
                                (String) mipData.get("referredFromSpokesSite"),
                                mipData.containsKey("dateOfEnrollment") && mipData.get("dateOfEnrollment") != null
                                        ? LocalDate.parse(mipData.get("dateOfEnrollment").toString()) : null,
                                parseInteger(mipData.get("gravida")),
                                parseInteger(mipData.get("parity")),
                                mipData.containsKey("lmp") && mipData.get("lmp") != null
                                        ? LocalDate.parse(mipData.get("lmp").toString()) : null,
                                parseInteger(mipData.get("gaweeks")),
                                personUuid, // patient_uuid
                                (String) mipData.get("pmtctCycleUuid"),
                                mipVitalSigns,
                                mipCounselling,
                                mipSyphilisInfo,
                                mipHepatitisBInfo,
                                mipHepatitisCInfo,
                                mipUrinalysis,
                                mipLabTest,
                                mipInterventions,
                                (String) mipData.get("outcomeOfVisit"),
                                (String) mipData.get("referralReason"),
                                (String) mipData.get("transportationOut")
                        );

                        ANCRespondDto ancRespondDto = ancService.ANCEnrollement(pmtctMipRequestDto);
                        pmtctMipId = ancRespondDto.getId();
                        resultBuilder.pmtctEnrollmentAncEncounterId(pmtctMipId);
                        resultBuilder.ancNo(pmtctMipRequestDto.getAncNo());
                        successfulComponents.add("motherInfantPairCard");
                        pmtctMipCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct MIP for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("motherInfantPairCard: " + e.getMessage());
                        pmtctMipFailed++;
                    }
                } else {
                    pmtctMipSkipped++;
                }

                // 9. PMTCT_ENROLLMENT_L&D -----------------------------------------------------------------
                Long pmtctEnrollmentLndEncounterId = null;
                if (hasPmtctCycle && pmtctLabourDeliveryField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pmtctLabourDeliveryData = (Map<String, Object>) pmtctLabourDeliveryField;

                        DeliveryRequestDto deliveryRequestDto =
                                objectMapper.convertValue(pmtctLabourDeliveryData, DeliveryRequestDto.class);

                        DeliveryResponseDto deliveryResponseDto = deliveryService.save(deliveryRequestDto);
                        pmtctEnrollmentLndEncounterId = deliveryResponseDto.getId();
                        resultBuilder.pmtctEnrollmentAncEncounterId(pmtctEnrollmentLndEncounterId);
//                        resultBuilder.pmtctCycleUuid(deliveryResponseDto.getPmtctCycleUuid());
                        successfulComponents.add("labourDelivery");
                        pmtctLabourDeliveryCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct labour and delivery encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("labourDelivery: " + e.getMessage());
                        pmtctLabourDeliveryFailed++;
                    }
                } else {
                    pmtctLabourDeliverySkipped++;
                }

                // 10. PMTCT_INFANT_REGISTRATION -----------------------------------------------------------------
                Long pmtctInfantRegistrationEncounterId = null;
                if (hasPmtctCycle && pmtctInfantRegistrationField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> infantRegistrationFieldData = (Map<String, Object>) pmtctInfantRegistrationField;

                        InfantDto infantRegistrationRequestDto =
                                objectMapper.convertValue(infantRegistrationFieldData, InfantDto.class);

                        InfantDtoResponse infantRegistrationResponseDto = infantService.save(infantRegistrationRequestDto);
                        pmtctInfantRegistrationEncounterId = Long.valueOf(infantRegistrationResponseDto.getInfant().getId());
                        resultBuilder.infantRegistrationEncounterId(pmtctInfantRegistrationEncounterId);
                        successfulComponents.add("infantRegistration");
                        pmtctInfantRegistrationCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process infant registration encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("infantRegistration: " + e.getMessage());
                        pmtctInfantRegistrationFailed++;
                    }
                } else {
                    pmtctInfantRegistrationSkipped++;
                }

                // 11. PMTCT_CHILD_FOLLOW_UP_VISIT -----------------------------------------------------------------
                Long pmtctChildFollowUpVisitEncounterId = null;
                if (hasPmtctCycle && pmtctChildFollowUpVisitField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pmtctChildFollowUpVisitData = (Map<String, Object>) pmtctChildFollowUpVisitField;

                        InfantVisitRequestDto pmtctChildFollowUpVisitRequestDto =
                                objectMapper.convertValue(pmtctChildFollowUpVisitData, InfantVisitRequestDto.class);

                        InfantVisitResponseDto pmtctChildFollowUpVisitResponseDto = infantVisitService.save(pmtctChildFollowUpVisitRequestDto);
                        pmtctChildFollowUpVisitEncounterId = Long.valueOf(pmtctChildFollowUpVisitResponseDto.getId());
                        resultBuilder.pmtctChildFollowUpVisitEncounterId(pmtctChildFollowUpVisitEncounterId);
                        successfulComponents.add("childFollowupVisit");
                        pmtctChildFollowUpVisitCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct child follow up visit encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("childFollowupVisit: " + e.getMessage());
                        pmtctChildFollowUpVisitFailed++;
                    }
                } else {
                    pmtctChildFollowUpVisitSkipped++;
                }

                // 12. PMTCT_MOTHER_FOLLOW_UP_VISIT -----------------------------------------------------------------
                Long pmtctMotherFollowUpVisitEncounterId = null;
                if (hasPmtctCycle && pmtctMotherFollowUpVisitField instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pmtctMotherFollowUpVisitData = (Map<String, Object>) pmtctMotherFollowUpVisitField;

                        PmtctVisitRequestDto pmtctMotherFollowUpVisitRequestDto =
                                objectMapper.convertValue(pmtctMotherFollowUpVisitData, PmtctVisitRequestDto.class);

                        PmtctVisitResponseDto pmtctVisitResponseDto = pmtctVisitService.save(pmtctMotherFollowUpVisitRequestDto);
                        pmtctMotherFollowUpVisitEncounterId = Long.valueOf(pmtctVisitResponseDto.getId());
                        resultBuilder.pmtctMotherFollowUpVisitEncounterId(pmtctMotherFollowUpVisitEncounterId);
                        successfulComponents.add("motherFollowupVisit");
                        pmtctMotherFollowUpVisitCreated++;
                    } catch (Exception e) {
                        System.err.println("PHS sync: failed to process pmtct mother follow up visit encounter for hospitalNumber=" + hospitalNumber + " - " + e.getMessage());
                        failedComponents.add("motherFollowupVisit: " + e.getMessage());
                        pmtctMotherFollowUpVisitFailed++;
                    }
                } else {
                    pmtctMotherFollowUpVisitSkipped++;
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
                .pmtctHtsCreated(pmtctHtsCreated)
                .pmtctHtsSkipped(pmtctHtsSkipped)
                .pmtctHtsFailed(pmtctHtsFailed)
                .pmtctHtsRetestingCreated(pmtctHtsRetestingCreated)
                .pmtctHtsRetestingSkipped(pmtctHtsRetestingSkipped)
                .pmtctHtsRetestingFailed(pmtctHtsRetestingFailed)
                .pmtctEnrollmentAncCreated(pmtctEnrollmentAncCreated)
                .pmtctEnrollmentAncSkipped(pmtctEnrollmentAncSkipped)
                .pmtctEnrollmentAncFailed(pmtctEnrollmentAncFailed)
                .pmtctLabourDeliveryCreated(pmtctLabourDeliveryCreated)
                .pmtctLabourDeliverySkipped(pmtctLabourDeliverySkipped)
                .pmtctLabourDeliveryFailed(pmtctLabourDeliveryFailed)
                .pmtctAncRevisitCreated(pmtctAncRevisitCreated)
                .pmtctAncRevisitSkipped(pmtctAncRevisitSkipped)
                .pmtctAncRevisitFailed(pmtctAncRevisitFailed)
                .pmtctMipCreated(pmtctMipCreated)
                .pmtctMipSkipped(pmtctMipSkipped)
                .pmtctMipFailed(pmtctMipFailed)
                .pmtctMotherFollowUpVisitCreated(pmtctMotherFollowUpVisitCreated)
                .pmtctMotherFollowUpVisitSkipped(pmtctMotherFollowUpVisitSkipped)
                .pmtctMotherFollowUpVisitFailed(pmtctMotherFollowUpVisitFailed)
                .pmtctChildFollowUpVisitCreated(pmtctChildFollowUpVisitCreated)
                .pmtctChildFollowUpVisitSkipped(pmtctChildFollowUpVisitSkipped)
                .pmtctChildFollowUpVisitFailed(pmtctChildFollowUpVisitFailed)
                .pmtctInfantRegistrationCreated(pmtctInfantRegistrationCreated)
                .pmtctInfantRegistrationSkipped(pmtctInfantRegistrationSkipped)
                .pmtctInfantRegistrationFailed(pmtctInfantRegistrationFailed)
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

    /**
     * Safely parse an integer from an Object that could be a String, Number, or null.
     * Handles both "6" (String) and 6 (Integer) formats from JSON.
     */
    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String strValue = ((String) value).trim();
            if (strValue.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(strValue);
            } catch (NumberFormatException e) {
                System.err.println("PHS sync: could not parse integer from string: " + strValue);
                return null;
            }
        }
        return null;
    }

    /**
     * Extracts pmtctCycleUuid from any PMTCT section in the payload.
     * Checks multiple sections in priority order: anc, pmtctHts, ancRevisit, etc.
     */
    @SuppressWarnings("unchecked")
    private String extractPmtctCycleUuid(Map<String, Object> record) {
        // Priority order: check sections most likely to have the UUID first
        String[] pmtctSections = {
            "anc",
            "pmtctHts",
            "pmtctRetesting",
            "ancRevisit",
            "motherInfantPairCard",
            "labourDelivery",
            "infantRegistration",
            "childFollowupVisit",
            "motherFollowupVisit"
        };

        for (String section : pmtctSections) {
            Object sectionObj = record.get(section);
            if (sectionObj instanceof Map) {
                Map<String, Object> sectionData = (Map<String, Object>) sectionObj;
                Object uuid = sectionData.get("pmtctCycleUuid");
                if (uuid != null && !uuid.toString().trim().isEmpty()) {
                    return uuid.toString().trim();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void ensurePregnancyCycleExists(String pmtctCycleUuid, String patientUuid,
                                           Map<String, Object> record, Long facilityId,
                                           String hospitalNumber) {
        // Check if cycle already exists
        Optional<PmtctPregnancyCycle> existingCycle = pmtctPregnancyCycleRepository.findById(pmtctCycleUuid);
        if (existingCycle.isPresent()) {
            // Cycle already exists, nothing to do
            return;
        }

        // Cycle doesn't exist, create it
        System.out.println("PHS sync: Creating pregnancy cycle " + pmtctCycleUuid + " for hospitalNumber=" + hospitalNumber);

        // Extract entry point from payload (check multiple sources)
        String entryPoint = extractEntryPoint(record);

        PmtctPregnancyCycle cycle = new PmtctPregnancyCycle();
        cycle.setUuid(pmtctCycleUuid); // Use UUID from mobile
        cycle.setPatientUuid(patientUuid);
        cycle.setEntryPoint(entryPoint);
        cycle.setPmtctStatus("INACTIVE"); // Will be updated to ACTIVE by ANC Enrollment
        cycle.setFacilityId(facilityId);
        cycle.setArchived(false);
        cycle.setIsClosed(false);
        cycle.setCreatedDate(LocalDateTime.now());
        cycle.setLastModifiedDate(LocalDateTime.now());
        cycle.setCreatedBy("batch-sync");
        cycle.setLastModifiedBy("batch-sync");

        pmtctPregnancyCycleRepository.save(cycle);
        System.out.println("PHS sync: Successfully created pregnancy cycle " + pmtctCycleUuid);
    }


    @SuppressWarnings("unchecked")
    private String extractEntryPoint(Map<String, Object> record) {
        // Check ANC section first
        Object ancField = record.get("anc");
        if (ancField instanceof Map) {
            Map<String, Object> ancData = (Map<String, Object>) ancField;
            Object entryPoint = ancData.get("entryPoint");
            if (entryPoint != null && !entryPoint.toString().trim().isEmpty()) {
                return mapEntryPoint(entryPoint.toString());
            }
        }

        // Check Labour & Delivery section
        Object ldField = record.get("labourDelivery");
        if (ldField instanceof Map) {
            return "L&D";
        }

        // Check PMTCT HTS section
        Object htsField = record.get("pmtctHts");
        if (htsField instanceof Map) {
            Map<String, Object> htsData = (Map<String, Object>) htsField;
            Object entryPoint = htsData.get("pmtctTestEntryPoint");
            if (entryPoint != null && !entryPoint.toString().trim().isEmpty()) {
                return mapEntryPoint(entryPoint.toString());
            }
        }

        // Check MIP section
        Object mipField = record.get("motherInfantPairCard");
        if (mipField instanceof Map) {
            Map<String, Object> mipData = (Map<String, Object>) mipField;
            Object entryPoint = mipData.get("entryPoint");
            if (entryPoint != null && !entryPoint.toString().trim().isEmpty()) {
                return mapEntryPoint(entryPoint.toString());
            }
        }

        // Default to ANC if we can't determine
        return "ANC";
    }


    private String mapEntryPoint(String entryPoint) {
        if (entryPoint == null) {
            return "ANC";
        }

        // Map frontend codes to backend codes
        switch (entryPoint.toUpperCase()) {
            case "PMTCT_ENTRY_POINT_ANC":
            case "ANC":
                return "ANC";
            case "PMTCT_ENTRY_POINT_L&D":
            case "L&D":
            case "LABOUR":
            case "DELIVERY":
                return "L&D";
            case "PMTCT_ENTRY_POINT_POST-PARTUM":
            case "POST-PARTUM":
            case "POSTPARTUM":
                return "Post-partum";
            default:
                // Return as-is if unrecognized
                return entryPoint;
        }
    }
}