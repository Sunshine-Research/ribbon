/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.loadbalancer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.Property;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 负载均衡的集群是整个集群的子集，在集群规模很大的时非常有用，可以最大化利用http连接池，避免所有的连接都在一个池子中
 * 它还可以通过网络故障和并发连接数来判断和摘除不健康的服务器
 */
public class ServerListSubsetFilter<T extends Server> extends ZoneAffinityServerListFilter<T> implements IClientConfigAware, Comparator<T>{

    private Random random = new Random();
    private volatile Set<T> currentSubset = Sets.newHashSet(); 
    private Property<Integer> sizeProp;
    private Property<Float> eliminationPercent;
    private Property<Integer> eliminationFailureCountThreshold;
    private Property<Integer> eliminationConnectionCountThreshold;

    private static final IClientConfigKey<Integer> SIZE = new CommonClientConfigKey<Integer>("ServerListSubsetFilter.size", 20) {};
    private static final IClientConfigKey<Float> FORCE_ELIMINATE_PERCENT = new CommonClientConfigKey<Float>("ServerListSubsetFilter.forceEliminatePercent", 0.1f) {};
    private static final IClientConfigKey<Integer> ELIMINATION_FAILURE_THRESHOLD = new CommonClientConfigKey<Integer>("ServerListSubsetFilter.eliminationFailureThresold", 0) {};
    private static final IClientConfigKey<Integer> ELIMINATION_CONNECTION_THRESHOLD = new CommonClientConfigKey<Integer>("ServerListSubsetFilter.eliminationConnectionThresold", 0) {};

    /**
     * @deprecated ServerListSubsetFilter should only be created with an IClientConfig.  See {@link ServerListSubsetFilter#ServerListSubsetFilter(IClientConfig)}
     */
    @Deprecated
    public ServerListSubsetFilter() {
        sizeProp = Property.of(SIZE.defaultValue());
        eliminationPercent = Property.of(FORCE_ELIMINATE_PERCENT.defaultValue());
        eliminationFailureCountThreshold = Property.of(ELIMINATION_FAILURE_THRESHOLD.defaultValue());
        eliminationConnectionCountThreshold = Property.of(ELIMINATION_CONNECTION_THRESHOLD.defaultValue());
    }

    public ServerListSubsetFilter(IClientConfig clientConfig) {
        super(clientConfig);

        initWithNiwsConfig(clientConfig);
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        sizeProp = clientConfig.getDynamicProperty(SIZE);
        eliminationPercent = clientConfig.getDynamicProperty(FORCE_ELIMINATE_PERCENT);
        eliminationFailureCountThreshold = clientConfig.getDynamicProperty(ELIMINATION_FAILURE_THRESHOLD);
        eliminationConnectionCountThreshold = clientConfig.getDynamicProperty(ELIMINATION_CONNECTION_THRESHOLD);
    }

