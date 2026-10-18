package com.sinomed.service;

import com.sinomed.entity.ItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ItemService {
    /**
     * 根据项目的id查询项目
     * */
    Optional<ItemEntity> findById(Long id);

    /**
     * 分页过滤：name 包含（不区分大小写）、price 精确、category 精确，均可为空（空即不过滤）
     */
    Page<ItemEntity> findAllByPage(String name, Integer price, Integer category, Pageable pageable);

    Optional<ItemEntity> findByName(String name);
    /**
     * 保存项目
     * */
    ItemEntity save(ItemEntity itemEntity);
    /**
     * 根据项目的id删除项目
     * */
    void deleteById(Long id);
}
