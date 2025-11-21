package org.zsy.bysj.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket配置类
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，用于向客户端发送消息
        config.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息的前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                 WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                        String token = null;
                        
                        // 1. 优先从Header中获取Authorization
                        String authHeader = request.getHeaders().getFirst("Authorization");
                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            token = authHeader.substring(7);
                            System.out.println("WebSocket握手：从Header提取到token");
                        }
                        
                        // 2. 如果Header中没有，从查询参数中提取token
                        if (token == null) {
                            String query = request.getURI().getQuery();
                            if (query != null && query.contains("token=")) {
                                String[] params = query.split("&");
                                for (String param : params) {
                                    if (param.startsWith("token=")) {
                                        token = java.net.URLDecoder.decode(param.substring(6), "UTF-8");
                                        System.out.println("WebSocket握手：从查询参数提取到token");
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // 3. 将token存入attributes，供后续使用
                        if (token != null && !token.isEmpty()) {
                            attributes.put("token", token);
                            System.out.println("WebSocket握手：token已存入token到session attributes");
                        } else {
                            System.out.println("WebSocket握手：未找到token");
                        }
                        
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                              WebSocketHandler wsHandler, Exception exception) {
                        // 握手后的处理
                    }
                })
                .withSockJS();
    }
}

