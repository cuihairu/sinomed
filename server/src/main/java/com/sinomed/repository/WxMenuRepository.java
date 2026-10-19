package com.sinomed.repository;

import com.sinomed.entity.WxMenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WxMenuRepository extends JpaRepository<WxMenuEntity, Long> {

    /** 该账号的菜单（两级按 sort 序），供第二期菜单管理与推送使用 */
    List<WxMenuEntity> findAllByAccountIdOrderBySortAsc(Long accountId);
}
