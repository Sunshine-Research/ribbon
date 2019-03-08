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
package com.netflix.client;

import com.netflix.client.config.IClientConfig;

/**
 * 多个类或者组件都需要访问配置
 * 如果我们使用IClientConfig来作为存储配置的对象，所有事情都会变得简单
 * 所以我们有了这个接口，供实现
 * @author stonse
 * @author awang 
 *
 */
public interface IClientConfigAware {
    interface Factory {
        Object create(String type, IClientConfig config) throws InstantiationException, IllegalAccessException, ClassNotFoundException;
    }

    /**
	 * 实现Niws的配置
     * @param clientConfig
     */
    default void initWithNiwsConfig(IClientConfig clientConfig) {
    }

    default void initWithNiwsConfig(IClientConfig clientConfig, Factory factory) {
        initWithNiwsConfig(clientConfig);
    }
    
}
