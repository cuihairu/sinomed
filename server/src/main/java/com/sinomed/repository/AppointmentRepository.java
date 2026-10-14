package com.sinomed.repository;

import com.sinomed.entity.AppointmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity,Long> {

    /** 管理端分页（status 可选过滤由控制器分流到 findAll / 本方法） */
    Page<AppointmentEntity> findByStatus(Integer status, Pageable pageable);

    /** 手机自助查预约：按顾客拉全量预约（待到店/未过期过滤在控制器做） */
    List<AppointmentEntity> findByCustomerIdOrderByIdDesc(Long customerId);

    /** Kiosk 自助约期频控：同顾客同日在给定状态集合内的预约数（status IN (0,1) 限 1 条） */
    long countByCustomerIdAndStartTimeBetweenAndStatusIn(Long customerId, Date start, Date end, Collection<Integer> statusList);

    /** 每时段限约 1 位：同医师在 [start, end) 半开区间内的预约数（BETWEEN 含端点，会把下一整点的预约算进上一格） */
    long countByStaffIdAndStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(
            Long staffId, Date start, Date end, Collection<Integer> statusList);

    /** 每时段限约 1 位：同医师在 [start, end) 半开区间内的预约数（staffId 为空，Kiosk 自助约期到店分配） */
    long countByStaffIdIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(
            Date start, Date end, Collection<Integer> statusList);

    /** 按时段过滤分页（date 参数落到 [day 00:00, 次日 00:00) 区间） */
    Page<AppointmentEntity> findByStartTimeBetween(Date start, Date end, Pageable pageable);

    /** 时段 + 状态双过滤分页 */
    Page<AppointmentEntity> findByStartTimeBetweenAndStatus(Date start, Date end, Integer status, Pageable pageable);

    /** 到店自动叫号的当日接待序号：今天（接待时刻起算）已接待且先于本单的预约数 */
    long countByStatusAndIdLessThanAndUpdateTimeGreaterThanEqual(Integer status, Long id, Date time);
}
