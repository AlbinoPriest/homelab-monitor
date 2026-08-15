package dev.homelabmonitor.monitor;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
class MonitorWorkerConfiguration {
	static final int SUPPORTED_MONITORS = 100;
	static final int SCHEDULED_WORKERS = 50;
	static final int SCHEDULED_QUEUE_CAPACITY = SUPPORTED_MONITORS - SCHEDULED_WORKERS;
	static final int MANUAL_WORKERS = 4;
	static final int DNS_WORKERS = SCHEDULED_WORKERS;
	static final int DNS_QUEUE_CAPACITY = MANUAL_WORKERS;

	@Bean(name = "taskScheduler")
	ThreadPoolTaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4);
		scheduler.setThreadNamePrefix("homelab-scheduler-");
		scheduler.setRemoveOnCancelPolicy(true);
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(10);
		scheduler.initialize();
		return scheduler;
	}

	@Bean(name = "monitorTaskExecutor")
	Executor monitorTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(SCHEDULED_WORKERS);
		executor.setMaxPoolSize(SCHEDULED_WORKERS);
		executor.setQueueCapacity(SCHEDULED_QUEUE_CAPACITY);
		executor.setThreadNamePrefix("monitor-check-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(65);
		executor.initialize();
		return executor;
	}

	@Bean(name = "monitorDnsExecutor")
	Executor monitorDnsExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(DNS_WORKERS);
		executor.setMaxPoolSize(DNS_WORKERS);
		executor.setQueueCapacity(DNS_QUEUE_CAPACITY);
		executor.setThreadNamePrefix("monitor-dns-");
		executor.setDaemon(true);
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}

	@Bean(name = "manualMonitorTaskExecutor")
	Executor manualMonitorTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(MANUAL_WORKERS);
		executor.setMaxPoolSize(MANUAL_WORKERS);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("manual-monitor-check-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(35);
		executor.initialize();
		return executor;
	}
}
