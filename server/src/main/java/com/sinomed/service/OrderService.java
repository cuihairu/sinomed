package com.sinomed.service;

import com.sinomed.entity.OrderEntity;
import com.sinomed.vo.OrderSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface OrderService {
    /**
     * 根据订单的id查询订单
     * */
    Optional<OrderEntity> findById(Long id);
    /**
     * 保存订单
     * */
    OrderEntity save(OrderEntity orderEntity);
    /**
     * 根据订单的id删除订单
     * */
    void deleteById(Long id);
    /**
     * 管理端分页；status 为空查全部；pending=true 只看待结算（状态 0 已下单 / 1 已确认）
     * */
    Page<OrderEntity> findPage(Integer status, Boolean pending, Pageable pageable);
    /**
     * 状态流转：只允许 0→1、1→2 正向与 0/1→9 取消；非法流转抛 IllegalArgumentException
     * */
    OrderEntity updateStatus(Long id, Integer status);

    /**
     * 前台建单：按卡项现价快照落单（status=0 待接待，staffId 记录建单人）；卡项须存在且启用
     * */
    OrderEntity create(Long customerId, Long itemId, Long staffId);

    /** 建单明细行：itemId + qty（≥1） */
    record Line(Long itemId, Integer qty) {}

    /**
     * 多品项建单：逐行校验卡项存在且启用、qty≥1，落一行订单（itemId=首行、price=各行小计合计）
     * 并写 order_item 明细行（名称/单价快照）；staffId 记录建单人
     * */
    OrderEntity createWithLines(Long customerId, List<Line> lines, Long staffId);

    /**
     * 整单备注：≤200 字，可空（清空传 null/空串）
     * */
    OrderEntity updateRemark(Long id, String remark);

    /**
     * 按顾客汇总消费：累计消费=已完成（status=2）订单价格合计
     * */
    OrderSummaryView summaryByCustomer(Long customerId);
}
