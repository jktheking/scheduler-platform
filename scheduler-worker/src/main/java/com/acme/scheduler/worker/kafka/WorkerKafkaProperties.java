package com.acme.scheduler.worker.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduler.worker")
public class WorkerKafkaProperties {

  private final Kafka kafka = new Kafka();
  private String workerId = "worker-1";

  public Kafka getKafka() { return kafka; }

  public String getWorkerId() { return workerId; }
  public void setWorkerId(String workerId) { this.workerId = workerId; }

  public static class Kafka {
    private String bootstrapServers = "localhost:9092";
    private String readyTopic = "scheduler.tasks.ready.v1";
    private String taskStateTopic = "scheduler.task.state.v1";
    private String groupId = "scheduler-worker";
    private String clientId = "scheduler-worker";

    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }

    public String getReadyTopic() { return readyTopic; }
    public void setReadyTopic(String readyTopic) { this.readyTopic = readyTopic; }

    public String getTaskStateTopic() { return taskStateTopic; }
    public void setTaskStateTopic(String taskStateTopic) { this.taskStateTopic = taskStateTopic; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
  }
}
