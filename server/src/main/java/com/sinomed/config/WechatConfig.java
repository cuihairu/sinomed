package com.sinomed.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 微信小程序服务端调用装配：jscode2session 走独立 RestTemplate bean，
 * 便于测试用 MockRestServiceServer 绑定替身（不打真实微信）。
 */
@Configuration
public class WechatConfig {

    @Bean
    public RestTemplate wechatRestTemplate() {
        return new RestTemplate();
    }
}
