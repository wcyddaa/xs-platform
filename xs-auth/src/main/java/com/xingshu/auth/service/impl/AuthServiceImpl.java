package com.xingshu.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.auth.common.constants.AuthErrorInfo;
import com.xingshu.auth.common.constants.JwtConstants;
import com.xingshu.auth.common.exception.AuthException;
import com.xingshu.auth.domain.dto.LoginFormDTO;
import com.xingshu.auth.domain.dto.LoginUserDTO;
import com.xingshu.auth.domain.dto.TokenResponse;
import com.xingshu.auth.domain.po.User;
import com.xingshu.auth.mapper.UserMapper;
import com.xingshu.auth.service.IAuthService;
import com.xingshu.auth.util.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 认证服务实现
 * 参考 tjxt 项目 AccountServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl extends ServiceImpl<UserMapper, User> implements IAuthService {

    private final JwtTool jwtTool;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private static final Map<Integer, String> ROLE_NAMES = Map.of(
            1, "管理员",
            2, "老师",
            3, "学生"
    );

    @Override
    public TokenResponse login(LoginFormDTO loginForm) {
        // 1.根据用户名和身份查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginForm.getUsername())
                .eq(User::getStatus, 1)
                .eq(User::getType, loginForm.getType());
        User user = userMapper.selectOne(wrapper);

        // 2.用户不存在
        if (user == null) {
            throw AuthException.badRequest(AuthErrorInfo.Msg.LOGIN_FAILED);
        }

        // 3.BCrypt 密码校验
        if (!passwordEncoder.matches(loginForm.getPassword(), user.getPassword())) {
            throw AuthException.badRequest(AuthErrorInfo.Msg.LOGIN_FAILED);
        }

        // 4.构建用户信息
        LoginUserDTO userDTO = LoginUserDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .number(user.getNumber())
                .roleId(user.getType())
                .roleName(ROLE_NAMES.getOrDefault(user.getType(), "未知"))
                .build();

        // 5.生成 token
        String accessToken = jwtTool.createToken(userDTO);
        String refreshToken = jwtTool.createRefreshToken(userDTO);

        log.info("用户 {}（角色: {}）登录成功", userDTO.getUsername(), userDTO.getRoleName());

        // 6.返回结果
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(JwtConstants.JWT_TOKEN_TTL.toSeconds())
                .userInfo(userDTO)
                .build();
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        // 1.校验 refresh-token
        LoginUserDTO userDTO = jwtTool.parseRefreshToken(refreshToken);

        // 2.生成新的 token 对
        String accessToken = jwtTool.createToken(userDTO);
        String newRefreshToken = jwtTool.createRefreshToken(userDTO);

        log.info("用户 {} 刷新 token 成功", userDTO.getUsername());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(JwtConstants.JWT_TOKEN_TTL.toSeconds())
                .userInfo(userDTO)
                .build();
    }

    @Override
    public void logout(Long userId) {
        // 清理 refresh-token 的 JTI，使其作废
        jwtTool.cleanJtiCache(userId);
        log.info("用户 {} 登出成功", userId);
    }
}