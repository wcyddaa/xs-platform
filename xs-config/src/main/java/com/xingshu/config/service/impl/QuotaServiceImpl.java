package com.xingshu.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xingshu.config.entity.ClassQuota;
import com.xingshu.config.entity.PlatformQuota;
import com.xingshu.config.mapper.ClassQuotaMapper;
import com.xingshu.config.mapper.PlatformQuotaMapper;
import com.xingshu.config.service.IQuotaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuotaServiceImpl implements IQuotaService {

    private static final long DEFAULT_PLATFORM_TOTAL = 10_000_000L;

    private final PlatformQuotaMapper platformQuotaMapper;
    private final ClassQuotaMapper classQuotaMapper;

    public QuotaServiceImpl(PlatformQuotaMapper platformQuotaMapper,
                            ClassQuotaMapper classQuotaMapper) {
        this.platformQuotaMapper = platformQuotaMapper;
        this.classQuotaMapper = classQuotaMapper;
    }

    private PlatformQuota platform() {
        PlatformQuota quota = platformQuotaMapper.selectById(1L);
        if (quota == null) {
            quota = new PlatformQuota();
            quota.setId(1L);
            quota.setTotalTokenLimit(DEFAULT_PLATFORM_TOTAL);
            quota.setTokenUsed(0L);
            platformQuotaMapper.insert(quota);
        }
        return quota;
    }

    @Override
    public Map<String, Object> platformStats() {
        PlatformQuota platform = platform();
        List<ClassQuota> classes = classQuotaMapper.selectList(new LambdaQueryWrapper<>());
        long allocated = classes.stream()
                .mapToLong(c -> c.getTotalTokenLimit() == null ? 0L : c.getTotalTokenLimit())
                .sum();
        long used = platform.getTokenUsed() == null ? 0L : platform.getTokenUsed();
        long total = platform.getTotalTokenLimit() == null ? 0L : platform.getTotalTokenLimit();

        List<Map<String, Object>> classItems = new ArrayList<>();
        for (ClassQuota c : classes) {
            Map<String, Object> item = new HashMap<>();
            item.put("classId", c.getClassId());
            item.put("totalTokenLimit", c.getTotalTokenLimit() == null ? 0L : c.getTotalTokenLimit());
            item.put("tokenUsed", c.getTokenUsed() == null ? 0L : c.getTokenUsed());
            classItems.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalTokenLimit", total);
        result.put("allocatedTokenLimit", allocated);
        result.put("availableTokenLimit", Math.max(total - allocated, 0L));
        result.put("tokenUsed", used);
        result.put("classCount", classes.size());
        result.put("classes", classItems);
        return result;
    }

    @Override
    public Map<String, Object> platformOverview() {
        PlatformQuota platform = platform();
        List<ClassQuota> classes = classQuotaMapper.selectList(new LambdaQueryWrapper<>());
        long allocated = classes.stream()
                .mapToLong(c -> c.getTotalTokenLimit() == null ? 0L : c.getTotalTokenLimit())
                .sum();
        long total = platform.getTotalTokenLimit() == null ? 0L : platform.getTotalTokenLimit();
        long used = platform.getTokenUsed() == null ? 0L : platform.getTokenUsed();

        Map<String, Object> result = new HashMap<>();
        result.put("totalTokenLimit", total);
        result.put("allocatedTokenLimit", allocated);
        result.put("availableTokenLimit", Math.max(total - allocated, 0L));
        result.put("tokenUsed", used);
        result.put("classCount", classes.size());
        return result;
    }

    @Override
    public Map<String, Object> updatePlatformTotal(Long totalTokenLimit) {
        if (totalTokenLimit == null || totalTokenLimit < 0) {
            throw new IllegalArgumentException("平台总额度不能为空或负数");
        }
        long allocated = classQuotaMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .mapToLong(c -> c.getTotalTokenLimit() == null ? 0L : c.getTotalTokenLimit())
                .sum();
        if (totalTokenLimit < allocated) {
            throw new IllegalArgumentException("平台总额度不能低于已分配给教学班的额度（" + allocated + "）");
        }
        PlatformQuota platform = platform();
        platform.setTotalTokenLimit(totalTokenLimit);
        platformQuotaMapper.updateById(platform);
        return platformStats();
    }

    @Override
    public Map<String, Object> getClassQuota(Long classId) {
        ClassQuota quota = findByClassId(classId);
        Map<String, Object> result = new HashMap<>();
        result.put("classId", classId);
        result.put("configured", quota != null);
        long total = quota == null || quota.getTotalTokenLimit() == null ? 0L : quota.getTotalTokenLimit();
        long used = quota == null || quota.getTokenUsed() == null ? 0L : quota.getTokenUsed();
        result.put("totalTokenLimit", total);
        result.put("tokenUsed", used);
        result.put("remainingTokenLimit", Math.max(total - used, 0L));
        return result;
    }

    @Override
    @Transactional
    public ClassQuota upsertClassQuota(Long classId, Long totalTokenLimit, Long operatorId) {
        if (classId == null) {
            throw new IllegalArgumentException("教学班不能为空");
        }
        if (totalTokenLimit == null || totalTokenLimit < 0) {
            throw new IllegalArgumentException("教学班额度不能为空或负数");
        }
        ClassQuota existing = findByClassId(classId);
        long used = existing == null || existing.getTokenUsed() == null ? 0L : existing.getTokenUsed();
        if (totalTokenLimit < used) {
            throw new IllegalArgumentException("教学班额度不能低于已消耗的 token（" + used + "）");
        }

        long allocatedOthers = classQuotaMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(c -> !classId.equals(c.getClassId()))
                .mapToLong(c -> c.getTotalTokenLimit() == null ? 0L : c.getTotalTokenLimit())
                .sum();
        PlatformQuota platform = platform();
        long total = platform.getTotalTokenLimit() == null ? 0L : platform.getTotalTokenLimit();
        if (allocatedOthers + totalTokenLimit > total) {
            throw new IllegalArgumentException("平台剩余可分配额度不足，当前最多可分配 "
                    + Math.max(total - allocatedOthers, 0L) + " token");
        }

        if (existing == null) {
            existing = new ClassQuota();
            existing.setClassId(classId);
            existing.setTotalTokenLimit(totalTokenLimit);
            existing.setTokenUsed(0L);
            existing.setCreaterId(operatorId);
            classQuotaMapper.insert(existing);
        } else {
            existing.setTotalTokenLimit(totalTokenLimit);
            existing.setUpdaterId(operatorId);
            classQuotaMapper.updateById(existing);
        }
        return existing;
    }

    @Override
    @Transactional
    public void consume(Long classId, int tokens) {
        if (classId == null || tokens <= 0) {
            return;
        }
        ClassQuota quota = findByClassId(classId);
        if (quota != null) {
            LambdaUpdateWrapper<ClassQuota> classUpdate = new LambdaUpdateWrapper<>();
            classUpdate.eq(ClassQuota::getId, quota.getId())
                    .setSql("token_used = token_used + " + tokens);
            classQuotaMapper.update(null, classUpdate);
        }
        LambdaUpdateWrapper<PlatformQuota> platformUpdate = new LambdaUpdateWrapper<>();
        platformUpdate.eq(PlatformQuota::getId, platform().getId())
                .setSql("token_used = token_used + " + tokens);
        platformQuotaMapper.update(null, platformUpdate);
    }

    @Override
    public boolean isExhausted(Long classId) {
        ClassQuota quota = findByClassId(classId);
        if (quota == null) {
            return true;
        }
        long total = quota.getTotalTokenLimit() == null ? 0L : quota.getTotalTokenLimit();
        long used = quota.getTokenUsed() == null ? 0L : quota.getTokenUsed();
        return total <= 0 || used >= total;
    }

    private ClassQuota findByClassId(Long classId) {
        LambdaQueryWrapper<ClassQuota> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassQuota::getClassId, classId).last("limit 1");
        return classQuotaMapper.selectOne(wrapper);
    }
}
