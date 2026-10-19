package com.sinomed.repository;

import com.sinomed.entity.WxMenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WxMenuRepository extends JpaRepository<WxMenuEntity, Long> {

    /** 该账号的菜单（两级按 sort 序），供第二期菜单管理与推送使用 */
    List<WxMenuEntity> findAllByAccountIdOrderBySortAsc(Long accountId);

    /** 管理端列表：sort 升序、id 兜底（同序号按创建序稳定展示） */
    List<WxMenuEntity> findAllByAccountIdOrderBySortAscIdAsc(Long accountId);

    List<WxMenuEntity> findAllByParentId(Long parentId);

    List<WxMenuEntity> findAllByAccountIdAndParentIdIsNull(Long accountId);
}
