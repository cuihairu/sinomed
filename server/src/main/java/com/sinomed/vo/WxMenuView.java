package com.sinomed.vo;

import com.sinomed.entity.WxMenuEntity;
import lombok.Data;

import java.util.Date;

/**
 * 管理端自定义菜单视图（设计稿 §4.3「编辑/草稿」本地半边）：
 * 兼作创建/更新入参与列表出参；两级结构校验、微信协议校验（名字数加权、类型必填字段、容量）在服务层。
 */
@Data
public class WxMenuView {
    public Long id;
    public Long accountId;
    /** 父菜单 id；顶级为空。最多两级（微信协议上限） */
    public Long parentId;
    /** 一级 ≤4 汉字（8 字母等效）、二级 ≤8 汉字（16 字母等效），服务层加权校验 */
    public String name;
    /** view/click/scancode_push/scancode_waitmsg/pic_sysphoto/pic_photo_or_album/pic_weixin/
     *  location_select/view_miniprogram；一级仅 view/click */
    public String menuType;
    /** view 型必填（http/https） */
    public String url;
    /** 素材类菜单的 media_id（wx_materials） */
    public String mediaId;
    /** view_miniprogram 必填 */
    public String miniprogramAppid;
    public String miniprogramPagepath;
    /** click 型必填（回调透传） */
    public String eventKey;
    /** 展示顺序，缺省 0 */
    public Integer sort;
    /** 1 启用 / 0 草稿（草稿不推送微信），缺省 1 */
    public Integer enabled;
    public Date createTime;
    public Date updateTime;

    public static WxMenuView from(WxMenuEntity entity) {
        WxMenuView view = new WxMenuView();
        view.id = entity.getId();
        view.accountId = entity.getAccountId();
        view.parentId = entity.getParentId();
        view.name = entity.getName();
        view.menuType = entity.getMenuType();
        view.url = entity.getUrl();
        view.mediaId = entity.getMediaId();
        view.miniprogramAppid = entity.getMiniprogramAppid();
        view.miniprogramPagepath = entity.getMiniprogramPagepath();
        view.eventKey = entity.getEventKey();
        view.sort = entity.getSort();
        view.enabled = entity.getEnabled();
        view.createTime = entity.getCreateTime();
        view.updateTime = entity.getUpdateTime();
        return view;
    }

    /** 入参转实体（校验与 normalize 在服务层，不在这里兜底默认值） */
    public WxMenuEntity toEntity() {
        WxMenuEntity entity = new WxMenuEntity();
        entity.setId(id);
        entity.setAccountId(accountId);
        entity.setParentId(parentId);
        entity.setName(name);
        entity.setMenuType(menuType);
        entity.setUrl(url);
        entity.setMediaId(mediaId);
        entity.setMiniprogramAppid(miniprogramAppid);
        entity.setMiniprogramPagepath(miniprogramPagepath);
        entity.setEventKey(eventKey);
        entity.setSort(sort);
        entity.setEnabled(enabled);
        return entity;
    }
}
