package com.xingshu.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.xingshu.common.context.UserContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理
 */
@Component
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.handlers.MetaObjectHandler")
@ConditionalOnBean(DataSource.class)
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        Long userId = UserContextHolder.getUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createrId", Long.class, userId);
            this.strictInsertFill(metaObject, "updaterId", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        Long userId = UserContextHolder.getUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updaterId", Long.class, userId);
        }
    }
}