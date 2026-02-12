package com.acme.scheduler.master.ha;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acme.scheduler.master.config.MasterShardingProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ETCD-based per-shard leader election using leased keys.
 */
public final class EtcdShardLeaderElector implements ShardLeaderElector {

	private static final Logger log = LoggerFactory.getLogger(EtcdShardLeaderElector.class);

	private final List<String> endpoints;
	private final String basePath;
	private final String nodeId;
	private final int shards;
	private final Duration leaseTtl;

	private final CopyOnWriteArrayList<Consumer<Integer>> listeners = new CopyOnWriteArrayList<>();
	private final ExecutorService bg = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r);
		t.setDaemon(true);
		t.setName("etcd-shard-elector");
		return t;
	});

	private volatile Client client;
	private volatile KV kv;
	private volatile Lease lease;

	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean[] leader;
	private final AtomicLong[] epoch;
	private final AtomicLong[] leaseId;

	public EtcdShardLeaderElector(MasterShardingProperties props, int shardCountOverride) {
		this(props.getEtcdEndpoints(), props.getElectionBasePath(), props.getNodeId(), props.getShards(),
				props.getLeaseTtl());
	}

	public EtcdShardLeaderElector(List<String> endpoints, String basePath, String nodeId, int shards,
			Duration leaseTtl) {
		this.endpoints = Objects.requireNonNull(endpoints);
		this.basePath = Objects.requireNonNull(basePath);
		this.nodeId = nodeId == null ? "" : nodeId;
		this.shards = shards;
		this.leaseTtl = leaseTtl == null ? Duration.ofSeconds(10) : leaseTtl;
		this.leader = new AtomicBoolean[shards];
		this.epoch = new AtomicLong[shards];
		this.leaseId = new AtomicLong[shards];
		for (int i = 0; i < shards; i++) {
			leader[i] = new AtomicBoolean(false);
			epoch[i] = new AtomicLong(0);
			leaseId[i] = new AtomicLong(0);
		}
	}

	@Override
	public void start() {
		if (!running.compareAndSet(false, true))
			return;
		client = Client.builder().endpoints(endpoints.toArray(new String[0])).build();
		kv = client.getKVClient();
		lease = client.getLeaseClient();
		for (int shardId = 0; shardId < shards; shardId++) {
			int sid = shardId;
			bg.submit(() -> campaignLoop(sid));
		}
		log.info("EtcdShardLeaderElector started (shards={}, basePath={}).", shards, basePath);
	}

	@Override
	public void stop() {
		if (!running.compareAndSet(true, false))
			return;
		bg.shutdownNow();
		try {
			bg.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
		// best-effort revoke leases
		for (int i = 0; i < shards; i++) {
			try {
				long id = leaseId[i].getAndSet(0);
				if (id != 0 && lease != null)
					lease.revoke(id).get(1, TimeUnit.SECONDS);
			} catch (Exception ignore) {
			}
			setLeader(i, false);
		}
		try {
			if (client != null)
				client.close();
		} catch (Exception ignore) {
		}
		client = null;
		kv = null;
		lease = null;
	}

	@Override
	public boolean isLeader(int shardId) {
		if (shardId < 0 || shardId >= shards)
			return false;
		return leader[shardId].get();
	}

	@Override
	public Set<Integer> leaderShards() {
		LinkedHashSet<Integer> out = new LinkedHashSet<>();
		for (int i = 0; i < shards; i++)
			if (leader[i].get())
				out.add(i);
		return out.isEmpty() ? Collections.emptySet() : out;
	}

	@Override
	public OptionalLong leaderEpoch(int shardId) {
		if (shardId < 0 || shardId >= shards)
			return OptionalLong.empty();
		return OptionalLong.of(epoch[shardId].get());
	}

	@Override
	public void addListener(Consumer<Integer> onAnyShardChange) {
		listeners.add(Objects.requireNonNull(onAnyShardChange));
	}

	private void campaignLoop(int shardId) {
		String key = basePath + "/" + shardId + "/leader";
		ByteSequence bsKey = bs(key);
		ByteSequence bsVal = bs(safeNodeId(shardId));

		long backoffMs = 200;
		while (running.get()) {
			long id = 0;
			try {
				id = lease.grant(Math.max(1, (int) leaseTtl.toSeconds())).get(5, TimeUnit.SECONDS).getID();
				leaseId[shardId].set(id);

				var txn = kv.txn().If(new Cmp(bsKey, Cmp.Op.EQUAL, CmpTarget.version(0)))
						.Then(Op.put(bsKey, bsVal, PutOption.newBuilder().withLeaseId(id).build()))
						.Else(Op.get(bsKey, GetOption.DEFAULT));

				var resp = txn.commit().get(5, TimeUnit.SECONDS);
				if (!resp.isSucceeded()) {
					// not leader
					setLeader(shardId, false);
					lease.revoke(id).get(2, TimeUnit.SECONDS);
					leaseId[shardId].set(0);
					sleep(backoffMs);
					backoffMs = Math.min(backoffMs * 2, 2_000);
					continue;
				}

				// leader
				epoch[shardId].incrementAndGet();
				setLeader(shardId, true);
				backoffMs = 200;
				log.info("checkpoint=master.shard_leader_acquired shardId={} key={} leaseId={} nodeId={}", shardId, key,
						id, safeNodeId(shardId));

				AtomicBoolean keepAliveOk = new AtomicBoolean(true);
				lease.keepAlive(id, new StreamObserver<LeaseKeepAliveResponse>() {
					@Override
					public void onNext(LeaseKeepAliveResponse value) {
					}

					@Override
					public void onError(Throwable t) {
						keepAliveOk.set(false);
					}

					@Override
					public void onCompleted() {
						keepAliveOk.set(false);
					}
				});

				while (running.get() && keepAliveOk.get()) {
					Thread.sleep(1_000);
				}

			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				log.warn("checkpoint=master.shard_election_error shardId={} error={}", shardId, e.toString());
			} finally {
				setLeader(shardId, false);
				try {
					long lid = leaseId[shardId].getAndSet(0);
					if (lid != 0 && lease != null)
						lease.revoke(lid).get(2, TimeUnit.SECONDS);
				} catch (Exception ignore) {
				}
			}
		}
	}

	private void setLeader(int shardId, boolean isLeader) {
		boolean prev = leader[shardId].getAndSet(isLeader);
		if (prev != isLeader) {
			for (Consumer<Integer> c : listeners) {
				try {
					c.accept(shardId);
				} catch (Exception ignore) {
				}
			}
		}
	}

	private String safeNodeId(int shardId) {
		if (nodeId != null && !nodeId.isBlank())
			return nodeId;
		return "master-" + Integer.toHexString(System.identityHashCode(this)) + "-s" + shardId;
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private static ByteSequence bs(String s) {
		return ByteSequence.from(Objects.requireNonNull(s), StandardCharsets.UTF_8);
	}
}
