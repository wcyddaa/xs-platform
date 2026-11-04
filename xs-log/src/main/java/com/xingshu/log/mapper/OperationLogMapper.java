package com.xingshu.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingshu.log.domain.po.OperationLog;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OperationLogMapper extends BaseMapper<OperationLog> {
    // Only INSERT and SELECT allowed - no update/delete methods
    // BaseMapper provides insert() and select methods

    @Select("SELECT * FROM operation_log WHERE operator_id = #{operatorId} ORDER BY create_time DESC LIMIT #{limit}")
    List<OperationLog> findByOperator(Long operatorId, int limit);

    @Select("SELECT * FROM operation_log WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY create_time DESC")
    List<OperationLog> findByTarget(String targetType, String targetId);
}