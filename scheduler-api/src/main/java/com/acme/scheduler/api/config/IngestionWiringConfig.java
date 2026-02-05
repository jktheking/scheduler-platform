package com.acme.scheduler.api.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.acme.scheduler.adapter.inmemory.InMemoryAdmissionController;
import com.acme.scheduler.adapter.inmemory.InMemoryCommandBus;
import com.acme.scheduler.adapter.inmemory.InMemoryCommandIngestionGateway;
import com.acme.scheduler.adapter.inmemory.InMemoryCommandRepository;
import com.acme.scheduler.adapter.jdbc.JdbcCommandIngestionGateway;
import com.acme.scheduler.adapter.jdbc.JdbcCommandRepository;
import com.acme.scheduler.service.admission.SimpleCircuitBreaker;
import com.acme.scheduler.service.admission.TokenBucketAdmissionController;
import com.acme.scheduler.service.port.AdmissionController;
import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.port.CommandRepository;
import com.acme.scheduler.service.workflow.StartWorkflowUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(SchedulerIngestionProperties.class)
public class IngestionWiringConfig {

	// ---------- Common primitives ----------

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}

	@Bean
	TokenBucketAdmissionController tokenBucketAdmissionController(SchedulerIngestionProperties p) {
		var a = p.admission();
		return new TokenBucketAdmissionController(
				new TokenBucketAdmissionController.Config(a.refillTokensPerSecond(), a.capacity(), a.maxTenants()));
	}

	@Bean
	SimpleCircuitBreaker ingestionCircuitBreaker(SchedulerIngestionProperties p) {
		var cb = p.admission().circuitBreaker();
		return new SimpleCircuitBreaker(cb.failureThreshold(), Duration.ofMillis(cb.openMillis()),
				cb.halfOpenMaxProbes());
	}

	// ---------- INMEMORY (default dev) ----------

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "INMEMORY", matchIfMissing = true)
	AdmissionController inMemoryAdmission(SchedulerIngestionProperties p) {
		return new InMemoryAdmissionController(p.admission().maxInFlight());
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "INMEMORY", matchIfMissing = true)
	InMemoryCommandRepository inMemoryRepo() {
		return new InMemoryCommandRepository();
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "INMEMORY", matchIfMissing = true)
	InMemoryCommandBus inMemoryBus() {
		return new InMemoryCommandBus();
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "INMEMORY")
	CommandIngestionGateway inMemoryGateway(InMemoryCommandRepository repo, InMemoryCommandBus bus) {
		return new InMemoryCommandIngestionGateway(repo, bus);
	}

	// ---------- JDBC (low TPS) ----------

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "JDBC")
	AdmissionController jdbcAdmission(TokenBucketAdmissionController base) {
		// Low TPS: token bucket is usually enough; inFlight can be added if you want.
		return base;
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "JDBC")
	CommandRepository jdbcCommandRepository(JdbcTemplate jdbcTemplate) {
		return new JdbcCommandRepository(jdbcTemplate);
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "JDBC")
	CommandIngestionGateway jdbcGateway(CommandRepository repo) {
		return new JdbcCommandIngestionGateway(repo);
	}

	// ---------- Use case ----------

	/*
	 * @Bean StartWorkflowUseCase startWorkflowUseCase(AdmissionController
	 * admission, CommandIngestionGateway gateway) { return new
	 * StartWorkflowUseCase(admission, gateway); }
	 */

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "INMEMORY", matchIfMissing = true)
	StartWorkflowUseCase startWorkflowUseCaseInMemory(@Qualifier("inMemoryAdmission") AdmissionController admission,
			@Qualifier("inMemoryGateway") CommandIngestionGateway gateway) {
		return new StartWorkflowUseCase(admission, gateway);
	}


	@Bean
	@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "JDBC")
	StartWorkflowUseCase startWorkflowUseCaseJdbc(@Qualifier("jdbcAdmission") AdmissionController admission,
			@Qualifier("jdbcGateway") CommandIngestionGateway gateway) {
		return new StartWorkflowUseCase(admission, gateway);
	}

	
}
