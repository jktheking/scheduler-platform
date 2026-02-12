package com.acme.scheduler.master.config;

import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.acme.scheduler.domain.alert.AlertPublisher;
import com.acme.scheduler.master.adapter.jdbc.JdbcCommandDedupeRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcCommandWriter;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagEdgeRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcTriggerRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcWorkflowDefinitionRepository;
import com.acme.scheduler.master.ha.EtcdShardLeaderElector;
import com.acme.scheduler.master.ha.FixedShardLeaderElector;
import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.kafka.KafkaAlertPublisher;
import com.acme.scheduler.master.kafka.KafkaClientFactory;
import com.acme.scheduler.master.kafka.KafkaCommandWriterLoop;
import com.acme.scheduler.master.kafka.KafkaMasterCommandConsumerLoop;
import com.acme.scheduler.master.kafka.KafkaReadyPublisher;
import com.acme.scheduler.master.kafka.KafkaTaskStateConsumerLoop;
import com.acme.scheduler.master.kafka.ShardKeyPartitioner;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.DlqReplayer;
import com.acme.scheduler.master.port.WorkflowScheduler;
import com.acme.scheduler.master.runtime.DagProgressionEngine;
import com.acme.scheduler.master.runtime.DagRuntime;
import com.acme.scheduler.master.runtime.DefaultDagRuntime;
import com.acme.scheduler.master.runtime.JdbcTemplateWorkflowInstanceStatus;
import com.acme.scheduler.master.runtime.RetryPlanner;
import com.acme.scheduler.master.runtime.TaskEnqueueReconciler;
import com.acme.scheduler.master.runtime.TaskTimeoutReconciler;
import com.acme.scheduler.master.runtime.WorkflowMaterializer;
import com.acme.scheduler.master.sharding.ShardCalculator;
import com.acme.scheduler.master.trigger.QuartzTriggerEngine;
import com.acme.scheduler.master.trigger.TimeWheelTriggerEngine;
import com.acme.scheduler.master.trigger.TriggerEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({ MasterKafkaProperties.class,MasterShardingProperties.class,
		TaskTimeoutReconcilerProperties.class, TaskEnqueueReconcilerProperties.class })
public class MasterKafkaWiringConfig {
		


	@Bean
	@ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
	public ShardCalculator shardCalculator(MasterShardingProperties sharding) {
		return new ShardCalculator(sharding.getShards());
	}

	@Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public ShardLeaderElector shardLeaderElector(MasterShardingProperties sharding) {
    // If sharding is disabled but HA is enabled, we still need a single active master.
    // In that case, we elect leadership for a single "global" shard (0) and gate the non-sharded loops on it.
    int shardCountForElection = sharding.getShards();

    if (sharding.isEnabled()) {
      return new EtcdShardLeaderElector(sharding, shardCountForElection);
    }
	    // Non-HA: this node is leader for all shards.
	    Set<Integer> shards = IntStream.range(0, sharding.getShards()).boxed().collect(Collectors.toSet());
	    return new FixedShardLeaderElector(shards);
  }

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
	public SmartLifecycle shardLeaderElectorLifecycle(ShardLeaderElector elector) {
		return new SmartLifecycle() {
			private volatile boolean running = false;

			@Override
			public void start() {
				elector.start();
				running = true;
			}

			@Override
			public void stop() {
				elector.stop();
				running = false;
			}

			@Override
			public boolean isRunning() {
				return running;
			}

			@Override
			public int getPhase() {
				return -10;
			}
		};
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}

