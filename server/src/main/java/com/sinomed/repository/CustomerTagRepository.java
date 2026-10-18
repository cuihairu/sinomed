package com.sinomed.repository;

import com.sinomed.entity.CustomerTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerTagRepository extends JpaRepository<CustomerTagEntity,Long>{

    /** 档案标签：新加的在前 */
    List<CustomerTagEntity> findByCustomerIdOrderByIdDesc(Long customerId);

    /** 去重校验：同顾客同标签唯一 */
    Optional<CustomerTagEntity> findByCustomerIdAndTag(Long customerId, String tag);
}
