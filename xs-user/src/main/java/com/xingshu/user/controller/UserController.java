package com.xingshu.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.user.domain.po.User;
import com.xingshu.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口
 * 参考 tjxt 项目 UserController
 */
@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "用户的增删改查、批量导入、条件查询")
public class UserController {

    @Autowired
    private IUserService userService;

    @Operation(summary = "根据ID查询用户")
    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        return ApiResponse.success(user);
    }

    @Operation(summary = "根据用户名查询用户")
    @GetMapping("/username/{username}")
    public ApiResponse<User> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        return ApiResponse.success(user);
    }

    @Operation(summary = "根据工号查询用户")
    @GetMapping("/number/{number}")
    public ApiResponse<User> getUserByNumber(@PathVariable String number) {
        User user = userService.getUserByNumber(number);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        return ApiResponse.success(user);
    }

    @Operation(summary = "添加用户")
    @PostMapping
    public ApiResponse<User> addUser(@RequestBody User user) {
        userService.addUser(user);
        return ApiResponse.success(user);
    }

    @Operation(summary = "更新用户")
    @PutMapping
    public ApiResponse<Void> updateUser(@RequestBody User user) {
        if (user.getId() == null) {
            return ApiResponse.error(400, "用户ID不能为空");
        }
        userService.updateUser(user);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@RequestParam Long userId, @RequestParam String password) {
        userService.updatePassword(userId, password);
        return ApiResponse.success(null);
    }

    @Operation(summary = "禁用用户")
    @PutMapping("/disable/{userId}")
    public ApiResponse<Void> disableUser(@PathVariable Long userId) {
        userService.disableUser(userId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "启用用户")
    @PutMapping("/enable/{userId}")
    public ApiResponse<Void> enableUser(@PathVariable Long userId) {
        userService.enableUser(userId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "用户登录（旧接口，建议使用 xs-auth 的 /auth/login）")
    @PostMapping("/login")
    public ApiResponse<User> login(@RequestParam String username, @RequestParam String password) {
        User user = userService.login(username, password);
        if (user != null) {
            return ApiResponse.success(user);
        }
        return ApiResponse.error(401, "用户名或密码错误，或账号已被禁用");
    }

    @Operation(summary = "条件分页查询用户")
    @GetMapping("/page")
    public ApiResponse<PageResult<User>> getUserPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "搜索关键词（用户名/手机号）") @RequestParam(required = false) String keyword,
            @Parameter(description = "身份类型：1-管理员,2-老师,3-学生") @RequestParam(required = false) Integer type,
            @Parameter(description = "状态：0-禁用,1-正常") @RequestParam(required = false) Integer status) {
        Page<User> page = userService.getUserPageByCondition(pageNum, pageSize, keyword, type, status);
        PageResult<User> result = PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
        return ApiResponse.success(result);
    }

    @Operation(summary = "批量导入用户")
    @PostMapping("/batch-import")
    public ApiResponse<Integer> batchImportUsers(@RequestBody List<User> users) {
        int count = userService.batchImportUsers(users);
        return ApiResponse.success("成功导入 " + count + " 个用户", count);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        Map<String, Long> stats = userService.getStats();
        return ApiResponse.success(stats);
    }

    @Operation(summary = "批量查询用户ID对应的用户名")
    @GetMapping("/batch")
    public ApiResponse<Map<Long, String>> getUsernamesByIds(
            @Parameter(description = "用户ID列表，逗号分隔") @RequestParam List<Long> ids) {
        return ApiResponse.success(userService.getUsernamesByIds(ids));
    }
}