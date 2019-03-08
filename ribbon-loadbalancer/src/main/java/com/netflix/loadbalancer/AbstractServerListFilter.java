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


/**
 * 负责过滤从LoadBalancer的集群中过滤出符合条件的可用的服务器
 */
public abstract class AbstractServerListFilter<T extends Server> implements ServerListFilter<T> {

	private volatile LoadBalancerStats stats;

	public void setLoadBalancerStats(LoadBalancerStats stats) {
		this.stats = stats;
	}

	public LoadBalancerStats getLoadBalancerStats() {
		return stats;
	}

}
