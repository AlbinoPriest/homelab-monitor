package dev.homelabmonitor.analytics;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AnalyticsController {
	private final AnalyticsService service;

	AnalyticsController(AnalyticsService service) { this.service = service; }

	@GetMapping("/analytics")
	AnalyticsResponse overview(@RequestParam(defaultValue = "24h") String window) {
		return service.overview(window);
	}

	@GetMapping("/monitors/{id}/metrics")
	MonitorMetricsResponse monitor(
			@PathVariable UUID id,
			@RequestParam(defaultValue = "24h") String window) {
		return service.monitor(id, window);
	}
}
