package com.sinomed.repository;

import com.sinomed.entity.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<ItemEntity,Long>, JpaSpecificationExecutor<ItemEntity> {

    /** Kiosk 浏览：上架项按 sort、name 升序 */
    List<ItemEntity> findByEnabledOrderBySortAscNameAsc(Integer enabled);

    /** 按名称取卡项（name 唯一；种子数据自然键） */
    Optional<ItemEntity> findByName(String name);
}
