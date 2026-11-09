package com.sinomed.service;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.SmsCodeEntity;
import com.sinomed.entity.WxBindingLogEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.SmsCodeRepository;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxBindingLogRepository;
import com.sinomed.repository.WxFanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 粉丝↔顾客绑定（设计稿第一期交付物 5，复用 Kiosk /openid-bind「手机号 + 一次性短信验证码」双因子口径）：
 * 取码（复用 sms_codes 一次性 5 分钟验证码桩）、绑定校验（占用/冲突/防爆破烧码）、管理端解绑、
 * 流水顾客姓名解析。每次尝试（含拒绝）落 wx_binding_logs 审计（§4.8，不静默）。
 */
@Component
public class WxFanBindService {

    private final WxAccountRepository accountRepository;
    private final WxFanRepository fanRepository;
    private final SmsCodeRepository smsCodeRepository;
    private final CustomerRepository customerRepository;
    private final WxBindingLogRepository bindingLogRepository;

    /** 模块总开关（env WX_OA_ENABLED）；缺省 false=取码/绑定拒绝并审计 */
    private final boolean enabled;

    /** 绑定短信验证码有效期（分钟），与 Kiosk 短信通道同口径 */
    private static final long BIND_SMS_TTL_MINUTES = 5;

    /** 验证码连续错试上限：达上限烧码作废（防 6 位数字暴力枚举），须重新取码 */
    private static final int SMS_CODE_MAX_ATTEMPTS = 5;

    public WxFanBindService(WxAccountRepository accountRepository,
                            WxFanRepository fanRepository,
                            SmsCodeRepository smsCodeRepository,
                            CustomerRepository customerRepository,
                            WxBindingLogRepository bindingLogRepository,
                            @Value("${sinomed.official-account.enabled:false}") boolean enabled) {
        this.accountRepository = accountRepository;
        this.fanRepository = fanRepository;
        this.smsCodeRepository = smsCodeRepository;
        this.customerRepository = customerRepository;
        this.bindingLogRepository = bindingLogRepository;
        this.enabled = enabled;
    }