    /**
     * Given all the servers, keep only a stable subset of servers to use. This method
     * keeps the current list of subset in use and keep returning the same list, with exceptions
     * to relatively unhealthy servers, which are defined as the following:
     * <p>
     * <ul>
     * <li>Servers with their concurrent connection count exceeding the client configuration for 
     *  {@code <clientName>.<nameSpace>.ServerListSubsetFilter.eliminationConnectionThresold} (default is 0)
     * <li>Servers with their failure count exceeding the client configuration for 
     *  {@code <clientName>.<nameSpace>.ServerListSubsetFilter.eliminationFailureThresold}  (default is 0)
     *  <li>If the servers evicted above is less than the forced eviction percentage as defined by client configuration
     *   {@code <clientName>.<nameSpace>.ServerListSubsetFilter.forceEliminatePercent} (default is 10%, or 0.1), the
     *   remaining servers will be sorted by their health status and servers will worst health status will be
     *   forced evicted.
     * </ul>
     * <p>
     * After the elimination, new servers will be randomly chosen from all servers pool to keep the
     * number of the subset unchanged. 
     * 
     */
    @Override
    public List<T> getFilteredListOfServers(List<T> servers) {
    	// 先去获取同分区下的服务器列表
        List<T> zoneAffinityFiltered = super.getFilteredListOfServers(servers);
        // 进行一次去重
        Set<T> candidates = Sets.newHashSet(zoneAffinityFiltered);
        // 当前服务器子集
        Set<T> newSubSet = Sets.newHashSet(currentSubset);
        // 获取负载均衡器状态
        LoadBalancerStats lbStats = getLoadBalancerStats();
        for (T server: currentSubset) {
        	// 遍历所有的集群，删除没有从分区中查出来的集群
            if (!candidates.contains(server)) {
                newSubSet.remove(server);
            } else {
            	// 从负载均衡器状态中获取当前服务器的状态
                ServerStats stats = lbStats.getSingleServerStat(server);
				// 去除不满足服务状态的服务集群
                if (stats.getActiveRequestsCount() > eliminationConnectionCountThreshold.getOrDefault()
                        || stats.getFailureCount() > eliminationFailureCountThreshold.getOrDefault()) {
                    newSubSet.remove(server);
                    // 分区列表同时也删除对应的服务器
                    candidates.remove(server);
                }
            }
        }
		// 子集群的大小
        int targetedListSize = sizeProp.getOrDefault();
		// 看看摘除掉了多少个集群
        int numEliminated = currentSubset.size() - newSubSet.size();
		// 算一个最小摘除数
        int minElimination = (int) (targetedListSize * eliminationPercent.getOrDefault());
        int numToForceEliminate = 0;
        if (targetedListSize < newSubSet.size()) {
            numToForceEliminate = newSubSet.size() - targetedListSize;
		} else if (minElimination > numEliminated) {
			numToForceEliminate = minElimination - numEliminated;
		}

		if (numToForceEliminate > newSubSet.size()) {
            numToForceEliminate = newSubSet.size();
        }

		// 如果需要摘除，排序后，从小到大进行摘除
        if (numToForceEliminate > 0) {
            List<T> sortedSubSet = Lists.newArrayList(newSubSet);
            Collections.sort(sortedSubSet, this);
			List<T> forceEliminated = sortedSubSet.subList(0, numToForceEliminate);
			newSubSet.removeAll(forceEliminated);
			candidates.removeAll(forceEliminated);
        }
        
		// 我们摘除后，需要进行一次服务器补充，是从当前分区集群中随机选择机器进行补充
        if (newSubSet.size() < targetedListSize) {
            int numToChoose = targetedListSize - newSubSet.size();
            candidates.removeAll(newSubSet);
            if (numToChoose > candidates.size()) {
                // Not enough healthy instances to choose, fallback to use the
                // total server pool
                candidates = Sets.newHashSet(zoneAffinityFiltered);
                candidates.removeAll(newSubSet);
            }
            List<T> chosen = randomChoose(Lists.newArrayList(candidates), numToChoose);
            for (T server: chosen) {
                newSubSet.add(server);
            }
        }
        // 重置当前子集群
        currentSubset = newSubSet;       
        return Lists.newArrayList(newSubSet);            
    }

    /**
     * Randomly shuffle the beginning portion of server list (according to the number passed into the method) 
     * and return them.
     *  
     * @param servers
     * @param toChoose
     * @return
     */
    private List<T> randomChoose(List<T> servers, int toChoose) {
        int size = servers.size();
        if (toChoose >= size || toChoose < 0) {
            return servers;
        } 
        for (int i = 0; i < toChoose; i++) {
            int index = random.nextInt(size);
            T tmp = servers.get(index);
            servers.set(index, servers.get(i));
            servers.set(i, tmp);
        }
        return servers.subList(0, toChoose);        
    }

    /**
     * Function to sort the list by server health condition, with
     * unhealthy servers before healthy servers. The servers are first sorted by
     * failures count, and then concurrent connection count.
     */
    @Override
    public int compare(T server1, T server2) {
        LoadBalancerStats lbStats = getLoadBalancerStats();
        ServerStats stats1 = lbStats.getSingleServerStat(server1);
        ServerStats stats2 = lbStats.getSingleServerStat(server2);
        int failuresDiff = (int) (stats2.getFailureCount() - stats1.getFailureCount());
        if (failuresDiff != 0) {
            return failuresDiff;
        } else {
            return (stats2.getActiveRequestsCount() - stats1.getActiveRequestsCount());
        }
    }
}
