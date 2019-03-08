/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

/**
 * Rule that use the average/percentile response times
 * to assign dynamic "weights" per Server which is then used in
 * the "Weighted Round Robin" fashion.
 * <p>
 * The basic idea for weighted round robin has been obtained from JCS
 * The implementation for choosing the endpoint from the list of endpoints
 * is as follows:Let's assume 4 endpoints:A(wt=10), B(wt=30), C(wt=40),
 * D(wt=20).
 * <p>
 * Using the Random API, generate a random number between 1 and10+30+40+20.
 * Let's assume that the above list is randomized. Based on the weights, we
 * have intervals as follows:
 * <p>
 * 1-----10 (A's weight)
 * <br>
 * 11----40 (A's weight + B's weight)
 * <br>
 * 41----80 (A's weight + B's weight + C's weight)
 * <br>
 * 81----100(A's weight + B's weight + C's weight + C's weight)
 * <p>
 * Here's the psuedo code for deciding where to send the request:
 * <p>
 * if (random_number between 1 &amp; 10) {send request to A;}
 * <br>
 * else if (random_number between 11 &amp; 40) {send request to B;}
 * <br>
 * else if (random_number between 41 &amp; 80) {send request to C;}
 * <br>
 * else if (random_number between 81 &amp; 100) {send request to D;}
 * <p>
 * When there is not enough statistics gathered for the servers, this rule
 * will fall back to use {@link RoundRobinRule}.
 * @author stonse
 * @see WeightedResponseTimeRule
 * @deprecated Use {@link WeightedResponseTimeRule}
 */
public class ResponseTimeWeightedRule extends RoundRobinRule {

	public static final IClientConfigKey<Integer> WEIGHT_TASK_TIMER_INTERVAL_CONFIG_KEY = WeightedResponseTimeRule.WEIGHT_TASK_TIMER_INTERVAL_CONFIG_KEY;

	public static final int DEFAULT_TIMER_INTERVAL = 30 * 1000;

	private int serverWeightTaskTimerInterval = DEFAULT_TIMER_INTERVAL;

	private static final Logger logger = LoggerFactory.getLogger(ResponseTimeWeightedRule.class);

	// 存储了计算好的权重，比如索引2的位置存储的值是从0到2所有服务节点的权重之和
	private volatile List<Double> accumulatedWeights = new ArrayList<Double>();


	private final Random random = new Random();

	protected Timer serverWeightTimer = null;

	protected AtomicBoolean serverWeightAssignmentInProgress = new AtomicBoolean(false);

	String name = "unknown";

	public ResponseTimeWeightedRule() {
		super();
	}

	public ResponseTimeWeightedRule(ILoadBalancer lb) {
		super(lb);
	}

	@Override
	public void setLoadBalancer(ILoadBalancer lb) {
		super.setLoadBalancer(lb);
		if (lb instanceof BaseLoadBalancer) {
			name = ((BaseLoadBalancer) lb).getName();
		}
		initialize(lb);
	}

