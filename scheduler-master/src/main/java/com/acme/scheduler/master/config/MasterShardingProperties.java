package com.acme.scheduler.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Master sharding + shard leadership configuration.
 */
@ConfigurationProperties(prefix = "scheduler.master.sharding")
public class MasterShardingProperties {

  /**
   * Enable shard leadership. When disabled, the existing global leader fence remains in effect.
   *
   * <p>Default: false (safest for single-master / local dev).
   */
  private boolean enabled = false;

  /** Number of shards. Default: 1 (demo). */
  private int shards = 1;

  /** Logical node id used as election value (for debugging). */
  private String nodeId = "";

  /** Base path for shard elections in etcd. Default: /scheduler/master/shards */
  private String electionBasePath = "/scheduler/master/shards";

  /** Lease TTL for shard leadership. Default: 10s. */
  private Duration leaseTtl = Duration.ofSeconds(10);

  /** ETCD endpoints for shard leadership, e.g. http://etcd:2379. */
  private List<String> etcdEndpoints = new ArrayList<>();

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }

  public int getShards() { return shards; }
  public void setShards(int shards) { this.shards = shards; }

  public String getNodeId() { return nodeId; }
  public void setNodeId(String nodeId) { this.nodeId = nodeId; }

  public String getElectionBasePath() { return electionBasePath; }
  public void setElectionBasePath(String electionBasePath) { this.electionBasePath = electionBasePath; }

  public Duration getLeaseTtl() { return leaseTtl; }
  public void setLeaseTtl(Duration leaseTtl) { this.leaseTtl = leaseTtl; }

  public List<String> getEtcdEndpoints() { return etcdEndpoints; }
  public void setEtcdEndpoints(List<String> etcdEndpoints) { this.etcdEndpoints = etcdEndpoints; }
}
