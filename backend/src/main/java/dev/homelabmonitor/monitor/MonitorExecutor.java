package dev.homelabmonitor.monitor;

interface MonitorExecutor {

	MonitorType type();

	ExecutionResult execute(MonitorExecutionSnapshot monitor);
}
