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

import java.io.Closeable;
import java.net.URI;
import java.util.Map;

/**
 * 客户端响应接口
 */
public interface IResponse extends Closeable {

	/**
	 * 返回响应的原始实体
	 */
	public Object getPayload() throws ClientException;

	/**
	 * 判断响应中是否有实体
	 */
	public boolean hasPayload();

	/**
	 * 判断响应是否成功
	 * @return true if the response is deemed success, for example, 200 response code for http protocol.
	 */
	public boolean isSuccess();


	/**
	 * 请求的URI
	 */
	public URI getRequestedURI();

	/**
	 * 响应头
	 */
	public Map<String, ?> getHeaders();
}
