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
 * ping，判断服务器是否存活
 * @author stonse
 *
 */
public interface IPing {
    
    /**
     * 检查指定的服务器，是否存活
	 * 用于在负载均衡时，判断服务器是否可作为一个候选者
     */
    public boolean isAlive(Server server);
}
