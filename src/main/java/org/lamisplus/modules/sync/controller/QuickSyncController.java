package org.lamisplus.modules.sync.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.modules.sync.service.PersonQuickSyncService;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;

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
//			@RequestParam(name = "biometric", defaultValue = "false") boolean biometric,
//			@RequestParam(name = "hts", defaultValue = "false") boolean hts,
//			@RequestParam(name = "patient", defaultValue ="false") boolean patient,
			@RequestParam("startDate") LocalDate start,
			@RequestParam("endDate") LocalDate end) throws IOException {
		messagingTemplate.convertAndSend("/topic/quick-sync", "start");
			ByteArrayOutputStream baos = questionQuickSyncService.generatePersonData(response, facility, start, end);
			setStream(baos, response);
			messagingTemplate.convertAndSend("/topic/quick-sync", "end");
	}
	
	@PostMapping("/import/person-data")
	public String importPersonData(@RequestParam("facilityId") Long facility, @RequestParam("file") MultipartFile file) throws IOException {
	  return  questionQuickSyncService.importPersonData(facility, file);
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
