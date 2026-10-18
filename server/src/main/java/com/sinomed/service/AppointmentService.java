package com.sinomed.service;

import com.sinomed.entity.AppointmentEntity;
import com.sinomed.vo.AppointmentView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.Optional;

public interface AppointmentService {
    /**
     * 根据预约的id查询预约
     * */
    Optional<AppointmentEntity> findById(Long id);
    /**
     * 保存预约（新建：status 置 0 待到店；每时段限约 1 位，同医师/同工位同小时已有待到店/已接待预约报错）
     * */
    AppointmentEntity save(AppointmentView view);
    /**
     * 每时段限约 1 位：同医师（staffId 为空归同组）同小时已有 status 0/1 预约则抛 IllegalArgumentException；
     * roomId 非空时工位维同样互斥（美容业态 b 双维冲突，docs/design/salon-extension.md §2-b）
     * */
    void ensureSlotFree(Date startTime, Long staffId, Long roomId);
    /**
     * 根据预约的id删除预约
     * */
    void deleteById(Long id);
    /**
     * 管理端分页；status 为空查全部
     * */
    Page<AppointmentEntity> findPage(Integer status, Pageable pageable);
    /**
     * 状态流转：只允许 0→1（到店接待）、0→8（过号）与 0→9（取消）；非法流转抛 IllegalArgumentException
     * */
    AppointmentEntity updateStatus(Long id, Integer status);
}
