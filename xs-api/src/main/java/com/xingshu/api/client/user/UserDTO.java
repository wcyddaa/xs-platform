package com.xingshu.api.client.user;

import lombok.Data;

/**
 * 用户信息 DTO
 */
@Data
public class UserDTO {
    private Long id;
    private String account;
    private String name;
    private String role;
    private String status;
}