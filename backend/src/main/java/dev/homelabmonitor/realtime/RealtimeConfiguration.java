package dev.homelabmonitor.realtime;

import jakarta.servlet.http.HttpSessionListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RealtimeConfiguration {
	@Bean(name = "realtimeDeliveryExecutor", destroyMethod = "shutdownNow")
	ExecutorService realtimeDeliveryExecutor() {
		return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("realtime-delivery-", 0).factory());
	}

	@Bean
	ServletListenerRegistrationBean<HttpSessionListener> realtimeSessionListener(RealtimeBroker broker) {
		return new ServletListenerRegistrationBean<>(new RealtimeSessionListener(broker));
	}
}
