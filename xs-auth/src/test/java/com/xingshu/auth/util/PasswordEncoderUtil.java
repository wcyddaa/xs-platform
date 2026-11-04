package com.xingshu.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 临时工具：生成 BCrypt 密码哈希，用于初始化数据库中的密码
 * 使用方法：直接运行此类的 main 方法，然后复制输出到 SQL UPDATE 语句
 */
public class PasswordEncoderUtil {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 需要加密的密码
        String password = "123456";
        String encoded = encoder.encode(password);

        System.out.println("原始密码: " + password);
        System.out.println("BCrypt 哈希: " + encoded);
        System.out.println();
        System.out.println("SQL 更新语句:");
        System.out.println("UPDATE user SET password = '" + encoded + "' WHERE username = 'admin001';");
        System.out.println("UPDATE user SET password = '" + encoded + "' WHERE username = 'teacher001';");
        System.out.println("UPDATE user SET password = '" + encoded + "' WHERE username = 'student001';");
    }
}