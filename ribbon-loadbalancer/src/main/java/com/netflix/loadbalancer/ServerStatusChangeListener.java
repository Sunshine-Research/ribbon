/**
 * Copyright 2015 Netflix, Inc.
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
 */
package com.netflix.loadbalancer;

import java.util.Collection;

public interface ServerStatusChangeListener {

    /**
	 * 当服务器状态发生变化时（可以是被标记为已宕机，或者是ping策略发现服务器已经处于dead状态），BaseLoadBalancer会调用这个方法
     * @param servers the servers that had their status changed, never {@code null}
     */
    public void serverStatusChanged(Collection<Server> servers);

}
