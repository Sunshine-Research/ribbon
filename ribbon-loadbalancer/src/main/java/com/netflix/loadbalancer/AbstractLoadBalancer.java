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

import java.util.List;

/**
 * AbstractLoadBalancer包含了大多数负载均衡所需要的特性
 * 1. 有一个指定标准的集群
 * 2. 声明或者实现一个负载均衡策略
 * 3. 可以在集群中找到适合的，可用的节点或者服务器
 */
public abstract class AbstractLoadBalancer implements ILoadBalancer {
    
    public enum ServerGroup{
        ALL,
        STATUS_UP,
        STATUS_NOT_UP        
    }
        
    /**
     * 一个没有入参的服务器选择
     */
    public Server chooseServer() {
    	return chooseServer(null);
    }

    
    /**
	 * 获取负载均衡器负责管理的集群服务列表
     */
	public abstract List<Server> getServerList(ServerGroup serverGroup);

	/**
     * 提供负载均衡器的数据分析
     */
    public abstract LoadBalancerStats getLoadBalancerStats();    
}
