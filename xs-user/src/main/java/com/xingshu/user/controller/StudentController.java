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

/**
 * 学生管理接口
 * 参考 tjxt 项目 StudentController
 */
@RestController
@RequestMapping("/students")
@Tag(name = "学生管理", description = "学生信息的分页查询、注册、密码修改")
public class StudentController {

    @Autowired
    private IUserService userService;

    @Operation(summary = "分页查询学生信息")
    @GetMapping("/page")
    public ApiResponse<PageResult<User>> queryStudentPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "搜索关键词（用户名/手机号）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：0-禁用,1-正常") @RequestParam(required = false) Integer status) {
        Page<User> page = userService.getUserPageByCondition(pageNum, pageSize, keyword, 3, status);
        PageResult<User> result = PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
        return ApiResponse.success(result);
    }

    @Operation(summary = "学生注册")
    @PostMapping("/register")
    public ApiResponse<Void> registerStudent(@RequestBody User user) {
        user.setType(3); // 强制设置为学生身份
        user.setStatus(1); // 默认正常状态
        userService.addUser(user);
        return ApiResponse.success(null);
    }

    @Operation(summary = "修改学生密码")
    @PutMapping("/{id}/password")
    public ApiResponse<Void> updateStudentPassword(
            @PathVariable Long id,
            @RequestParam String password) {
        userService.updatePassword(id, password);
        return ApiResponse.success(null);
    }
}