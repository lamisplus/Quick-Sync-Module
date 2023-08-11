package org.lamisplus.modules.sync.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.modules.sync.domain.QuickSyncHistory;
import org.lamisplus.modules.sync.domain.dto.QuickSyncHistoryDTO;
import org.lamisplus.modules.sync.service.PersonQuickSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/quick-sync")
public class QuickSyncController {
	private final SimpMessageSendingOperations messagingTemplate;
	
	private final PersonQuickSyncService questionQuickSyncService;
	
	@GetMapping("/export/person-data")
	public void exportPersonData(HttpServletResponse response,
			@RequestParam("facilityId") Long facility,
			@RequestParam("startDate") LocalDate start,
			@RequestParam("endDate") LocalDate end) throws IOException {
			//messagingTemplate.convertAndSend("/topic/person-data", "start");
			ByteArrayOutputStream baos = questionQuickSyncService.generatePersonData(response, facility, start, end);
			setStream(baos, response);
			//messagingTemplate.convertAndSend("/topic/person-data", "end");
	}
	
	@PostMapping("/import/person-data")
	public ResponseEntity<QuickSyncHistoryDTO> importPersonData(@RequestParam("facilityId") Long facility, @RequestParam("file") MultipartFile file) throws IOException {
		return  ResponseEntity.ok(questionQuickSyncService.importPersonData(facility, file));
	}
	
	@PostMapping("/import/biometric-data")
	public ResponseEntity<QuickSyncHistoryDTO> importBiometricData(@RequestParam("facilityId") Long facility, @RequestParam("file") MultipartFile file) throws IOException {
		return  ResponseEntity.ok(questionQuickSyncService.importBiometricData(facility, file));
	}
	
	@GetMapping("/export/biometric-data")
	public void exportBiometricData(HttpServletResponse response,
	                             @RequestParam("facilityId") Long facility,
	                             @RequestParam("startDate") LocalDate start,
	                             @RequestParam("endDate") LocalDate end) throws IOException {
		//messagingTemplate.convertAndSend("/topic/biometric-data", "start");
		ByteArrayOutputStream baos = questionQuickSyncService.generateBiometricData(response, facility, start, end);
		setStream(baos, response);
		//messagingTemplate.convertAndSend("/topic/biometric-data", "end");
	}
	
	@GetMapping("/history")
	public ResponseEntity<List<QuickSyncHistory>> getQuickSyncHistory() {
		return ResponseEntity.ok(questionQuickSyncService.getQuickSyncHistory());
	}

	private void setStream(ByteArrayOutputStream baos, HttpServletResponse response) throws IOException {
		response.setHeader("Content-Type", "application/octet-stream");
		response.setHeader("Content-Length", Integer.toString(baos.size()));
		OutputStream outputStream = response.getOutputStream();
		outputStream.write(baos.toByteArray());
		outputStream.close();
		response.flushBuffer();
	}
}
