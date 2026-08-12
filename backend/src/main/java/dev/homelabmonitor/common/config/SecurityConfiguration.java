package dev.homelabmonitor.common.config;

import dev.homelabmonitor.auth.OwnerService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain applicationSecurity(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health", "/api/v1/csrf", "/api/v1/auth/status",
						"/api/v1/auth/setup", "/api/v1/auth/login").permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().permitAll());
		http.exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/problem+json");
			objectMapper.writeValue(response.getOutputStream(), Map.of(
					"type", "about:blank", "title", "Authentication required",
					"status", 401, "detail", "Sign in to continue."));
		}));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	UserDetailsService ownerUserDetailsService(OwnerService ownerService) {
		return ownerService::load;
	}
}
