package com.sinomed.service.impl;

import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.OrderItemEntity;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.OrderItemRepository;
import com.sinomed.repository.OrderRepository;
import com.sinomed.service.OrderService;
import com.sinomed.vo.OrderSummaryView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ItemRepository itemRepository;
    /**
     * 根据订单的id查询订单
     *
     * @param id
     */
    @Override
    @Cacheable("OrderEntity")
    public Optional<OrderEntity> findById(Long id) {
        return orderRepository.findById(id);
    }

    /**
     * 保存订单
     *
     * @param orderEntity
     */
    @Override
    public OrderEntity save(OrderEntity orderEntity) {
        Long id = orderEntity.getId();
        if (id != null){
            Optional<OrderEntity> byId = orderRepository.findById(id);
            if (!byId.isEmpty()){
                orderEntity.setCreateTime(byId.get().getCreateTime());
                orderEntity.setUpdateTime(byId.get().getUpdateTime());
            }
        }
        return orderRepository.save(orderEntity);
    }

    /**
     * 根据订单的id删除订单（连同明细行）
     *
     * @param id
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        orderItemRepository.deleteByOrderId(id);
        orderRepository.deleteById(id);
    }

    /**
     * 管理端分页；status 为空查全部；pending=true 只看待结算（状态 0 已下单 / 1 已确认）；
     * 按创建时间倒序（原型列表新单在前），同秒按 id 倒序保证稳定
     */
    @Override
    public Page<OrderEntity> findPage(Integer status, Boolean pending, Pageable pageable) {
        Pageable sorted = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createTime").and(Sort.by(Sort.Direction.DESC, "id")))
                : pageable;
        if (Boolean.TRUE.equals(pending)) {
            return orderRepository.findByStatusIn(List.of(0, 1), sorted);
        }
        if (status == null) {
            return orderRepository.findAll(sorted);
        }
        return orderRepository.findByStatus(status, sorted);
    }

    /**
     * 状态流转：只允许 0→1、1→2 正向与 0/1→9 取消
     */
    @Override
    public OrderEntity updateStatus(Long id, Integer status) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + id));
        Integer current = order.getStatus();
        boolean allowed = (current == null || current == 0) && (status == 1 || status == 9)
                || current != null && current == 1 && (status == 2 || status == 9);
        if (!allowed) {
            throw new IllegalArgumentException("订单状态不允许从 " + current + " 流转到 " + status);
        }
        order.setStatus(status);
        return orderRepository.save(order);
    }

    /**
     * 前台建单：卡项须存在且启用，价格取卡项现价快照，落 status=0 待接待，并写一行明细
     */
    @Override
    public OrderEntity create(Long customerId, Long itemId, Long staffId) {
        ItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("卡项不存在：" + itemId));
        if (item.getEnabled() == null || item.getEnabled() != 1) {
            throw new IllegalArgumentException("卡项已下架：" + item.getName());
        }
        OrderEntity order = new OrderEntity();
        order.setUserId(customerId);
        order.setItemId(item.getId());
        order.setStaffId(staffId);
        order.setStatus(0);
        order.setPrice(item.getPrice());
        OrderEntity saved = orderRepository.save(order);
        saveLine(saved.getId(), item, 1);
        return saved;
    }

    /**
     * 多品项建单：逐行校验、价格快照与合计，落一行订单 + N 行明细
     */
    @Override
    @Transactional
    public OrderEntity createWithLines(Long customerId, List<Line> lines, Long staffId) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("明细行不能为空");
        }
        OrderEntity order = new OrderEntity();
        order.setUserId(customerId);
        order.setStaffId(staffId);
        order.setStatus(0);
        List<OrderItemEntity> rows = new ArrayList<>();
        int total = 0;
        Long firstItemId = null;
        for (Line line : lines) {
            if (line.itemId() == null) {
                throw new IllegalArgumentException("明细行缺卡项 id");
            }
            if (line.qty() == null || line.qty() < 1) {
                throw new IllegalArgumentException("明细行数量须 ≥1");
            }
            ItemEntity item = itemRepository.findById(line.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("卡项不存在：" + line.itemId()));
            if (item.getEnabled() == null || item.getEnabled() != 1) {
                throw new IllegalArgumentException("卡项已下架：" + item.getName());
            }
            int subtotal = (item.getPrice() == null ? 0 : item.getPrice()) * line.qty();
            total += subtotal;
            if (firstItemId == null) {
                firstItemId = item.getId();
            }
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setItemId(item.getId());
            itemEntity.setName(item.getName());
            itemEntity.setPrice(item.getPrice());
            itemEntity.setQty(line.qty());
            itemEntity.setSubtotal(subtotal);
            rows.add(itemEntity);
        }
        order.setItemId(firstItemId);
        order.setPrice(total);
        OrderEntity saved = orderRepository.save(order);
        for (OrderItemEntity itemEntity : rows) {
            itemEntity.setOrderId(saved.getId());
            orderItemRepository.save(itemEntity);
        }
        return saved;
    }

    /**
     * 整单备注：≤200 字，空传清空
     */
    @Override
    @Transactional
    public OrderEntity updateRemark(Long id, String remark) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + id));
        if (remark != null && remark.length() > 200) {
            throw new IllegalArgumentException("备注最长 200 字");
        }
        order.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        return orderRepository.save(order);
    }

    /**
     * 按顾客汇总消费：只统计已完成（status=2）的订单，价格空视为 0
     */
    @Override
    public OrderSummaryView summaryByCustomer(Long customerId) {
        List<OrderEntity> done = orderRepository.findByUserIdAndStatus(customerId, 2);
        long amount = done.stream().mapToLong(order -> order.getPrice() == null ? 0L : order.getPrice()).sum();
        return OrderSummaryView.builder()
                .orders((long) done.size())
                .amount(amount)
                .build();
    }

    /** 明细行落库（名称/单价为下单时刻快照） */
    private void saveLine(Long orderId, ItemEntity item, int qty) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        itemEntity.setOrderId(orderId);
        itemEntity.setItemId(item.getId());
        itemEntity.setName(item.getName());
        itemEntity.setPrice(item.getPrice());
        itemEntity.setQty(qty);
        itemEntity.setSubtotal((item.getPrice() == null ? 0 : item.getPrice()) * qty);
        orderItemRepository.save(itemEntity);
    }
}
