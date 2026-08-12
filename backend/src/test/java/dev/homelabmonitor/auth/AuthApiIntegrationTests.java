package dev.homelabmonitor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.homelabmonitor.TestcontainersConfiguration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = "homelab-monitor.scheduling.enabled=false")
class AuthApiIntegrationTests {
	private final MockMvc mockMvc;
	private final OwnerRepository owners;

	@Autowired
	AuthApiIntegrationTests(MockMvc mockMvc, OwnerRepository owners) {
		this.mockMvc = mockMvc;
		this.owners = owners;
	}

	@BeforeEach
	void resetOwner() { owners.deleteAll(); }

	@Test
	void completesSetupOnceHashesPasswordAndCreatesAuthenticatedSession() throws Exception {
		mockMvc.perform(get("/api/v1/auth/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.setupRequired").value(true))
				.andExpect(jsonPath("$.authenticated").value(false));

		MvcResult setup = mockMvc.perform(post("/api/v1/auth/setup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"Owner@Example.com","displayName":"Lab Owner","password":"correct horse battery staple"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.setupRequired").value(false))
				.andExpect(jsonPath("$.authenticated").value(true))
				.andExpect(jsonPath("$.owner.email").value("owner@example.com"))
				.andExpect(jsonPath("$.owner.displayName").value("Lab Owner"))
				.andExpect(jsonPath("$.owner.password").doesNotExist())
				.andReturn();

		Owner stored = owners.findByEmail("owner@example.com").orElseThrow();
		assertThat(stored.passwordHash()).startsWith("$2").doesNotContain("correct horse");
		MockHttpSession session = (MockHttpSession) setup.getRequest().getSession(false);
		assertThat(session).isNotNull();
		SecurityContext savedContext = (SecurityContext) session.getAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(((OwnerPrincipal) savedContext.getAuthentication().getPrincipal()).password()).isNull();
		mockMvc.perform(get("/api/v1/monitors").session(session)).andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/setup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"second@example.com","displayName":"Second","password":"another secure password"}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	void concurrentSetupCreatesExactlyOneOwner() throws Exception {
		try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
			Future<MvcResult> first = callers.submit(() -> setupRequest("first@example.com"));
			Future<MvcResult> second = callers.submit(() -> setupRequest("second@example.com"));
			List<Integer> statuses = List.of(
					first.get().getResponse().getStatus(), second.get().getResponse().getStatus()).stream().sorted().toList();
			assertThat(statuses).containsExactly(201, 409);
			assertThat(owners.count()).isEqualTo(1);
		}
	}

	@Test
	void protectsMonitorApiAndSupportsGenericLoginAndLogout() throws Exception {
		createOwner();
		mockMvc.perform(get("/api/v1/monitors"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Sign in to continue."));

		mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"owner@example.com","password":"wrong password"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Email or password is incorrect."));

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"OWNER@example.com","password":"correct horse battery staple"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authenticated").value(true))
				.andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

		mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/monitors").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void requiresCsrfForSetupAndLoginAndRejectsOversizedUtf8Password() throws Exception {
		mockMvc.perform(post("/api/v1/auth/setup").contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isForbidden());
		String password = "å".repeat(40);
		mockMvc.perform(post("/api/v1/auth/setup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"owner@example.com","displayName":"Owner","password":"%s"}
						""".formatted(password)))
				.andExpect(status().isBadRequest());

		createOwner();
		mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"owner@example.com","password":"%s"}
						""".formatted(password)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
	}

	private void createOwner() throws Exception {
		mockMvc.perform(post("/api/v1/auth/setup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"owner@example.com","displayName":"Owner","password":"correct horse battery staple"}
						"""))
				.andExpect(status().isCreated());
	}

	private MvcResult setupRequest(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/setup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","displayName":"Owner","password":"correct horse battery staple"}
						""".formatted(email)))
				.andReturn();
	}
}