	private static Properties shardedProducerProps(MasterKafkaProperties props, MasterShardingProperties sharding) {
		Properties p = KafkaClientFactory.producerProps(props);
		if (sharding.isEnabled()) {
			p.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, ShardKeyPartitioner.class.getName());
			p.put(ShardKeyPartitioner.CONF_SHARDS, Integer.toString(Math.max(1, sharding.getShards())));
		}
		return p;
	}

	@Bean
	public KafkaProducer<String, byte[]> readyProducer(MasterKafkaProperties props, MasterShardingProperties sharding) {
		return new KafkaProducer<>(shardedProducerProps(props, sharding));
	}

	@Bean
	public KafkaProducer<String, byte[]> alertsProducer(MasterKafkaProperties props,
			MasterShardingProperties sharding) {
		return new KafkaProducer<>(shardedProducerProps(props, sharding));
	}

	@Bean
	public KafkaReadyPublisher kafkaReadyPublisher(KafkaProducer<String, byte[]> readyProducer,
			MasterKafkaProperties props, ObjectMapper mapper, MasterMetrics metrics, ShardLeaderElector shardElector,
			ShardCalculator shardCalculator,
			MasterShardingProperties sharding) {
		return new KafkaReadyPublisher(readyProducer, props, mapper, metrics, shardElector, shardCalculator, sharding.isEnabled());
	}

	@Bean
	public AlertPublisher alertPublisher(KafkaProducer<String, byte[]> alertsProducer, MasterKafkaProperties props,
			ObjectMapper mapper, MasterMetrics metrics, ShardLeaderElector shardElector,
			ShardCalculator shardCalculator,
			MasterShardingProperties sharding) {
		return new KafkaAlertPublisher(alertsProducer, props, mapper, metrics, shardElector, shardCalculator, sharding.isEnabled());
	}

	@Bean
	public JdbcTriggerRepository jdbcTriggerRepository(JdbcTemplate jdbc, TransactionTemplate tx,
			ShardCalculator shardCalculator) {
		return new JdbcTriggerRepository(jdbc, tx, shardCalculator);
	}

	@Bean
	public JdbcDagTaskInstanceRepository jdbcDagTaskInstanceRepository(JdbcTemplate jdbc,
			ShardCalculator shardCalculator) {
		return new JdbcDagTaskInstanceRepository(jdbc, shardCalculator);
	}

	@Bean
	public JdbcWorkflowDefinitionRepository jdbcWorkflowDefinitionRepository(JdbcTemplate jdbc) {
		return new JdbcWorkflowDefinitionRepository(jdbc);
	}

	@Bean
	public JdbcDagEdgeRepository jdbcDagEdgeRepository(JdbcTemplate jdbc) {
		return new JdbcDagEdgeRepository(jdbc);
	}

	@Bean
	public JdbcTemplateWorkflowInstanceStatus jdbcTemplateWorkflowInstanceStatus(JdbcTemplate jdbc) {
		return new JdbcTemplateWorkflowInstanceStatus(jdbc);
	}

	@Bean
	public WorkflowMaterializer workflowMaterializer(JdbcWorkflowDefinitionRepository defs,
			JdbcDagTaskInstanceRepository tasks, ObjectMapper mapper) {
		return new WorkflowMaterializer(defs, tasks, mapper);
	}

	@Bean
	public DagProgressionEngine dagProgressionEngine(JdbcDagEdgeRepository edges, JdbcDagTaskInstanceRepository tasks,
			JdbcTemplateWorkflowInstanceStatus wi, KafkaReadyPublisher publisher, ObjectMapper mapper,
			MasterMetrics metrics, MasterShardingProperties sharding) {
		return new DagProgressionEngine(edges, tasks, wi, publisher, mapper, metrics);
	}

	@Bean
	public RetryPlanner retryPlanner(JdbcDagTaskInstanceRepository tasks, JdbcTriggerRepository triggers,
			JdbcTemplateWorkflowInstanceStatus wi, AlertPublisher alertPublisher, MasterMetrics metrics, MasterShardingProperties sharding) {
		return new RetryPlanner(tasks, triggers, wi, alertPublisher, metrics);
	}

	@Bean
	public DagRuntime dagRuntime(WorkflowMaterializer materializer, JdbcDagTaskInstanceRepository tasks,
			JdbcTemplateWorkflowInstanceStatus wi, JdbcTriggerRepository triggers, KafkaReadyPublisher publisher,
			DagProgressionEngine progression, RetryPlanner retryPlanner, AlertPublisher alertPublisher,
			ShardLeaderElector shardLeaderElector, ShardCalculator shardCalculator, ObjectMapper mapper,
			MasterMetrics metrics, MasterShardingProperties sharding) {
		return new DefaultDagRuntime(materializer, tasks, wi, triggers, publisher, progression, retryPlanner,
				alertPublisher, shardLeaderElector, shardCalculator, mapper, metrics, sharding.isEnabled());
	}

	@Bean
	public TriggerEngine triggerEngine(MasterKafkaProperties props, JdbcTriggerRepository repo, DagRuntime dagRuntime,
			MasterMetrics metrics, ShardLeaderElector shardElector, MasterShardingProperties sharding) {
		var t = props.getTrigger();
		if (t.getEngine() == MasterKafkaProperties.Trigger.Engine.QUARTZ) {
			return new QuartzTriggerEngine(repo, dagRuntime, metrics, shardElector, sharding.isEnabled(),
					t.getQuartzPollMs(), t.getDrainBatch());
		}
		return new TimeWheelTriggerEngine(repo, dagRuntime, metrics, shardElector, sharding.isEnabled(),
				t.getWheelShards(), t.getWheelTickMs(), t.getWheelSlots(), t.getWheelLookaheadMs(), t.getDrainBatch());
	}

	@Bean
	public SmartLifecycle triggerEngineLifecycle(TriggerEngine engine) {
		return new SmartLifecycle() {
			private volatile boolean running = false;

			@Override
			public void start() {
				engine.start();
				running = true;
			}

			@Override
			public void stop() {
				engine.stop();
				running = false;
			}

			@Override
			public boolean isRunning() {
				return running;
			}

			@Override
			public int getPhase() {
				return 0;
			}
		};
	}

	@Bean
	public TaskTimeoutReconciler taskTimeoutReconciler(JdbcDagTaskInstanceRepository tasks, AlertPublisher alerts,
			ShardLeaderElector shardLeaderElector, TaskTimeoutReconcilerProperties props, MasterMetrics metrics, MasterShardingProperties sharding) {
		return new TaskTimeoutReconciler(tasks, alerts, shardLeaderElector, metrics, props.getPollMs(),
				props.getBatch());
	}

	@Bean
	public SmartLifecycle taskTimeoutReconcilerLifecycle(TaskTimeoutReconciler r) {
		return new SmartLifecycle() {
			private volatile boolean running = false;

			@Override
			public void start() {
				r.start();
				running = true;
			}

			@Override
			public void stop() {
				r.stop();
				running = false;
			}

			@Override
			public boolean isRunning() {
				return running;
			}

			@Override
			public int getPhase() {
				return 0;
			}
		};
	}

	@Bean
	public TaskEnqueueReconciler taskEnqueueReconciler(JdbcTemplate jdbc, ShardLeaderElector shardLeaderElector,
			KafkaReadyPublisher publisher, ObjectMapper mapper, MasterMetrics metrics,
			TaskEnqueueReconcilerProperties props) {
		return new TaskEnqueueReconciler(jdbc, shardLeaderElector, publisher, mapper, metrics,
				props.getReenqueueAfterMs(), props.getIntervalMs(), props.getBatch());
	}

	@Bean
	public SmartLifecycle taskEnqueueReconcilerLifecycle(TaskEnqueueReconciler r) {
		return new SmartLifecycle() {
			private volatile boolean running = false;

			@Override
			public void start() {
				r.start();
				running = true;
			}

			@Override
			public void stop() {
				r.stop();
				running = false;
			}

			@Override
			public boolean isRunning() {
				return running;
			}

			@Override
			public int getPhase() {
				return 0;
			}
		};
	}

	@Bean
	public KafkaConsumer<String, byte[]> taskStateConsumer(MasterKafkaProperties props) {
		return new KafkaConsumer<>(
				KafkaClientFactory.consumerProps(props, props.getKafka().getMasterGroupId() + "-taskstate"));
	}

	@Bean
	public KafkaTaskStateConsumerLoop kafkaTaskStateConsumerLoop(MasterKafkaProperties props,
			KafkaConsumer<String, byte[]> taskStateConsumer, DagRuntime dagRuntime,
			ShardLeaderElector shardLeaderElector, ShardCalculator shardCalculator, MasterShardingProperties sharding,
			ObjectMapper mapper, MasterMetrics metrics) {
		return new KafkaTaskStateConsumerLoop(props, taskStateConsumer, dagRuntime, shardLeaderElector, shardCalculator,
				sharding.isEnabled(), mapper, metrics);
	}

	@Bean
	public SmartLifecycle taskStateConsumerLifecycle(KafkaTaskStateConsumerLoop loop) {
		return new SmartLifecycle() {
			private volatile boolean running = false;

			@Override
			public void start() {
				loop.start();
				running = true;
			}

			@Override
			public void stop() {
				loop.stop();
				running = false;
			}

			@Override
			public boolean isRunning() {
				return running;
			}

			@Override
			public int getPhase() {
				return 0;
			}
		};
	}

	@Bean
	public KafkaConsumer<String, byte[]> masterConsumer(MasterKafkaProperties props) {
		return new KafkaConsumer<>(KafkaClientFactory.consumerProps(props, props.getKafka().getMasterGroupId()));
	}

	@Bean
	public JdbcCommandDedupeRepository jdbcCommandDedupeRepository(JdbcTemplate jdbc) {
		return new JdbcCommandDedupeRepository(jdbc);
	}

	@Bean
	public KafkaMasterCommandConsumerLoop kafkaMasterCommandConsumerLoop(MasterKafkaProperties props,
			@Qualifier("masterConsumer") KafkaConsumer<String, byte[]> masterConsumer,
			JdbcCommandDedupeRepository dedupe, WorkflowScheduler workflowScheduler, DlqReplayer dlqReplayer,
			ShardLeaderElector shardLeaderElector, ShardCalculator shardCalculator,
			MasterShardingProperties sharding, ObjectMapper mapper, MasterMetrics metrics) {
		return new KafkaMasterCommandConsumerLoop(props, masterConsumer, dedupe, workflowScheduler, dlqReplayer,
				shardLeaderElector, shardCalculator, sharding.isEnabled(), mapper, metrics);
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
	public SmartLifecycle masterCommandConsumerLifecycle(KafkaMasterCommandConsumerLoop loop) {
		return new SmartLifecycle() {
			private volatile boolean running = false;
			@Override public void start() { loop.start(); running = true; }
			@Override public void stop() { loop.stop(); running = false; }
			@Override public boolean isRunning() { return running; }
			@Override public int getPhase() { return 0; }
		};
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "WRITER")
	public JdbcCommandWriter jdbcCommandWriter(JdbcTemplate jdbc) {
		return new JdbcCommandWriter(jdbc);
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "WRITER")
	public KafkaConsumer<String, byte[]> writerConsumer(MasterKafkaProperties props) {
		return new KafkaConsumer<>(KafkaClientFactory.consumerProps(props, props.getKafka().getWriterGroupId()));
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "WRITER")
	public KafkaCommandWriterLoop kafkaCommandWriterLoop(MasterKafkaProperties props,
			@Qualifier("writerConsumer") KafkaConsumer<String, byte[]> consumer, JdbcCommandWriter writer,
			ObjectMapper mapper, MasterMetrics metrics, MasterShardingProperties sharding) {
		return new KafkaCommandWriterLoop(props, consumer, writer, mapper, metrics);
	}

	@Bean
	@ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "WRITER")
	public SmartLifecycle commandWriterLoopLifecycle(KafkaCommandWriterLoop loop) {
		return new SmartLifecycle() {
			private volatile boolean running = false;

			@Override
			public void start() {
				loop.start();
				running = true;
			}

			@Override
			public void stop() {
				loop.stop();
				running = false;
			}

			@Override
			public boolean isRunning() {
				return running;
			}

			@Override
			public int getPhase() {
				return 0;
			}
		};
	}
}
