package com.acme.scheduler.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduler.master")
public class MasterKafkaProperties {

  /** MASTER processes commands -> instances/plans/triggers and publishes ready events. WRITER persists commands to DB. */
  private Role role = Role.MASTER;

  private CommandDriver commandDriver = CommandDriver.KAFKA;

  private final Kafka kafka = new Kafka();
  private final Trigger trigger = new Trigger();

  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }

  public CommandDriver getCommandDriver() { return commandDriver; }
  public void setCommandDriver(CommandDriver commandDriver) { this.commandDriver = commandDriver; }

  public Kafka getKafka() { return kafka; }
  public Trigger getTrigger() { return trigger; }

  public enum Role { MASTER, WRITER }

  public enum CommandDriver { KAFKA, DBPOLL }

  public static class Kafka {
    private String bootstrapServers = "localhost:9092";
    private String commandsTopic = "scheduler.commands.v1";
    private String readyTopic = "scheduler.tasks.ready.v1";
    private String masterGroupId = "scheduler-master";
    private String writerGroupId = "scheduler-command-writer";
    private String clientId = "scheduler-master";

    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }

    public String getCommandsTopic() { return commandsTopic; }
    public void setCommandsTopic(String commandsTopic) { this.commandsTopic = commandsTopic; }

    public String getReadyTopic() { return readyTopic; }
    public void setReadyTopic(String readyTopic) { this.readyTopic = readyTopic; }

    public String getMasterGroupId() { return masterGroupId; }
    public void setMasterGroupId(String masterGroupId) { this.masterGroupId = masterGroupId; }

    public String getWriterGroupId() { return writerGroupId; }
    public void setWriterGroupId(String writerGroupId) { this.writerGroupId = writerGroupId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
  }

  public static class Trigger {
    private Engine engine = Engine.TIMEWHEEL;

    /** Common drain batch limit for due triggers. */
    private int drainBatch = 500;

    /** Quartz-like poller interval. */
    private long quartzPollMs = 200;

    /** TimeWheel parameters */
    private int wheelShards = 8;
    private long wheelTickMs = 100;
    private int wheelSlots = 512;
    private long wheelLookaheadMs = 30000; // preload next 30s of triggers

    public Engine getEngine() { return engine; }
    public void setEngine(Engine engine) { this.engine = engine; }

    public int getDrainBatch() { return drainBatch; }
    public void setDrainBatch(int drainBatch) { this.drainBatch = drainBatch; }

    public long getQuartzPollMs() { return quartzPollMs; }
    public void setQuartzPollMs(long quartzPollMs) { this.quartzPollMs = quartzPollMs; }

    public int getWheelShards() { return wheelShards; }
    public void setWheelShards(int wheelShards) { this.wheelShards = wheelShards; }

    public long getWheelTickMs() { return wheelTickMs; }
    public void setWheelTickMs(long wheelTickMs) { this.wheelTickMs = wheelTickMs; }

    public int getWheelSlots() { return wheelSlots; }
    public void setWheelSlots(int wheelSlots) { this.wheelSlots = wheelSlots; }

    public long getWheelLookaheadMs() { return wheelLookaheadMs; }
    public void setWheelLookaheadMs(long wheelLookaheadMs) { this.wheelLookaheadMs = wheelLookaheadMs; }

    public enum Engine { TIMEWHEEL, QUARTZ }
  }
}
