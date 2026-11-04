package com.xingshu.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.log.domain.po.OperationLog;
import com.xingshu.log.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogService extends ServiceImpl<OperationLogMapper, OperationLog> {

    public List<OperationLog> findByOperator(Long operatorId, int limit) {
        return baseMapper.findByOperator(operatorId, limit);
    }

    public List<OperationLog> findByTarget(String targetType, String targetId) {
        return baseMapper.findByTarget(targetType, targetId);
    }

    public Page<OperationLog> pageByType(String operationType, int pageNum, int pageSize) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(OperationLog::getOperationType, operationType);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    // Only INSERT (save) and SELECT (list/page/getById) are used
    // No update/delete methods are defined
}