package com.sinomed.vo;

import com.sinomed.entity.WxMaterialEntity;
import lombok.Data;

import java.util.Date;

/**
 * 管理端素材台账只读视图（设计稿 §4.5「供管理端查看」）：台账行 + 本地副本标记（localPath 非空=可取图）。
 * 无密钥列；url 为微信侧地址（临时素材有时效，仅作记录）。
 */
@Data
public class WxMaterialView {
    public Long id;
    public Long accountId;
    public String mediaId;
    public String materialType;
    public String name;
    public String url;
    /** 本地落盘相对路径；为空表示仅远端记录（下载失败/超限或未落盘） */
    public String localPath;
    public Long size;
    public String mimeType;
    public Date createTime;
    public Date updateTime;

    public static WxMaterialView from(WxMaterialEntity entity) {
        WxMaterialView view = new WxMaterialView();
        view.id = entity.getId();
        view.accountId = entity.getAccountId();
        view.mediaId = entity.getMediaId();
        view.materialType = entity.getMaterialType();
        view.name = entity.getName();
        view.url = entity.getUrl();
        view.localPath = entity.getLocalPath();
        view.size = entity.getSize();
        view.mimeType = entity.getMimeType();
        view.createTime = entity.getCreateTime();
        view.updateTime = entity.getUpdateTime();
        return view;
    }
}
