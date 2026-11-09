package com.sinomed.seed;

import com.sinomed.entity.AcupointEntity;
import com.sinomed.entity.FormulaEntity;
import com.sinomed.entity.FormulaItemEntity;
import com.sinomed.entity.HerbEntity;
import com.sinomed.entity.PrescriptionTemplateEntity;
import com.sinomed.entity.PrescriptionTemplateItemEntity;
import com.sinomed.repository.AcupointRepository;
import com.sinomed.repository.FormulaItemRepository;
import com.sinomed.repository.FormulaRepository;
import com.sinomed.repository.HerbRepository;
import com.sinomed.repository.PrescriptionTemplateItemRepository;
import com.sinomed.repository.PrescriptionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 医药字典种子（LAY-002 拆分）：药材价格字典、病症处方模板、方剂库（公版文献口径）、穴位字典。
 * 逐条按自然键判重（老库缺项自动补齐）；方剂/穴位整库为空才全量播种。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogSeedService {

    private final HerbRepository herbRepository;
    private final PrescriptionTemplateRepository templateRepository;
    private final PrescriptionTemplateItemRepository templateItemRepository;
    private final FormulaRepository formulaRepository;
    private final FormulaItemRepository formulaItemRepository;
    private final AcupointRepository acupointRepository;
    private final SeedSupport support;

    /** 药材字典：覆盖种子处方 7 味与常用饮片，价格（分/克）为虚构演示值 */
    public void seedHerbs() {
        record HerbPrice(String name, int pricePerGram) {}
        List<HerbPrice> herbs = List.of(
                new HerbPrice("柴胡", 8), new HerbPrice("白芍", 5), new HerbPrice("当归", 12),
                new HerbPrice("茯苓", 4), new HerbPrice("白术", 6), new HerbPrice("薄荷", 3),
                new HerbPrice("炙甘草", 2), new HerbPrice("甘草", 2), new HerbPrice("黄芪", 6),
                new HerbPrice("丹参", 8), new HerbPrice("半夏", 9), new HerbPrice("陈皮", 3),
                new HerbPrice("川芎", 7), new HerbPrice("地黄", 8), new HerbPrice("麦冬", 10),
                // 方剂库补常用饮片（逐条判重，老库缺项自动补齐）
                new HerbPrice("人参", 25), new HerbPrice("熟地黄", 10), new HerbPrice("山药", 5),
                new HerbPrice("泽泻", 4), new HerbPrice("牡丹皮", 6), new HerbPrice("苍术", 5),
                new HerbPrice("厚朴", 4), new HerbPrice("升麻", 5), new HerbPrice("生姜", 2),
                new HerbPrice("山茱萸", 12));
        for (HerbPrice herb : herbs) {
            if (herbRepository.existsByName(herb.name())) {
                continue;
            }
            HerbEntity entity = new HerbEntity();
            entity.setName(herb.name());
            entity.setPrice(herb.pricePerGram());
            herbRepository.save(entity);
        }
        log.info("种子·药材字典：检查完成");
    }

    /** 病症处方模板：两条常见病症，开方页「套用模板」演示（按病症名幂等） */
    public void seedTemplates() {
        record TemplateSeed(String name, int doses, int decoction, String usage, String remark,
                            List<String[]> herbs) {}
        List<TemplateSeed> seeds = List.of(
                new TemplateSeed("风寒感冒", 7, 0, "水煎服，日一剂，早晚温服", "避风寒，多饮温水",
                        List.of(new String[]{"荆芥", "10"}, new String[]{"防风", "10"}, new String[]{"紫苏叶", "9"})),
                new TemplateSeed("脾胃虚弱", 14, 1, "水煎服，日一剂，早晚温服；代煎", "忌生冷油腻",
                        List.of(new String[]{"白术", "12"}, new String[]{"茯苓", "15"}, new String[]{"炙甘草", "6"})));
        for (TemplateSeed seed : seeds) {
            if (templateRepository.findByName(seed.name()).isPresent()) {
                continue;
            }
            PrescriptionTemplateEntity template = new PrescriptionTemplateEntity();
            template.setName(seed.name());
            template.setDoses(seed.doses());
            template.setDecoction(seed.decoction());
            template.setUsage(seed.usage());
            template.setRemark(seed.remark());
            template.setEnabled(1);
            template = templateRepository.save(template);
            List<String[]> herbs = seed.herbs();
            for (int i = 0; i < herbs.size(); i++) {
                PrescriptionTemplateItemEntity item = new PrescriptionTemplateItemEntity();
                item.setTemplateId(template.getId());
                item.setHerb(herbs.get(i)[0]);
                item.setWeight(Double.parseDouble(herbs.get(i)[1]));
                item.setSort(i);
                templateItemRepository.save(item);
            }
        }
        log.info("种子·病症处方模板：检查完成");
    }

    /** 方剂库演示数据：经典方剂与药味组成（公版方剂文献口径），供开方页「方剂库」按方名/拼音检索带出 */
    public void seedFormulas() {
        if (formulaRepository.count() > 0) {
            return;
        }
        record HerbComp(String herb, double weight, String special) {}
        record FormulaSeed(String name, String pinyin, String source, String indication, List<HerbComp> herbs) {}
        List<FormulaSeed> seeds = List.of(
                new FormulaSeed("四君子汤", "sijunzitang", "太平惠民和剂局方", "补气健脾，用于脾胃虚弱、食少便溏",
                        List.of(new HerbComp("人参", 9, null), new HerbComp("白术", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("四物汤", "siwutang", "太平惠民和剂局方", "补血调血，用于血虚萎黄、月经不调",
                        List.of(new HerbComp("熟地黄", 12, null), new HerbComp("当归", 9, null),
                                new HerbComp("白芍", 9, null), new HerbComp("川芎", 6, null))),
                new FormulaSeed("逍遥散", "xiaoyaosan", "太平惠民和剂局方", "疏肝健脾养血，用于肝郁血虚脾弱",
                        List.of(new HerbComp("柴胡", 9, null), new HerbComp("当归", 9, null),
                                new HerbComp("白芍", 9, null), new HerbComp("白术", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("薄荷", 6, "后下"),
                                new HerbComp("生姜", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("补中益气汤", "buzhongyiqitang", "内外伤辨惑论", "补中益气升阳，用于脾虚气陷、少气懒言",
                        List.of(new HerbComp("黄芪", 15, null), new HerbComp("人参", 9, null),
                                new HerbComp("白术", 9, null), new HerbComp("当归", 9, null),
                                new HerbComp("陈皮", 6, null), new HerbComp("升麻", 6, null),
                                new HerbComp("柴胡", 6, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("玉屏风散", "yupingfengsan", "丹溪心法", "益气固表止汗，用于表虚自汗、易感风寒",
                        List.of(new HerbComp("黄芪", 15, null), new HerbComp("白术", 9, null),
                                new HerbComp("防风", 6, null))),
                new FormulaSeed("六味地黄丸", "liuweidihuangwan", "小儿药证直诀", "滋补肾阴，用于肾阴虚亏、腰膝酸软",
                        List.of(new HerbComp("熟地黄", 24, null), new HerbComp("山茱萸", 12, null),
                                new HerbComp("山药", 12, null), new HerbComp("泽泻", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("牡丹皮", 9, null))),
                new FormulaSeed("平胃散", "pingweisan", "太平惠民和剂局方", "燥湿运脾行气，用于湿滞脾胃、脘腹胀满",
                        List.of(new HerbComp("苍术", 12, null), new HerbComp("厚朴", 9, null),
                                new HerbComp("陈皮", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("二陈汤", "erchantang", "太平惠民和剂局方", "燥湿化痰理气，用于痰湿停滞、咳嗽痰多",
                        List.of(new HerbComp("半夏", 9, null), new HerbComp("陈皮", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("八珍汤", "bazhentang", "正体类要", "气血双补，用于气血两虚、面色萎黄",
                        List.of(new HerbComp("人参", 9, null), new HerbComp("白术", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("甘草", 6, null),
                                new HerbComp("熟地黄", 12, null), new HerbComp("当归", 9, null),
                                new HerbComp("白芍", 9, null), new HerbComp("川芎", 6, null)))
        );
        for (FormulaSeed f : seeds) {
            FormulaEntity formula = new FormulaEntity();
            formula.setName(f.name());
            formula.setPinyin(f.pinyin());
            formula.setSource(f.source());
            formula.setIndication(f.indication());
            FormulaEntity saved = formulaRepository.save(formula);
            support.backdateRow("formulas", saved.getId(), support.at(-20, 10, 0));
            for (int i = 0; i < f.herbs().size(); i++) {
                HerbComp h = f.herbs().get(i);
                FormulaItemEntity item = new FormulaItemEntity();
                item.setFormulaId(saved.getId());
                item.setHerb(h.herb());
                item.setWeight(h.weight());
                item.setSpecial(h.special());
                item.setSort(i);
                formulaItemRepository.save(item);
            }
        }
        log.info("种子·方剂库：检查完成");
    }

    /** 穴位字典：常用经络穴位 27 穴（归经/定位/主治为文献参考口径） */
    public void seedAcupoints() {
        if (acupointRepository.count() > 0) {
            return;
        }
        record PointSeed(String name, String pinyin, String meridian, String location, String indication) {}
        List<PointSeed> seeds = List.of(
                new PointSeed("足三里", "zusanli", "足阳明胃经", "犊鼻下 3 寸，胫骨前嵴外 1 横指", "健脾和胃、扶正培元，主治胃痛、腹胀、虚劳"),
                new PointSeed("三阴交", "sanyinjiao", "足太阴脾经", "内踝尖上 3 寸，胫骨内侧面后缘", "健脾利湿、调经止痛，主治月经不调、失眠"),
                new PointSeed("合谷", "hegu", "手阳明大肠经", "手背第 1、2 掌骨间，第 2 掌骨桡侧中点", "疏风解表、镇痛通络，主治头痛、牙痛、面口疾患"),
                new PointSeed("太冲", "taichong", "足厥阴肝经", "足背第 1、2 跖骨间，跖骨结合部前凹陷", "疏肝理气、平肝熄风，主治头痛眩晕、胁痛"),
                new PointSeed("内关", "neiguan", "手厥阴心包经", "腕横纹上 2 寸，掌长肌腱与桡侧腕屈肌腱之间", "宁心安神、和胃降逆，主治心悸、失眠、呕恶"),
                new PointSeed("外关", "waiguan", "手少阳三焦经", "腕背横纹上 2 寸，尺骨与桡骨之间", "疏风清热、通经活络，主治热病、肩背痛"),
                new PointSeed("曲池", "quchi", "手阳明大肠经", "屈肘，肘横纹外侧端凹陷", "清热解表、通络止痛，主治发热、高血压、肘臂痛"),
                new PointSeed("阳陵泉", "yanglingquan", "足少阳胆经", "腓骨头前下方凹陷", "疏肝利胆、舒筋活络，主治胁痛、下肢痿痹"),
                new PointSeed("委中", "weizhong", "足太阳膀胱经", "腘横纹中点", "舒筋通络、凉血泄热，主治腰背痛"),
                new PointSeed("百会", "baihui", "督脉", "前发际正中直上 5 寸（两耳尖连线中点）", "升阳举陷、醒脑开窍，主治头痛、眩晕、脱肛"),
                new PointSeed("风池", "fengchi", "足少阳胆经", "枕骨下，斜方肌与胸锁乳突肌之间凹陷", "祛风解表、明目醒脑，主治感冒、颈项强痛、眩晕"),
                new PointSeed("大椎", "dazhui", "督脉", "第 7 颈椎棘突下凹陷", "清热解表、截疟止痛，主治感冒发热、项强"),
                new PointSeed("关元", "guanyuan", "任脉", "前正中线上，脐下 3 寸", "培元固本、温阳益气，主治虚劳、遗尿、宫寒"),
                new PointSeed("气海", "qihai", "任脉", "前正中线上，脐下 1.5 寸", "益气补虚、调理气机，主治气虚乏力、脘腹胀满"),
                new PointSeed("中脘", "zhongwan", "任脉", "前正中线上，脐上 4 寸", "健脾和胃、消积化滞，主治胃脘痛、呕吐、纳呆"),
                new PointSeed("神阙", "shenque", "任脉", "脐窝正中", "温阳救逆、健运脾胃，主治腹痛、泄泻（多灸不针）"),
                new PointSeed("涌泉", "yongquan", "足少阴肾经", "足底前 1/3 凹陷处", "滋阴益肾、引火下行，主治失眠、高血压、足心热"),
                new PointSeed("太溪", "taixi", "足少阴肾经", "内踝尖与跟腱之间凹陷", "滋阴补肾、调理下焦，主治腰痛、耳鸣、遗精"),
                new PointSeed("血海", "xuehai", "足太阴脾经", "髌骨内上缘上 2 寸", "理血调经、祛风止痒，主治月经不调、皮肤瘙痒"),
                new PointSeed("阴陵泉", "yinlingquan", "足太阴脾经", "胫骨内侧髁下缘凹陷", "健脾利湿、通利小便，主治水肿、泄泻、膝痛"),
                new PointSeed("肩井", "jianjing", "足少阳胆经", "第 7 颈椎棘突与肩峰连线中点", "祛风活络、消肿散结，主治肩背痛、乳痈"),
                new PointSeed("命门", "mingmen", "督脉", "第 2 腰椎棘突下凹陷", "温肾壮阳、强腰固本，主治腰痛、遗尿、阳痿"),
                new PointSeed("肾俞", "shenshu", "足太阳膀胱经", "第 2 腰椎棘突下旁开 1.5 寸", "益肾固精、强腰明目，主治腰痛、耳鸣、肾虚诸症"),
                new PointSeed("脾俞", "pishu", "足太阳膀胱经", "第 11 胸椎棘突下旁开 1.5 寸", "健脾化湿、统血生气，主治腹胀、泄泻、水肿"),
                new PointSeed("胃俞", "weishu", "足太阳膀胱经", "第 12 胸椎棘突下旁开 1.5 寸", "和胃健脾、消食化滞，主治胃脘痛、呕吐"),
                new PointSeed("印堂", "yintang", "经外奇穴", "两眉头连线中点", "宁神醒脑、通窍止痛，主治失眠、头痛、鼻渊"),
                new PointSeed("太阳", "taiyang", "经外奇穴", "眉梢与目外眦之间后约 1 横指凹陷", "疏风止痛、明目，主治头痛、偏头痛、眼疾"));
        for (PointSeed p : seeds) {
            AcupointEntity point = new AcupointEntity();
            point.setName(p.name());
            point.setPinyin(p.pinyin());
            point.setMeridian(p.meridian());
            point.setLocation(p.location());
            point.setIndication(p.indication());
            AcupointEntity saved = acupointRepository.save(point);
            support.backdateRow("acupoints", saved.getId(), support.at(-18, 9, 30));
        }
        log.info("种子·穴位字典：检查完成");
    }
}
