package com.sinomed.service.impl;

import com.sinomed.entity.HerbEntity;
import com.sinomed.repository.HerbRepository;
import com.sinomed.service.HerbService;
import com.sinomed.vo.HerbView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 药材字典维护实现。
 */
@Service
@RequiredArgsConstructor
public class HerbServiceImpl implements HerbService {

    private final HerbRepository herbRepository;

    @Override
    @Transactional
    public HerbEntity create(HerbView view) {
        String name = normalizeName(view.getName());
        validatePrice(view);
        validateDoseMax(view);
        if (herbRepository.existsByName(name)) {
            throw new IllegalArgumentException("药材已收录：" + name);
        }
        HerbEntity entity = new HerbEntity();
        entity.setName(name);
        entity.setPrice(view.getPrice());
        entity.setStockMin(view.getStockMin());
        entity.setStockMax(view.getStockMax());
        entity.setDoseMax(view.getDoseMax());
        return herbRepository.save(entity);
    }

    @Override
    @Transactional
    public HerbEntity update(HerbView view) {
        if (view.getId() == null) {
            throw new IllegalArgumentException("药材id不能为空");
        }
        HerbEntity entity = herbRepository.findById(view.getId())
                .orElseThrow(() -> new IllegalArgumentException("药材不存在：" + view.getId()));
        String name = normalizeName(view.getName());
        validatePrice(view);
        validateDoseMax(view);
        if (herbRepository.existsByNameAndIdNot(name, entity.getId())) {
            throw new IllegalArgumentException("药材已收录：" + name);
        }
        entity.setName(name);
        entity.setPrice(view.getPrice());
        // 上下限随更新整量覆盖（传 null 即清除）
        entity.setStockMin(view.getStockMin());
        entity.setStockMax(view.getStockMax());
        entity.setDoseMax(view.getDoseMax());
        return herbRepository.save(entity);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        herbRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("药材不存在：" + id));
        herbRepository.deleteById(id);
    }

    @Override
    public Page<HerbEntity> findPage(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return herbRepository.findAll(pageable);
        }
        return herbRepository.findByNameContaining(keyword.trim(), pageable);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("药材名不能为空");
        }
        return name.trim();
    }

    private void validatePrice(HerbView view) {
        if (view.getPrice() == null || view.getPrice() <= 0) {
            throw new IllegalArgumentException("每克价格必须大于 0");
        }
    }

    private void validateDoseMax(HerbView view) {
        // 上限可空=不预警；填了就必须是正数，0 与负数会让「超上限」永远命中/永不命中
        if (view.getDoseMax() != null && view.getDoseMax() <= 0) {
            throw new IllegalArgumentException("单剂上限必须大于 0");
        }
    }
}
