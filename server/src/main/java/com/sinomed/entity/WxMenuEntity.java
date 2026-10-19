package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 公众号自定义菜单（wx_menus，设计稿 §4.3，第一期建表不建 UI）：草稿态（enabled=0）不推送微信，
 * 菜单管理与推送属第二期。最多两级（parent_id 指向顶级），menu_type 枚举按微信协议（§4.3）。
 * 菜单名字数限制（一级 ≤4 汉字、二级 ≤8 汉字）与个性化菜单均不做——第一期无菜单服务/入口。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "wx_menus", indexes = {
        @Index(name = "idx_wx_menu_parent", columnList = "account_id, parent_id")
})
@EntityListeners(AuditingEntityListener.class)
public class WxMenuEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 父菜单 id（顶级为空；最多两级，微信协议上限） */
    @Column(name = "parent_id")
    private Long parentId;

    /** 菜单名（微信限制：一级 ≤4 汉字、二级 ≤8 汉字，服务端校验留第二期） */
    @Column(name = "name")
    private String name;

    /** view/click/scancode_push/scancode_waitmsg/pic_sysphoto/pic_photo_or_album/pic_weixin/
     *  location_select/view_miniprogram */
    @Column(name = "menu_type")
    private String menuType;

    /** view 类型跳转 URL */
    @Column(name = "url")
    private String url;

    /** 素材类菜单的 media_id（wx_materials） */
    @Column(name = "media_id")
    private String mediaId;

    /** view_miniprogram 的小程序参数（可指向本仓小程序） */
    @Column(name = "miniprogram_appid")
    private String miniprogramAppid;

    @Column(name = "miniprogram_pagepath")
    private String miniprogramPagepath;

    /** click 类型的事件 key（回调透传，用于路由） */
    @Column(name = "event_key")
    private String eventKey;

    /** 展示顺序 */
    @Column(name = "sort")
    private Integer sort;

    /** 1 启用 / 0 草稿（草稿态不推送微信） */
    @Column(name = "enabled", columnDefinition = "integer default 1")
    private Integer enabled;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
