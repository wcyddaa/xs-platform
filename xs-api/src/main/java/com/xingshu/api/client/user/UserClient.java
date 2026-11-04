package com.xingshu.api.client.user;

import com.xingshu.common.base.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 客户端
 */
@FeignClient(name = "xs-user", url = "${xs.api.user-url:http://localhost:8080}")
public interface UserClient {

    @GetMapping("/api/user/{userId}")
    ApiResponse<UserDTO> getUserById(@PathVariable Long userId);

    @GetMapping("/api/user/{userId}/role")
    ApiResponse<String> getUserRole(@PathVariable Long userId);
}