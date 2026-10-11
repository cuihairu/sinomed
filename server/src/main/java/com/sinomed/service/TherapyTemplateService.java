package com.sinomed.service;

import com.sinomed.entity.TherapyTemplateEntity;
import com.sinomed.vo.TherapyTemplateView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 外治处方模板服务：模板库 CRUD + 接诊页「套用外治模板」取数入口。
 */
public interface TherapyTemplateService {

    TherapyTemplateEntity save(TherapyTemplateView view);

    TherapyTemplateEntity update(TherapyTemplateView view);

    TherapyTemplateView findById(Long id);

    Page<TherapyTemplateEntity> findPage(String name, String method, Pageable pageable);

    List<TherapyTemplateView> findEnabled();

    void deleteById(Long id);
}
