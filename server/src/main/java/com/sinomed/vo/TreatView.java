package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.sinomed.entity.TreatEntity;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Schema(title = "疗程表信息")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TreatView implements Serializable {
    @Schema(title = "id",description ="疗程表id" ,example = "1234567890")
    Long id;
    @Schema(title = "客人id",description = "用户id",example = "1")
    Long customerId;
    @Schema(title = "姓名",description = "姓名",example = "崔海涛")
    String name;
    @Schema(title = "手机号码",description = "手机号码",example = "13852299511")
    String phone;
    @Schema(title = "性别",description = "性别",example = "男")
    Integer gender;
    @Schema(title = "年纪",description = "年纪",example = "30")
    Integer age;
    //@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    @Schema(title = "初诊日期",description = "初诊日期",example = "2023-06-06 8:00:00")
    Date createTime;
    @Schema(title = "主诉",description = "主诉",example = "火气旺")
    String desc;
    @Schema(title = "问诊",description = "问诊",example = "火气旺")
    String inquiry;
    @Schema(title = "望诊",description = "望诊",example = "火气旺")
    String observation;
    @Schema(title = "触诊",description = "触诊",example = "火气旺")
    String palpation;
    @Schema(title = "脉象",description = "脉象",example = "脉象平稳")
    String pulse;
    @Schema(title = "左手脉象",description = "左手脉象",example = "脉象平稳")
    String pulseLeft;
    @Schema(title = "右手脉象",description = "右手脉象",example = "脉象平稳的很")
    String pulseRight;
    @Schema(title = "脉象信息",description = "脉象信息",example = "平稳")
    String pulseInfo;
    @Schema(title = "左手寸脉",description = "左手寸关尺分部-寸",example = "弦")
    String pulseCunLeft;
    @Schema(title = "左手关脉",description = "左手寸关尺分部-关",example = "沉迟")
    String pulseGuanLeft;
    @Schema(title = "左手尺脉",description = "左手寸关尺分部-尺",example = "弱")
    String pulseChiLeft;
    @Schema(title = "右手寸脉",description = "右手寸关尺分部-寸",example = "浮")
    String pulseCunRight;
    @Schema(title = "右手关脉",description = "右手寸关尺分部-关",example = "沉细")
    String pulseGuanRight;
    @Schema(title = "右手尺脉",description = "右手寸关尺分部-尺",example = "沉")
    String pulseChiRight;
    @Schema(title = "左手 五行脉_子_生_本子",description = "左手 五行脉_子_生_本子",example = "很正常")
    String fiveBenZiLeft;
    @Schema(title = "左手 五行脉_子_生_子本",description = "左手 五行脉_子_生_本丑",example = "很正常")
    String fiveZiBenLeft;
    @Schema(title = "右手 五行脉_子_生_本子",description = "右手 五行脉_子_生_本子",example = "很正常")
    String fiveBenZiRight;
    @Schema(title = "右手 五行脉_子_生_子本",description = "右手 五行脉_子_生_子本",example = "很正常")
    String fiveZiBenRight;
    @Schema(title = "左手 五行脉_克_生_本子",description = "左手 五行脉_克_生_本子",example = "很正常")
    String fiveBenKeLeft;
    @Schema(title = "左手 五行脉_克_生_子本",description = "左手 五行脉_克_生_子本",example = "很正常")
    String fiveKeBenLeft;
    @Schema(title = "右手 五行脉_克_生_本子",description = "右手 五行脉_克_生_本子",example = "很正常")
    String fiveBenKeRight;
    @Schema(title = "右手 五行脉_克_生_子本",description = "右手 五行脉_克_生_子本",example = "很正常")
    String fiveKeBenRight;
    @Schema(title = "难经 左",description = "难经",example = "很正常")
    String fiveNanJinLeft;
    @Schema(title = "难经 左 大",description = "难经 大",example = "很正常")
    String fiveNanJinBigLeft;
    @Schema(title = "难经 左 小",description = "难经 小",example = "很正常")
    String fiveNanJinSmallLeft;
    @Schema(title = "难经 右",description = "难经",example = "很正常")
    String fiveNanJinRight;
    @Schema(title = "难经 右 大",description = "难经 大",example = "很正常")
    String fiveNanJinBigRight;
    @Schema(title = "难经 右 小",description = "难经 小",example = "很正常")
    String fiveNanJinSmallRight;
    @Schema(title = "左手 缓急",description = "左手 缓急",example = "很正常")
    String fiveRateLeft;
    @Schema(title = "右手 缓急",description = "右手 缓急",example = "很正常")
    String fiveRateRight;
    @Schema(title = "左手 辅助",description = "左手 辅助",example = "很正常")
    String fiveAuxLeft;
    @Schema(title = "右手 辅助",description = "右手 辅助",example = "很正常")
    String fiveAuxRight;
    @Schema(title = "取穴-反应点 左",description = "取穴",example = "很正常")
    String acupointLeft;
    @Schema(title = "取穴-反应点 右",description = "取穴",example = "很正常")
    String acupointRight;
    @Schema(title = "针灸处方-针法",description = "如 毫针、电针、温针",example = "毫针")
    String acuMethod;
    @Schema(title = "针灸处方-留针分钟",description = "1-240",example = "25")
    Integer retentionMinutes;
    @Schema(title = "针灸处方-手法",description = "如 平补平泻、提插捻转",example = "平补平泻")
    String manipulation;
    @Schema(title = "针灸处方-灸法",description = "如 艾条悬灸，每穴 15 分钟",example = "艾条悬灸，每穴 15 分钟")
    String moxaMethod;
    @Schema(title = "针灸处方-疗程频次",description = "如 每周 2 次 × 4 周",example = "每周 2 次 × 4 周")
    String acuCourse;
    @Schema(title = "诊断",description = "反应点",example = "很正常")
    String diagnose;
    @Schema(title = "方案",description = "方案",example = "多喝热水")
    String plan;
    @Schema(title = "饮食",description = "饮食",example = "多吃水果")
    String diet;
    @Schema(title = "调理过程",description = "调理过程",example = "效果很好")
    String conditioning;
    @Schema(title = "回访",description = "回访",example = "很好")
    String review;

    public static TreatView FromTreatEntity(TreatEntity treat){
        return TreatView.builder()
                .createTime(treat.getCreateTime())
                .id(treat.getId())
                .customerId(treat.getCustomerId())
                .desc(treat.getDesc())
                .inquiry(treat.getInquiry())
                .observation(treat.getObservation())
                .palpation(treat.getPalpation())
                .pulse(treat.getPulse())
                .pulseLeft(treat.getPulseLeft())
                .pulseRight(treat.getPulseRight())
                .pulseInfo(treat.getPulseInfo())
                .pulseCunLeft(treat.getPulseCunLeft())
                .pulseGuanLeft(treat.getPulseGuanLeft())
                .pulseChiLeft(treat.getPulseChiLeft())
                .pulseCunRight(treat.getPulseCunRight())
                .pulseGuanRight(treat.getPulseGuanRight())
                .pulseChiRight(treat.getPulseChiRight())
                .fiveBenZiLeft(treat.getFiveBenZiLeft())
                .fiveBenZiRight(treat.getFiveBenZiRight())
                .fiveZiBenLeft(treat.getFiveZiBenLeft())
                .fiveZiBenRight(treat.getFiveZiBenRight())
                .fiveBenKeLeft(treat.getFiveBenKeLeft())
                .fiveBenKeRight(treat.getFiveBenKeRight())
                .fiveKeBenLeft(treat.getFiveKeBenLeft())
                .fiveKeBenRight(treat.getFiveKeBenRight())
                .fiveNanJinLeft(treat.getFiveNanJinLeft())
                .fiveNanJinRight(treat.getFiveNanJinRight())
                .fiveNanJinBigLeft(treat.getFiveNanJinBigLeft())
                .fiveNanJinBigRight(treat.getFiveNanJinBigRight())
                .fiveNanJinSmallLeft(treat.getFiveNanJinSmallLeft())
                .fiveNanJinSmallRight(treat.getFiveNanJinSmallRight())
                .fiveRateLeft(treat.getFiveRateLeft())
                .fiveRateRight(treat.getFiveRateRight())
                .fiveAuxLeft(treat.getFiveAuxLeft())
                .fiveAuxRight(treat.getFiveAuxRight())
                .acupointLeft(treat.getAcupointLeft())
                .acupointRight(treat.getAcupointRight())
                .acuMethod(treat.getAcuMethod())
                .retentionMinutes(treat.getRetentionMinutes())
                .manipulation(treat.getManipulation())
                .moxaMethod(treat.getMoxaMethod())
                .acuCourse(treat.getAcuCourse())
                .diagnose(treat.getDiagnose())
                .plan(treat.getPlan())
                .diet(treat.getDiet())
                .conditioning(treat.getConditioning())
                .review(treat.getReview())
                .build();
    }

    public TreatEntity ToTreatEntity(){
        TreatEntity treatEntity = new TreatEntity();
        treatEntity.setId(id);
        treatEntity.setCustomerId(customerId);
        treatEntity.setDesc(desc);
        treatEntity.setInquiry(inquiry);
        treatEntity.setObservation(observation);
        treatEntity.setPalpation(palpation);
        treatEntity.setPulse(pulse);
        treatEntity.setPulseLeft(pulseLeft);
        treatEntity.setPulseRight(pulseRight);
        treatEntity.setPulseInfo(pulseInfo);
        treatEntity.setPulseCunLeft(pulseCunLeft);
        treatEntity.setPulseGuanLeft(pulseGuanLeft);
        treatEntity.setPulseChiLeft(pulseChiLeft);
        treatEntity.setPulseCunRight(pulseCunRight);
        treatEntity.setPulseGuanRight(pulseGuanRight);
        treatEntity.setPulseChiRight(pulseChiRight);
        treatEntity.setFiveBenZiLeft(fiveBenZiLeft);
        treatEntity.setFiveBenZiRight(fiveBenZiRight);
        treatEntity.setFiveZiBenLeft(fiveZiBenLeft);
        treatEntity.setFiveZiBenRight(fiveZiBenRight);
        treatEntity.setFiveBenKeLeft(fiveBenKeLeft);
        treatEntity.setFiveBenKeRight(fiveBenKeRight);
        treatEntity.setFiveKeBenLeft(fiveKeBenLeft);
        treatEntity.setFiveKeBenRight(fiveKeBenRight);
        treatEntity.setFiveNanJinLeft(fiveNanJinLeft);
        treatEntity.setFiveNanJinRight(fiveNanJinRight);
        treatEntity.setFiveNanJinBigLeft(fiveNanJinBigLeft);
        treatEntity.setFiveNanJinBigRight(fiveNanJinBigRight);
        treatEntity.setFiveNanJinSmallLeft(fiveNanJinSmallLeft);
        treatEntity.setFiveNanJinSmallRight(fiveNanJinSmallRight);
        treatEntity.setFiveRateLeft(fiveRateLeft);
        treatEntity.setFiveRateRight(fiveRateRight);
        treatEntity.setFiveAuxLeft(fiveAuxLeft);
        treatEntity.setFiveAuxRight(fiveAuxRight);
        treatEntity.setAcupointLeft(acupointLeft);
        treatEntity.setAcupointRight(acupointRight);
        treatEntity.setAcuMethod(acuMethod);
        treatEntity.setRetentionMinutes(retentionMinutes);
        treatEntity.setManipulation(manipulation);
        treatEntity.setMoxaMethod(moxaMethod);
        treatEntity.setAcuCourse(acuCourse);
        treatEntity.setDiagnose(diagnose);
        treatEntity.setPlan(plan);
        treatEntity.setDiet(diet);
        treatEntity.setConditioning(conditioning);
        treatEntity.setReview(review);
        return treatEntity;
    }
}
