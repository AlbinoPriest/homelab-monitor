package dev.homelabmonitor.incident;

import dev.homelabmonitor.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/v1/incidents")
class IncidentController {
	private final IncidentService incidentService;

	IncidentController(IncidentService incidentService) { this.incidentService = incidentService; }

	@GetMapping
	PageResponse<IncidentResponse> list(
			@RequestParam(required = false) UUID monitorId,
			@RequestParam(required = false) IncidentStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return incidentService.list(monitorId, status, page, size);
	}
}
