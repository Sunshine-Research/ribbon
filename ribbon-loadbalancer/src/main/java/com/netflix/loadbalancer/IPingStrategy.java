package com.netflix.loadbalancer;

/**
 * 是ping所有服务器的ping策略
 * 将注册在BaseLoadBalancer中
 * 你可以实现自己的策略，比如并发ping
 * 但是要注意的是，ping策略最好不要重复
 * @author Dmitry_Cherkas
 * @see Server
 * @see IPing
 */
public interface IPingStrategy {

    boolean[] pingServers(IPing ping, Server[] servers);
}
