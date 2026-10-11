package com.sinomed.service;

import com.sinomed.entity.ReviewCustomerEntity;
import com.sinomed.entity.ReviewEntity;
import com.sinomed.entity.ReviewStaffEntity;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface ReviewService {
    ReviewStaffEntity saveStaffView(ReviewStaffEntity reviewStaffEntity);
    ReviewCustomerEntity saveCustomerView(ReviewCustomerEntity reviewStaffEntity);

    ReviewEntity saveView(ReviewEntity reviewEntity);
    List<ReviewStaffEntity> findStaffViewByDate(Date date);
    Optional<ReviewEntity> findViewByDate(Date date);
    List<ReviewCustomerEntity> findCustomerViewByDate(Date date);
    List<ReviewCustomerEntity> findCustomerViewsByCustomer(Long customerId);

    boolean deleteStaffViewById(Long id);
    boolean deleteCustomerViewById(Long id);

    /** 按 id 查每日复盘总结 */
    Optional<ReviewEntity> findViewById(Long id);

    /** 按 id 删除每日复盘总结（含联出的员工/顾客复盘，调用方保证存在） */
    void deleteViewById(Long id);
}
