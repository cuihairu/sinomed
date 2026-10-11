package com.sinomed.service.impl;

import com.sinomed.entity.TherapyTemplateEntity;
import com.sinomed.repository.TherapyTemplateRepository;
import com.sinomed.service.TherapyTemplateService;
import com.sinomed.vo.TherapyTemplateView;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 外治处方模板实现：模板名唯一（1–20 字）、方法必填（≤10 字）；
 * 取穴/疗程/注意事项各限 100 字可空。创建/更新时间由审计维护。
 * 模板只存建议值，接诊页套用后随单自由改（不写回模板）。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TherapyTemplateServiceImpl implements TherapyTemplateService {

    private final TherapyTemplateRepository templateRepository;

    @Override
    @Transactional
    public TherapyTemplateEntity save(TherapyTemplateView view) {
        TherapyTemplateEntity template = new TherapyTemplateEntity();
        applyGuards(view, null);
        template.setName(view.getName().trim());
        template.setMethod(view.getMethod().trim());
        template.setTarget(view.getTarget());
        template.setCourse(view.getCourse());
        template.setNote(view.getNote());
        template.setEnabled(view.getEnabled() == null ? 1 : view.getEnabled());
        return templateRepository.save(template);
    }

    @Override
    @Transactional
    public TherapyTemplateEntity update(TherapyTemplateView view) {
        if (view == null || view.getId() == null) {
            throw new IllegalArgumentException("模板id不能为空");
        }
        TherapyTemplateEntity template = templateRepository.findById(view.getId())
                .orElseThrow(() -> new IllegalArgumentException("模板不存在：" + view.getId()));
        applyGuards(view, template.getId());
        template.setName(view.getName().trim());
        template.setMethod(view.getMethod().trim());
        template.setTarget(view.getTarget());
        template.setCourse(view.getCourse());
        template.setNote(view.getNote());
        template.setEnabled(view.getEnabled() == null ? 1 : view.getEnabled());
        return templateRepository.save(template);
    }

    @Override
    public TherapyTemplateView findById(Long id) {
        return TherapyTemplateView.FromEntity(templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在：" + id)));
    }

    @Override
    public Page<TherapyTemplateEntity> findPage(String name, String method, Pageable pageable) {
        Specification<TherapyTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + name.trim() + "%"));
            }
            if (method != null && !method.isBlank()) {
                predicates.add(cb.equal(root.get("method"), method.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, pageable);
    }

    @Override
    public List<TherapyTemplateView> findEnabled() {
        return templateRepository.findByEnabledOrderByNameAsc(1).stream()
                .map(TherapyTemplateView::FromEntity).toList();
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在：" + id));
        templateRepository.deleteById(id);
    }

    /** 公共校验：名称/方法必填与长度、唯一性（更新时豁免自身）、可空字段长度 */
    private void applyGuards(TherapyTemplateView view, Long selfId) {
        if (view == null || view.getName() == null || view.getName().isBlank()) {
            throw new IllegalArgumentException("外治模板名不能为空");
        }
        if (view.getName().trim().length() > 20) {
            throw new IllegalArgumentException("外治模板名限 20 字内");
        }
        if (view.getMethod() == null || view.getMethod().isBlank()) {
            throw new IllegalArgumentException("外治方法必填");
        }
        if (view.getMethod().trim().length() > 10) {
            throw new IllegalArgumentException("外治方法限 10 字内");
        }
        templateRepository.findByName(view.getName().trim())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("外治模板名已存在：" + view.getName().trim());
                });
        if (view.getTarget() != null && view.getTarget().trim().length() > 100) {
            throw new IllegalArgumentException("取穴或部位限 100 字内");
        }
        if (view.getCourse() != null && view.getCourse().trim().length() > 100) {
            throw new IllegalArgumentException("疗程限 100 字内");
        }
        if (view.getNote() != null && view.getNote().trim().length() > 100) {
            throw new IllegalArgumentException("注意事项限 100 字内");
        }
    }
}
