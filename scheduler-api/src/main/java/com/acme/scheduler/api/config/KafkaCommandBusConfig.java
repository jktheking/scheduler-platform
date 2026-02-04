package com.acme.scheduler.api.config;

import com.acme.scheduler.adapter.kafka.KafkaCommandBus;
import com.acme.scheduler.adapter.kafka.KafkaPressureSamplerImpl;
import com.acme.scheduler.adapter.kafka.KafkaWalCommandIngestionGateway;
import com.acme.scheduler.service.admission.InflightLimiter;
import com.acme.scheduler.service.admission.KafkaAwareAdmissionController;
import com.acme.scheduler.service.admission.SimpleCircuitBreaker;
import com.acme.scheduler.service.admission.TokenBucketAdmissionController;
import com.acme.scheduler.service.port.AdmissionController;
import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.port.KafkaPressureSampler;
import com.acme.scheduler.service.workflow.StartWorkflowUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka ingestion wiring (WAL) for extreme schedule create throughput.
 *
 * This is intentionally isolated so we can evolve Kafka knobs without touching
 * other modes.
 */
@Configuration
@ConditionalOnProperty(prefix = "scheduler.ingestion", name = "mode", havingValue = "KAFKA")
public class KafkaCommandBusConfig {

	@Bean
	KafkaCommandBus kafkaCommandBus(SchedulerIngestionProperties p, ObjectMapper mapper, SimpleCircuitBreaker breaker) {
		var k = p.kafka();
		return new KafkaCommandBus(k.bootstrapServers(), k.commandsTopic(), k.clientId(), mapper, breaker);
	}

	@Bean
	KafkaPressureSampler kafkaPressureSampler(KafkaCommandBus bus) {
		return new KafkaPressureSamplerImpl(bus);
	}

	@Bean
	AdmissionController kafkaAwareAdmission(TokenBucketAdmissionController base, SchedulerIngestionProperties p,
			KafkaPressureSampler sampler, SimpleCircuitBreaker breaker) {
		var a = p.admission();
		return new KafkaAwareAdmissionController(base, new InflightLimiter(a.maxInFlight()), sampler, breaker,
				a.kafkaRejectAbovePressure());
	}

	@Bean
	CommandIngestionGateway kafkaWalGateway(KafkaCommandBus bus) {
		return new KafkaWalCommandIngestionGateway(bus);
	}

	@Bean
	StartWorkflowUseCase startWorkflowUseCaseKafka(@Qualifier("kafkaAwareAdmission") AdmissionController admission,
			@Qualifier("kafkaWalGateway") CommandIngestionGateway gateway) {
		return new StartWorkflowUseCase(admission, gateway);
	}
}
