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
 * 为负载均衡声明一个规则接口
 * 规则，可以是一种策略
 * 比如众所周知的轮询，基于响应时间等
 *
 * @author stonse
 * 
 */
public interface IRule{
	/**
	 * 从所有服务器中选择一个服务器
	 * @param key
	 * @return
	 */
    public Server choose(Object key);

	/**
	 * 设置负载均衡
 	 * @param lb
	 */
    public void setLoadBalancer(ILoadBalancer lb);

	/**
	 * 获取负载均衡
	 * @return
	 */
	public ILoadBalancer getLoadBalancer();
}
