package com.sinomed.service.impl;

import com.sinomed.entity.HerbEntity;
import com.sinomed.repository.HerbRepository;
import com.sinomed.service.DoseService;
import com.sinomed.vo.DoseResultView;
import com.sinomed.vo.PrescriptionItemView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 剂量审方实现：字典精确同名匹配（与计价同口径，不做包含猜测），weight 严格大于 dose_max 才提示。
 * 上限来自药材字典 dose_max，可空=不预警；未收录药名如实进 unmatched 无参照。
 */
@Service
@RequiredArgsConstructor
public class DoseServiceImpl implements DoseService {

    private final HerbRepository herbRepository;

    @Override
    public DoseResultView check(List<PrescriptionItemView> herbs) {
        List<PrescriptionItemView> named = new ArrayList<>();
        if (herbs != null) {
            for (PrescriptionItemView herb : herbs) {
                if (herb != null && herb.getHerb() != null && !herb.getHerb().isBlank()) {
                    named.add(herb);
                }
            }
        }
        if (named.isEmpty()) {
            throw new IllegalArgumentException("药材名单不能为空");
        }

        Map<String, HerbEntity> dict = herbRepository.findByNameIn(named.stream()
                        .map(h -> h.getHerb().trim()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(HerbEntity::getName, Function.identity()));

        List<DoseResultView.Finding> findings = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        int limited = 0;
        for (PrescriptionItemView herb : named) {
            String name = herb.getHerb().trim();
            HerbEntity entry = dict.get(name);
            if (entry == null) {
                unmatched.add(name);
                continue;
            }
            Double limit = entry.getDoseMax();
            if (limit == null || limit <= 0) {
                // 未设上限不预警（可空=不预警）
                continue;
            }
            limited++;
            double weight = herb.getWeight() == null ? 0 : herb.getWeight();
            if (weight > limit) {
                findings.add(DoseResultView.Finding.builder()
                        .herb(name)
                        .weight(weight)
                        .limit(limit)
                        .build());
            }
        }
        return DoseResultView.builder()
                .checked(named.size())
                .limited(limited)
                .findings(findings)
                .unmatched(unmatched)
                .build();
    }
}