    /**
     * 绑定取码（粉丝 H5 侧）：复用 Kiosk 短信通道桩（sms_codes，一次性、5 分钟有效），
     * 无真实短信网关、发码只落库与日志；模块关/账号停用拒绝并审计（不静默）。
     */
    public String issueBindCode(long accountId, String phone) {
        if (!enabled) {
            auditBinding(accountId, null, phone, null, "disabled");
            throw new IllegalArgumentException("公众号模块未开启，无法发送验证码");
        }
        if (accountRepository.findById(accountId).filter(a -> a.getEnabled() != null && a.getEnabled() == 1).isEmpty()) {
            auditBinding(accountId, null, phone, null, "account_missing");
            throw new IllegalArgumentException("公众号账号不存在或已停用，无法发送验证码");
        }
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不对：应为 1 开头的 11 位数字");
        }
        SmsCodeEntity code = new SmsCodeEntity();
        code.setPhone(phone);
        code.setCode(String.valueOf(100000 + ThreadLocalRandom.current().nextInt(900000)));
        code.setUsed(0);
        code.setAttempts(0);
        code.setExpiresAt(new Date(System.currentTimeMillis() + BIND_SMS_TTL_MINUTES * 60_000L));
        smsCodeRepository.save(code);
        // 无短信网关：演示环境的送达方式（测试用 SmsCodeRepository 直接取码）
        org.slf4j.LoggerFactory.getLogger(WxFanBindService.class)
                .info("SMS_CODE_DEMO phone={} code={}", phone, code.getCode());
        return "验证码已发送（5 分钟内有效，演示环境请向店员索取）";
    }

    /**
     * 粉丝↔顾客绑定（设计稿第一期交付物 5，双因子口径同 Kiosk）：
     * 校验通过写 wx_fans.customer_id，每次尝试（含拒绝）落 wx_binding_logs（§4.8）。
     * 粉丝行不存在时补建（subscribe=1）：菜单/H5 入口仅关注用户可见，绑定价值大于粉丝计数精度，
     * 管理端可人工改（已代决）。顾客冲突按公众号内部语义判（wx_fans 同账号占位），
     * 与 customer.openid 的小程序绑定互不影响。
     */
    public String bind(long accountId, String openid, String phone, String smsCode) {
        if (!enabled) {
            auditBinding(accountId, openid, phone, null, "disabled");
            throw new IllegalArgumentException("公众号模块未开启，无法绑定");
        }
        if (accountRepository.findById(accountId).filter(a -> a.getEnabled() != null && a.getEnabled() == 1).isEmpty()) {
            auditBinding(accountId, openid, phone, null, "account_missing");
            throw new IllegalArgumentException("公众号账号不存在或已停用，无法绑定");
        }
        if (openid == null || openid.isBlank()) {
            auditBinding(accountId, openid, phone, null, "missing_openid");
            throw new IllegalArgumentException("需要 openid（网页授权登录态）才能绑定");
        }
        if (phone == null || phone.isBlank() || smsCode == null || smsCode.isBlank()) {
            auditBinding(accountId, openid, phone, null, "missing_code");
            throw new IllegalArgumentException("需要手机号与短信验证码才能绑定");
        }
        if (!phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不对：应为 1 开头的 11 位数字");
        }
        // 自证身份与查卡 sms_verified 同一通道：一次性验证码，消耗即绑定凭据（同 Kiosk 顺序：先验码后查档案）
        consumeBindSmsCode(accountId, openid, phone, smsCode);
        CustomerEntity customer = customerRepository.findByPhone(phone).orElse(null);
        if (customer == null) {
            auditBinding(accountId, openid, phone, null, "no_customer");
            throw new IllegalArgumentException("该手机号无顾客档案，请到前台建档");
        }
        Optional<WxFanEntity> occupied = fanRepository.findByAccountIdAndCustomerId(accountId, customer.getId());
        if (occupied.isPresent() && !occupied.get().getOpenid().equals(openid)) {
            auditBinding(accountId, openid, phone, customer.getId(), "conflict");
            throw new IllegalArgumentException("该顾客档案已绑定其他粉丝，请到前台处理");
        }
        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, openid).orElseGet(() -> {
            WxFanEntity created = new WxFanEntity();
            created.setAccountId(accountId);
            created.setOpenid(openid);
            created.setSubscribe(1);
            return fanRepository.save(created);
        });
        if (fan.getCustomerId() != null && !fan.getCustomerId().equals(customer.getId())) {
            auditBinding(accountId, openid, phone, customer.getId(), "bound_other");
            throw new IllegalArgumentException("该粉丝已绑定其他顾客档案，请先解绑或到前台处理");
        }
        fan.setCustomerId(customer.getId());
        fanRepository.save(fan);
        auditBinding(accountId, openid, phone, customer.getId(), "ok");
        if (occupied.isPresent()) {
            return "该粉丝已绑定此档案，无需重复绑定";
        }
        return "已绑定到「" + customer.getName() + "」的顾客档案";
    }

    /** 管理端解绑（设计稿 §4.7：清 customer_id + 审计）；顾客取关不自动解绑，换人关注属线下场景走管理端 */
    public String unbind(long accountId, String openid) {
        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, openid).orElse(null);
        if (fan == null) {
            auditBinding(accountId, openid, null, null, "no_fan");
            throw new IllegalArgumentException("该 openid 无粉丝记录，无需解绑");
        }
        if (fan.getCustomerId() == null) {
            auditBinding(accountId, openid, null, null, "not_bound");
            throw new IllegalArgumentException("该粉丝未绑定顾客档案，无需解绑");
        }
        Long customerId = fan.getCustomerId();
        fan.setCustomerId(null);
        fanRepository.save(fan);
        auditBinding(accountId, openid, null, customerId, "unbind");
        return "已解除粉丝与顾客档案的绑定";
    }

    /** 流水列表的顾客姓名展示（设计稿第一期验收：已绑定顾客发消息 → 消息流水可见对应顾客姓名）；未绑定回 null */
    public String resolveCustomerName(Long accountId, String fromUser) {
        if (accountId == null || fromUser == null) {
            return null;
        }
        return fanRepository.findByAccountIdAndOpenid(accountId, fromUser)
                .map(WxFanEntity::getCustomerId)
                .flatMap(customerRepository::findById)
                .map(CustomerEntity::getName)
                .orElse(null);
    }

    private void consumeBindSmsCode(long accountId, String openid, String phone, String submitted) {
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(phone).orElse(null);
        if (issued == null) {
            auditBinding(accountId, openid, phone, null, "bad_code");
            throw new IllegalArgumentException("验证码不存在，请先获取验证码");
        }
        if (issued.getUsed() != null && issued.getUsed() == 1) {
            auditBinding(accountId, openid, phone, null, "used_code");
            throw new IllegalArgumentException("验证码已使用，请重新获取");
        }
        if (issued.getExpiresAt() != null && issued.getExpiresAt().before(new Date())) {
            auditBinding(accountId, openid, phone, null, "expired_code");
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!issued.getCode().equals(submitted.trim())) {
            // 防爆破：错试计数，连续错满 5 次烧码（一次性凭证作废须重新取码）；每步落审计
            // 原子递增+达上限烧码（单条 UPDATE），并发错试不丢增量、不绕过 5 次上限
            smsCodeRepository.incrementAttempts(issued.getId(), SMS_CODE_MAX_ATTEMPTS);
            auditBinding(accountId, openid, phone, null, "bad_code");
            throw new IllegalArgumentException("验证码不对，请核对后重试");
        }
        // 原子烧码：正确码并发双投递下，仅首个 UPDATE 命中行（返回 1），其余请求读到 used 已置 1 → 400
        if (smsCodeRepository.markUsed(issued.getId()) == 0) {
            auditBinding(accountId, openid, phone, null, "used_code");
            throw new IllegalArgumentException("验证码已使用，请重新获取");
        }
    }

    /** 绑定审计落库（§4.8）；OAuth ticket 兑换失败路径也复用（bad_ticket） */
    public void auditBinding(long accountId, String openid, String phone, Long customerId, String result) {
        WxBindingLogEntity row = new WxBindingLogEntity();
        row.setAccountId(accountId);
        row.setFanOpenid(openid);
        row.setPhone(phone);
        row.setCustomerId(customerId);
        row.setResult(result);
        bindingLogRepository.save(row);
    }
}
