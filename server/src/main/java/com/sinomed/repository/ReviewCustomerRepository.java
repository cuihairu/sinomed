package com.sinomed.repository;

import com.sinomed.entity.ReviewCustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCustomerRepository extends JpaRepository<ReviewCustomerEntity,Long> {

    /** 按顾客查全部回访计划/记录，回访日倒序（首条即最近一次安排） */
    List<ReviewCustomerEntity> findByCustomerIdOrderByDayDesc(Long customerId);
}
