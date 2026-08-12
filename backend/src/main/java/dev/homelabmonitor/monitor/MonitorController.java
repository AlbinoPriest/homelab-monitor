package dev.homelabmonitor.monitor;

import dev.homelabmonitor.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/monitors")
class MonitorController {

	private final MonitorService monitorService;
	private final MonitorCheckCoordinator coordinator;

	MonitorController(MonitorService monitorService, MonitorCheckCoordinator coordinator) {
		this.monitorService = monitorService;
		this.coordinator = coordinator;
	}

	@GetMapping
	List<MonitorResponse> list() {
		return monitorService.list();
	}

	@PostMapping
	ResponseEntity<MonitorResponse> create(@Valid @RequestBody MonitorRequest request) {
		MonitorResponse created = monitorService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/monitors/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	MonitorResponse get(@PathVariable UUID id) {
		return monitorService.get(id);
	}

	@PutMapping("/{id}")
	MonitorResponse update(@PathVariable UUID id, @Valid @RequestBody MonitorRequest request) {
		return monitorService.update(id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		monitorService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/checks")
	MonitorCheckResponse checkNow(@PathVariable UUID id) {
		return coordinator.executeNow(id);
	}

	@GetMapping("/{id}/checks")
	PageResponse<MonitorCheckResponse> checks(
			@PathVariable UUID id,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return monitorService.checks(id, page, size);
	}

	@GetMapping("/{id}/history")
	PageResponse<MonitorStateHistoryResponse> history(
			@PathVariable UUID id,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return monitorService.history(id, page, size);
	}
}