	void initialize(ILoadBalancer lb) {
		if (serverWeightTimer != null) {
			serverWeightTimer.cancel();
		}
		serverWeightTimer = new Timer("NFLoadBalancer-serverWeightTimer-"
				+ name, true);
		serverWeightTimer.schedule(new DynamicServerWeightTask(), 0,
				serverWeightTaskTimerInterval);
		// do a initial run
		ServerWeight sw = new ServerWeight();
		sw.maintainWeights();

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				logger.info("Stopping NFLoadBalancer-serverWeightTimer-{}", name);
				serverWeightTimer.cancel();
			}
		}));
	}

	public void shutdown() {
		if (serverWeightTimer != null) {
			logger.info("Stopping NFLoadBalancer-serverWeightTimer-{}", name);
			serverWeightTimer.cancel();
		}
	}

	@edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE")
	@Override
	public Server choose(ILoadBalancer lb, Object key) {
		if (lb == null) {
			return null;
		}
		Server server = null;

		while (server == null) {
			// 因为是并发的，所以在找到合适的服务节点后，当前线程就可以退出寻找了
			List<Double> currentWeights = accumulatedWeights;
			if (Thread.interrupted()) {
				return null;
			}
			List<Server> allList = lb.getAllServers();

			int serverCount = allList.size();

			if (serverCount == 0) {
				return null;
			}

			int serverIndex = 0;

			// 由于刚才计算权重时是叠加的，所以列表中最后一个值是前面所有值+它本身的值
			double maxTotalWeight = currentWeights.size() == 0 ? 0 : currentWeights.get(currentWeights.size() - 1);
			// 如果所有服务器均没有命中，直接使用RoundRobin规则
			if (maxTotalWeight < 0.001d) {
				server = super.choose(getLoadBalancer(), key);
			} else {
				// 设定一个0到maxTotalWeight之间的一个随机值
				double randomWeight = random.nextDouble() * maxTotalWeight;
				int n = 0;
				// 取一个值
				for (Double d : currentWeights) {
					if (d >= randomWeight) {
						serverIndex = n;
						break;
					} else {
						n++;
					}
				}

				server = allList.get(serverIndex);
			}
			// 判断服务实例是否存在
			if (server == null) {
				Thread.yield();
				continue;
			}
			// 判断服务实例是否存活
			if (server.isAlive()) {
				return (server);
			}
			server = null;
		}
		return server;
	}

	class DynamicServerWeightTask extends TimerTask {
		public void run() {
			ServerWeight serverWeight = new ServerWeight();
			try {
				serverWeight.maintainWeights();
			} catch (Exception e) {
				logger.error("Error running DynamicServerWeightTask for {}", name, e);
			}
		}
	}

	class ServerWeight {

		/**
		 * 存储权重
		 */
		public void maintainWeights() {
			ILoadBalancer lb = getLoadBalancer();
			if (lb == null) {
				return;
			}

			// 存在并发情况，加个🔐
			if (!serverWeightAssignmentInProgress.compareAndSet(false, true)) {
				return;
			}

			try {
				logger.info("Weight adjusting job started");
				// 获取ribbon核心组件
				AbstractLoadBalancer nlb = (AbstractLoadBalancer) lb;
				LoadBalancerStats stats = nlb.getLoadBalancerStats();
				// 需要取每个服务实例的响应时间，如果没有负载均衡器状态，那么没有必要进行下去了
				if (stats == null) {
					return;
				}
				double totalResponseTime = 0;
				for (Server server : nlb.getAllServers()) {
					// 遍历每个节点，加和平均响应时间
					ServerStats ss = stats.getSingleServerStat(server);
					totalResponseTime += ss.getResponseTimeAvg();
				}
				// 权重计算公式是：集群的响应时间-总共响应时间，数字越小，权重越清
				// 集群响应时间是遍历中的加和
				Double weightSoFar = 0.0;

				// 一次将权重叠加放入到列表中
				List<Double> finalWeights = new ArrayList<Double>();
				for (Server server : nlb.getAllServers()) {
					ServerStats ss = stats.getSingleServerStat(server);
					double weight = totalResponseTime - ss.getResponseTimeAvg();
					weightSoFar += weight;
					finalWeights.add(weightSoFar);
				}
				// 设置权重值
				setWeights(finalWeights);
			} catch (Exception e) {
				logger.error("Error calculating server weights", e);
			} finally {
				// 权重设置开关关闭
				serverWeightAssignmentInProgress.set(false);
			}

		}
	}

	void setWeights(List<Double> weights) {
		this.accumulatedWeights = weights;
	}

	@Override
	public void initWithNiwsConfig(IClientConfig clientConfig) {
		super.initWithNiwsConfig(clientConfig);
		serverWeightTaskTimerInterval = clientConfig.get(WEIGHT_TASK_TIMER_INTERVAL_CONFIG_KEY, DEFAULT_TIMER_INTERVAL);
	}

}
