package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 公众号素材台账（wx_materials，设计稿 §4.5，第一期建表不做上传/管理 UI）：
 * 第一期只做消息内临时素材的拉取落日志（收到图片消息时下载 PicUrl 到 local_path，供管理端查看）；
 * url 为微信返回的访问地址（临时素材有时效，仅记录不依赖），素材上传与引用属第二期。
 * 复用 ads 上传目录同款挂载思路（sinomed.ads.upload-dir，application.yml）。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "wx_materials", uniqueConstraints = @UniqueConstraint(
        name = "uk_wx_material_account_media", columnNames = {"account_id", "media_id"}))
@EntityListeners(AuditingEntityListener.class)
public class WxMaterialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 微信侧 media_id（唯一键之一） */
    @Column(name = "media_id")
    private String mediaId;

    /** image/voice/video/news（图文）/temp（临时素材） */
    @Column(name = "material_type")
    private String materialType;

    /** 素材名 */
    @Column(name = "name")
    private String name;

    /** 微信返回的访问 URL（临时素材有时效，仅记录不依赖） */
    @Column(name = "url")
    private String url;

    /** 本仓本地落盘路径 */
    @Column(name = "local_path")
    private String localPath;

    /** 文件元信息 */
    @Column(name = "size")
    private Long size;

    @Column(name = "mime_type")
    private String mimeType;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
