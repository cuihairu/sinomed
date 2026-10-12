package com.sinomed.service.impl;

import com.sinomed.entity.HerbEntity;
import com.sinomed.entity.HerbStockLogEntity;
import com.sinomed.repository.HerbRepository;
import com.sinomed.repository.HerbStockLogRepository;
import com.sinomed.service.HerbStockService;
import com.sinomed.util.DateUtil;
import com.sinomed.vo.HerbStockBalanceView;
import com.sinomed.vo.HerbStockLogView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 饮片出入库台账实现：只记流水，当前库存 = 入库合计 - 出库合计；
 * 出库按效期先进先出（FEFO）消耗批次，批次效期用于近期到期预警。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class HerbStockServiceImpl implements HerbStockService {

    public static final int TYPE_IN = 1;
    public static final int TYPE_OUT = 0;
    private static final int DEFAULT_WARN_DAYS = 30;
    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    private final HerbStockLogRepository logRepository;
    private final HerbRepository herbRepository;

    @Override
    @Transactional
    public HerbStockLogView addLog(HerbStockLogView view) {
        if (view == null || view.getHerbId() == null) {
            throw new IllegalArgumentException("药材必填");
        }
        HerbEntity herb = herbRepository.findById(view.getHerbId())
                .orElseThrow(() -> new IllegalArgumentException("药材不存在"));
        if (view.getType() == null || (view.getType() != TYPE_IN && view.getType() != TYPE_OUT)) {
            throw new IllegalArgumentException("流水类型无效：应为 1 入库 / 0 出库");
        }
        if (view.getQuantity() == null || view.getQuantity() <= 0) {
            throw new IllegalArgumentException("数量须为正整数（克）");
        }
        Date expiry = parseExpiry(view.getExpiry());
        if (expiry != null && DateUtil.getZeroTime(expiry).before(DateUtil.getZeroTime(new Date()))) {
            throw new IllegalArgumentException("效期已过期：" + view.getExpiry());
        }
        String supplier = trimOrNull(view.getSupplier());
        if (supplier != null && supplier.length() > 50) {
            throw new IllegalArgumentException("供货方限 50 字内");
        }
        String note = trimOrNull(view.getNote());
        if (note != null && note.length() > 100) {
            throw new IllegalArgumentException("备注限 100 字内");
        }
        if (view.getType() == TYPE_OUT) {
            int stock = stockOf(herb.getId());
            if (view.getQuantity() > stock) {
                throw new IllegalArgumentException("出库数量超过当前库存：" + stock + " 克");
            }
        }

        HerbStockLogEntity entity = new HerbStockLogEntity();
        entity.setHerbId(herb.getId());
        entity.setType(view.getType());
        entity.setQuantity(view.getQuantity());
        entity.setExpiry(view.getType() == TYPE_IN ? expiry : null);
        entity.setSupplier(supplier);
        entity.setNote(note);
        HerbStockLogEntity saved = logRepository.save(entity);
        return HerbStockLogView.fromEntity(saved, herb.getName());
    }

    @Override
    public Page<HerbStockLogView> findPage(Long herbId, Integer type, Pageable pageable) {
        Specification<HerbStockLogEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new LinkedList<>();
            if (herbId != null) {
                predicates.add(cb.equal(root.get("herbId"), herbId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<HerbStockLogEntity> page = logRepository.findAll(spec, pageable);
        Map<Long, String> names = herbNames();
        return page.map(entity -> HerbStockLogView.fromEntity(entity, names.get(entity.getHerbId())));
    }

    @Override
    public List<HerbStockBalanceView> balance(Integer expiryWithinDays) {
        int withinDays = expiryWithinDays == null || expiryWithinDays <= 0 ? DEFAULT_WARN_DAYS : Math.min(expiryWithinDays, 365);
        List<HerbStockLogEntity> logs = logRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        Map<Long, List<HerbStockLogEntity>> byHerb = new HashMap<>();
        logs.forEach(log -> byHerb.computeIfAbsent(log.getHerbId(), key -> new ArrayList<>()).add(log));
        Map<Long, String> names = herbNames();
        Map<Long, HerbEntity> herbs = new HashMap<>();
        herbRepository.findAll().forEach(herb -> herbs.put(herb.getId(), herb));

        List<HerbStockBalanceView> result = new ArrayList<>();
        byHerb.forEach((herbId, herbLogs) -> {
            int stock = 0;
            // 批次按效期升序（无效期排最后），出库先进先出消耗
            TreeMap<Long, Integer> lots = new TreeMap<>();
            for (HerbStockLogEntity log : herbLogs) {
                if (log.getType() == TYPE_IN) {
                    stock += log.getQuantity();
                    long key = log.getExpiry() == null ? Long.MAX_VALUE : log.getExpiry().getTime();
                    lots.merge(key, log.getQuantity(), Integer::sum);
                } else {
                    stock -= log.getQuantity();
                    int remain = log.getQuantity();
                    while (remain > 0 && !lots.isEmpty()) {
                        Map.Entry<Long, Integer> first = lots.firstEntry();
                        int take = Math.min(remain, first.getValue());
                        remain -= take;
                        if (take == first.getValue()) {
                            lots.pollFirstEntry();
                        } else {
                            lots.put(first.getKey(), first.getValue() - take);
                        }
                    }
                }
            }
            result.add(balanceView(herbId, names.get(herbId), stock, lots, withinDays, herbs.get(herbId)));
        });
        result.sort(Comparator.comparing(HerbStockBalanceView::getName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    @Transactional
    public HerbStockLogView deleteById(Long id) {
        HerbStockLogEntity log = logRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("流水不存在"));
        HerbStockLogView view = HerbStockLogView.fromEntity(log, herbNames().get(log.getHerbId()));
        logRepository.delete(log);
        return view;
    }

    private HerbStockBalanceView balanceView(Long herbId, String name, int stock,
                                             TreeMap<Long, Integer> lots, int withinDays, HerbEntity herb) {
        Long expiryInDays = null;
        String nextExpiry = null;
        for (Map.Entry<Long, Integer> lot : lots.entrySet()) {
            if (lot.getKey() == Long.MAX_VALUE || lot.getValue() <= 0) {
                continue;
            }
            Date expiry = new Date(lot.getKey());
            nextExpiry = new SimpleDateFormat("yyyy-MM-dd").format(expiry);
            expiryInDays = (DateUtil.getZeroTime(expiry).getTime() - DateUtil.getZeroTime(new Date()).getTime()) / MILLIS_PER_DAY;
            break;
        }
        boolean warn = expiryInDays != null && expiryInDays <= withinDays;
        Integer stockMin = herb == null ? null : herb.getStockMin();
        Integer stockMax = herb == null ? null : herb.getStockMax();
        boolean warnStock = (stockMin != null && stock < stockMin) || (stockMax != null && stock > stockMax);
        return HerbStockBalanceView.builder()
                .herbId(herbId)
                .name(name)
                .stock(stock)
                .nextExpiry(nextExpiry)
                .expiryInDays(expiryInDays)
                .warnExpiry(warn)
                .stockMin(stockMin)
                .stockMax(stockMax)
                .warnStock(warnStock)
                .build();
    }

    private int stockOf(Long herbId) {
        int stock = 0;
        for (HerbStockLogEntity log : logRepository.findAll()) {
            if (!herbId.equals(log.getHerbId())) {
                continue;
            }
            stock += log.getType() == TYPE_IN ? log.getQuantity() : -log.getQuantity();
        }
        return stock;
    }

    private Map<Long, String> herbNames() {
        Map<Long, String> names = new HashMap<>();
        herbRepository.findAll().forEach(herb -> names.put(herb.getId(), herb.getName()));
        return names;
    }

    private Date parseExpiry(String text) {
        String trimmed = trimOrNull(text);
        if (trimmed == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        try {
            return format.parse(trimmed);
        } catch (ParseException e) {
            throw new IllegalArgumentException("效期格式无效：" + text + "（应为 yyyy-MM-dd）");
        }
    }

    private String trimOrNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
