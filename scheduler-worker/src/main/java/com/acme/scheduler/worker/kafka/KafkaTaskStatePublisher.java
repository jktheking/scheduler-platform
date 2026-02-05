package com.acme.scheduler.worker.kafka;

import com.acme.scheduler.common.runtime.TaskStateEvent;
import com.acme.scheduler.meter.SchedulerMeter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Objects;

public final class KafkaTaskStatePublisher {

  private final KafkaProducer<String, byte[]> producer;
  private final String topic;
  private final ObjectMapper mapper;
  private final SchedulerMeter.Counter publishError;

  public KafkaTaskStatePublisher(KafkaProducer<String, byte[]> producer,
                                WorkerKafkaProperties props,
                                ObjectMapper mapper,
                                SchedulerMeter meter) {
    this.producer = Objects.requireNonNull(producer);
    this.topic = Objects.requireNonNull(props).getKafka().getTaskStateTopic();
    this.mapper = Objects.requireNonNull(mapper);
    this.publishError = meter.counter("scheduler.worker.state.publish.error", "Worker task state publish errors");
  }

  public void publish(TaskStateEvent evt) {
    try {
      byte[] payload = mapper.writeValueAsBytes(evt);
      ProducerRecord<String, byte[]> rec = new ProducerRecord<>(topic, Long.toString(evt.workflowInstanceId()), payload);
      producer.send(rec, (meta, ex) -> {
        if (ex != null) publishError.add(1);
      });
    } catch (Exception e) {
      publishError.add(1);
    }
  }
}
