package com.xingshu.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.user.domain.po.User;

import java.util.List;
import java.util.Map;

/**
 * 用户表 服务类
 */
public interface IUserService extends IService<User> {

    User getUserById(Long id);

    User getUserByUsername(String username);

    User getUserByNumber(String number);

    void addUser(User user);

    void updateUser(User user);

    void deleteUser(Long id);

    void updatePassword(Long userId, String password);

    void disableUser(Long userId);

    void enableUser(Long userId);

    User login(String username, String password);

    Page<User> getUserPage(Integer pageNum, Integer pageSize);

    /**
     * 条件分页查询用户
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param keyword   搜索关键词（用户名/手机号）
     * @param type      身份类型：1-管理员, 2-老师, 3-学生，null-全部
     * @param status    状态：0-禁用, 1-正常，null-全部
     */
    Page<User> getUserPageByCondition(Integer pageNum, Integer pageSize,
                                       String keyword, Integer type, Integer status);

    /**
     * 批量导入用户
     * @param users 用户列表
     * @return 成功导入的数量
     */
    int batchImportUsers(List<User> users);

    Map<String, Long> getStats();

    /**
     * 批量查询用户ID对应的用户名
     * @param ids 用户ID列表
     * @return ID -> username 映射
     */
    Map<Long, String> getUsernamesByIds(List<Long> ids);
}