package dev.homelabmonitor.monitor;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
class MonitorWorkerConfiguration {

	@Bean(name = "monitorTaskExecutor")
	Executor monitorTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(8);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(92);
		executor.setThreadNamePrefix("monitor-check-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(35);
		executor.initialize();
		return executor;
	}

	@Bean(name = "monitorDnsExecutor")
	Executor monitorDnsExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("monitor-dns-");
		executor.setDaemon(true);
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}

	@Bean(name = "manualMonitorTaskExecutor")
	Executor manualMonitorTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("manual-monitor-check-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(35);
		executor.initialize();
		return executor;
	}
}
