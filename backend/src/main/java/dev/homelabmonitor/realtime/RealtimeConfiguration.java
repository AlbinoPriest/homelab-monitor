package dev.homelabmonitor.realtime;

import jakarta.servlet.http.HttpSessionListener;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class RealtimeConfiguration {
	@Bean(name = "realtimeDeliveryExecutor")
	Executor realtimeDeliveryExecutor(
			@Value("${homelab-monitor.realtime.max-connections:8}") int maxConnections) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(maxConnections);
		executor.setMaxPoolSize(maxConnections);
		executor.setQueueCapacity(maxConnections);
		executor.setThreadNamePrefix("realtime-delivery-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}

	@Bean
	ServletListenerRegistrationBean<HttpSessionListener> realtimeSessionListener(RealtimeBroker broker) {
		return new ServletListenerRegistrationBean<>(new RealtimeSessionListener(broker));
	}
}
