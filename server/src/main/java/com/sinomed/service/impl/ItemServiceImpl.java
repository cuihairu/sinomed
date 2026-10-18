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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.LinkedList;
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
     * 分页过滤：name 包含（不区分大小写）、price 精确、category 精确，均可为空（空即不过滤）。
     * 空条件走全量分页，避免全 null 探针把列默认带进查询条件（QBE 陷阱，见 ItemEntity 注释）。
     */
    @Override
    public Page<ItemEntity> findAllByPage(String name, Integer price, Integer category, Pageable pageable) {
        if ((name == null || name.trim().isEmpty()) && price == null && category == null) {
            return itemRepository.findAll(pageable);
        }
        Specification<ItemEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (name != null && !name.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name").as(String.class)),
                        "%" + name.trim().toLowerCase() + "%"));
            }
            if (price != null) {
                predicates.add(criteriaBuilder.equal(root.get("price").as(Integer.class), price));
            }
            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").as(Integer.class), category));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return itemRepository.findAll(spec, pageable);
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
