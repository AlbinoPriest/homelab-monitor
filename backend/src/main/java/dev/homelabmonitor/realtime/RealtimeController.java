package dev.homelabmonitor.realtime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
class RealtimeController {
	private final RealtimeBroker broker;

	RealtimeController(RealtimeBroker broker) {
		this.broker = broker;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream(HttpServletRequest request) {
		return broker.subscribe(request.getSession().getId());
	}
}
