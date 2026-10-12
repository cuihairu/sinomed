package com.sinomed.service.impl;

import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.ItemRepository;
import com.sinomed.service.ItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;

    public ItemServiceImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * 根据项目的id查询项目
     *
     * @param id
     */
    @Override
    @Cacheable("ItemEntity")
    public Optional<ItemEntity> findById(Long id) {
        return itemRepository.findById(id);
    }

    /**
     * @return
     */
    @Override
    public Page<ItemEntity> finAll(ItemEntity itemEntity, Pageable pageable) {
        return itemRepository.findAll(Example.of(itemEntity),pageable);
    }

    public Optional<ItemEntity> findByName(String name) {
        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setName(name);
        return itemRepository.findAll(Example.of(itemEntity)).stream().findFirst();
    }
    /**
     * 保存项目
     *
     * @param itemEntity
     */
    @Override
    @CacheEvict(value = "ItemEntity", key = "#itemEntity.id")
    public ItemEntity save(ItemEntity itemEntity) {
        Long id = itemEntity.getId();
        if (id != null){
            Optional<ItemEntity> byId = itemRepository.findById(id);
            if (!byId.isEmpty()){
                itemEntity.setCreateTime(byId.get().getCreateTime());
                itemEntity.setUpdateTime(byId.get().getUpdateTime());
            }
        }
        return itemRepository.save(itemEntity);
    }

    /**
     * 根据项目的id删除项目
     *
     * @param id
     */
    @Override
    @CacheEvict(value = "ItemEntity", key = "#id")
    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }
}
