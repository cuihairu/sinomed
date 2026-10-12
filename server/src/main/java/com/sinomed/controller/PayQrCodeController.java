package com.sinomed.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 到店扫码收款码出图（web.md 口径：不做在线支付、到店结算）。
 * 商户把自己的微信/支付宝收款码图片放 {sinomed.pay.qrcodes-dir}/{wechat|alipay}.png（或 .jpg），
 * 结算台选中对应支付方式时出图给顾客扫；文件缺失即 404，页面如实提示未配置，不做假码。
 * 纯出图：不接支付网关、无支付回调，钱货在系统外完成，系统只按所选方式记账。
 * 需登录（收款码是商户资产；img 标签带不上 Bearer，页面用 fetch+blob 取图，同小票补打口径）。
 */
@RestController
@RequestMapping("/api/v1/pay-qrcode")
@Tag(name = "收款码", description = "到店扫码收款码出图：data/payqrcodes/ 目录放商户码图片即生效，缺图 404")
public class PayQrCodeController {

    /** 方法白名单：微信 / 支付宝（现金与卡项无码；白名单同时封死路径拼接） */
    private static final Set<String> METHODS = Set.of("wechat", "alipay");

    /** 允许的图片扩展名与响应 Content-Type（按扩展名定型，不做魔数嗅探） */
    private static final List<String[]> EXTENSIONS = List.of(
            new String[] {".png", "image/png"},
            new String[] {".jpg", "image/jpeg"},
            new String[] {".jpeg", "image/jpeg"});

    private final Path dir;

    public PayQrCodeController(@Value("${sinomed.pay.qrcodes-dir:data/payqrcodes}") String dir) {
        this.dir = Path.of(dir);
    }

    @Operation(summary = "取商户收款码图片",
            description = "读 {qrcodes-dir}/{method}.png|.jpg|.jpeg，按扩展名给 Content-Type；"
                    + "文件缺失 404（页面如实提示未配置）；method 白名单 wechat/alipay 之外一律 404",
            responses = {
                    @ApiResponse(responseCode = "200", description = "图片字节流"),
                    @ApiResponse(responseCode = "404", description = "未配置（目录无对应图片或方法不在白名单）")})
    @GetMapping("/{method}")
    public ResponseEntity<byte[]> qr(@PathVariable String method) {
        if (!METHODS.contains(method)) {
            return ResponseEntity.notFound().build();
        }
        for (String[] ext : EXTENSIONS) {
            Path file = dir.resolve(method + ext[0]);
            if (Files.isRegularFile(file)) {
                try {
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(ext[1]))
                            .body(Files.readAllBytes(file));
                } catch (IOException e) {
                    // 读取瞬间文件消失/被换走：按未配置处理，页面落「未配置」提示
                    return ResponseEntity.notFound().build();
                }
            }
        }
        return ResponseEntity.notFound().build();
    }
}
