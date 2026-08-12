package dev.homelabmonitor;

import org.springframework.boot.SpringApplication;

public class TestHomeLabMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.from(HomeLabMonitorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
