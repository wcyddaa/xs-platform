package com.xingshu.user.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.user.domain.po.AdminClass;
import com.xingshu.user.service.IAdminClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin-classes")
@Tag(name = "行政班管理", description = "行政班的查询")
public class AdminClassController {

    private final IAdminClassService adminClassService;

    public AdminClassController(IAdminClassService adminClassService) {
        this.adminClassService = adminClassService;
    }

    @Operation(summary = "获取所有行政班")
    @GetMapping
    public ApiResponse<List<AdminClass>> listAll() {
        return ApiResponse.success(adminClassService.listAll());
    }
}