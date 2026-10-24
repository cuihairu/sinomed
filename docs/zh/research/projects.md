# 开源项目盘点

> 调研日期:2026-10-05。所有项目均经 GitHub `gh api` 或 Gitee `gitee.com/api/v5` 实时核验,查不到的如实标注「未查到」。功能清单摘自各自 README,未做推断。

## 起点线索核验

| 线索 | 结论 |
| ---- | ---- |
| TANGKUO/HIS(微服务 HIS) | **已核验存在**(GitHub) |
| gitee njpaper/hisystem(中小医院 BS) | **已核验存在**(为 `sensay/hisystem` 的 fork,父仓已不可访问) |
| gitee DzmHIS(大宅门医疗门诊) | **DzmHIS 是账号名而非仓库名**;`gitee.com/DzmHIS/OpenHIS` 原仓已 404,经其 fork 的 homepage 字段确认账号归属,现存可访问副本为若干 fork |
| OpenHIS(大宅门) | 原仓已下线;注意与 GitHub 上另一套同名的 `tntlinking-opensource/openhis`(天天开源)区分,两者是完全不同的产品 |

## 已核验项目

### 1. wolongHyy/Yukang-Smart-Cloud-Clinic-Platform(愈康云诊所)— 与本项目匹配度最高

- **仓库**:<https://github.com/wolongHyy/Yukang-Smart-Cloud-Clinic-Platform>
- **技术栈**:JavaScript / Electron 形态,本地优先 SQLite,电脑/手机/平板局域网访问
- **功能清单**(README):单店/连锁总店/分店三形态(分店独立库、总店聚合、邀请码接入);电子病历含主诉、现病史、既往史、过敏史、体格检查、**中医四诊、诊断、辨证**、医嘱;**处方类型覆盖中西成药、输注、中药、贴敷、外治**;药品分类含**中药饮片、中成药、中药颗粒**;药房库存(批次/效期/盘点)、门诊收费(现金/微信/支付宝/银行卡/医保)、经营统计;**AI 辅助开方与审方**;每日快照备份
- **活跃度**:105 stars / 12 forks,最近 push **2026-10-02**(活跃)
- **许可证**:MIT
- **可借鉴点**:中医四诊 + 辨证 + 中药/贴敷/外治处方的病历-处方模型;本地优先 + 连锁聚合的多店架构

### 2. tntlinking-opensource/openhis-clinic(天天开源 · 诊所版)

- **仓库**:<https://github.com/tntlinking-opensource/openhis-clinic>
- **技术栈**:C#/.NET(`Code/medical2.0` 含 back + client + docker-compose)+ SQL 脚本
- **功能清单**(README):面向社区卫生服务站/村卫生室/民营诊所,**5 个核心模块:预约登记、就诊、收费发药、报表查询、库存管理**;支持本地化 / SaaS、医保对接,可与 OpenHIS 医院系统组县域医共体
- **活跃度**:24 stars,最近 push **2026-09-10**(活跃,有社区与文档站)
- **许可证**:GPL-3.0(另附天天开源社区版许可协议,商业版并行)
- **可借鉴点**:「5 模块诊所闭环」与医馆规模最匹配;注意 GPL-3.0 传染性,复用代码前需评估
- 同组织 `tntlinking-opensource/openhis`(医院通用版,142 stars,活跃)面向二级以下医院,含体检、收费结算、医护协同、药房、电子病历等 10 大模块

### 3. ZainZhao/HIS — 同类 star 最高的中文 HIS

- **仓库**:<https://github.com/ZainZhao/HIS>
- **技术栈**:Spring Cloud Netflix(Eureka/Config/Feign/Zuul/Hystrix)+ Spring Boot 2.0.3 + MyBatis;前端 Vue 2.6 + Element-UI,患者端 uni-app;仓库含单体(`HIS-master`)与微服务(`his-cloud`)两套
- **功能清单**(README):**门诊医生、药房医生、医技医生、收费员、对帐员、管理员六工作站**;提供在线演示
- **活跃度**:**1635 stars / 603 forks**,最近 push 2025-02-24
- **许可证**:Apache-2.0
- **可借鉴点**:「多工作站分角色」的门诊业务划分;单体/微服务同构双实现(小医馆单机起步、连锁后再拆)

### 4. TANGKUO/HIS

- **仓库**:<https://github.com/TANGKUO/HIS>
- **技术栈**:与 ZainZhao/HIS 同源(东软实训需求):Spring Cloud Netflix + Spring Boot 2.0.3 + MyBatis + MySQL,配套 Redis/RabbitMQ/ES;前端 Vue + Element-UI + uni-app
- **功能清单**(README):临床诊疗 / 药品管理 / 财务管理 / 患者管理,**六工作站**(门诊医生、药房医生、医技医生、收费员、对帐员、管理员),含 PC 与 App 截图
- **活跃度**:600 stars / 321 forks,最近 push 2022-06-21(停滞,作者自述为实训课设)
- **许可证**:Apache-2.0
- **可借鉴点**:门诊业务主链路范式;双端结构(PC 诊疗端 + 患者 App)

### 5. Fankekeke/chinese_medicine_store_cos(中药店管理系统)

- **仓库**:<https://github.com/Fankekeke/chinese_medicine_store_cos>
- **技术栈**:SpringBoot + Vue(毕设级)
- **功能清单**(README):中药饮片进销存、中医处方流转;管理员侧:药材管理、库存预警、供应商、电子处方、**病症处方模板**、多门店、销售统计;医生侧:病症处方、订单;用户侧:药材购买、在线支付、订单评价;订单流转含抓药、复核、配送
- **活跃度**:10 stars,pushed 2026-03-13
- **许可证**:无 license(默认保留所有权利,不可直接复用代码)
- **可借鉴点**:中药饮片「药材档案-采购-库房预警-抓药复核配送」链路;病症处方模板库

### 6. xrl-workshop/herb-ms-ssm + herb-ms-vue(中药处方管理系统 HerbMS)

- **仓库**:<https://github.com/xrl-workshop/herb-ms-ssm>(后端)、<https://github.com/xrl-workshop/herb-ms-vue>(前端)
- **技术栈**:Java 17 + SSM(Spring/SpringMVC/MyBatis)+ Tomcat 10 + MySQL 8(Jakarta EE);前端 Vue 3
- **功能清单**:中药处方管理(药材-处方-计价),后端四层抽象
- **活跃度**:10 stars / 6 stars,均 pushed 2024-01-12
- **许可证**:WTFPL
- **可借鉴点**:小而干净的中药处方领域模型;功能深度有限

### 7. njpaper/hisystem(中小医院信息管理系统,Gitee)

- **仓库**:<https://gitee.com/njpaper/hisystem>
- **技术栈**:Java SpringBoot + Bootstrap(B/S 单体);含读卡器 SDK(德卡 D3 IC 读卡器)
- **功能清单**(README):挂号收费、门诊管理、划价收费、药房取药、体检管理、药房管理、系统维护;**就诊卡支持手动录入与 IC 读卡两种方式**
- **活跃度**:10 stars / 4 forks,pushed 2021-03-07(停滞)
- **许可证**:未标注
- **可借鉴点**:实体就诊卡/读卡器对接;轻量单体结构

### 8. OpenHIS 1.0「大宅门云诊所系统」(Gitee 原仓已下线,经 fork 核验)

- **原仓**:`DzmHIS/OpenHIS` 已 404;可访问副本:`wangzhixuan/OpenHIS`、`agaobiao/OpenHIS`(Gitee)、`1638824607/OpenHIS`(GitHub,59 stars)等;曾标 805 stars 的 `mali218/OpenHIS` 主副本现不可访问
- **技术栈**:PHP ≥5.5 + ThinkPHP 3.2.3 + MySQL
- **功能清单**(fork 内 README):诊所全流程(挂号 → 医生 → 划价 → 收费 → 取药)、微信绑定扫码登录、支付宝/微信支付、图表统计,自称「云诊所系统,开源 + PC + 移动端」
- **活跃度**:2017-2018 年后停滞
- **许可证**:第三方收录页标注 Apache;原仓失联,复用需谨慎
- **可借鉴点**:国内较早的云诊所实现;诊所全流程图可作业务基准线

### 9. 其他已核验的中医特色小项目

| 仓库 | 说明 | 活跃度 | 许可证 |
| ---- | ---- | ---- | ---- |
| `spunky5/his_mvp`(GitHub) | 中医 HIS MVP:挂号/看诊/收费/药房/库存/财务,含**辨证开方、中药方剂(带拼音输入)、药材出入库/盘点**;FastAPI + SQLite + Vue 3 | 0 stars,push 2026-09-08 | 无 |
| `feitas/zhong_yi`(GitHub) | Odoo V10 中医门诊模块 | 11 stars,push 2017-04 | 无 |
| `AnthonyLeeDevelopment/hospital`(Gitee) | SpringBoot + Vue 中医馆业务信息管理系统(毕设,仅标题无功能明细) | 0 stars,push 2024-11 | MIT |
| `Mahongsheng/neusoft-cloud-his`(GitHub) | 东软云 HIS(截图为主) | 28 stars,push 2024-08 | 无 |

## 未查到 / 存疑

- **gitee `DzmHIS` 账号与 `mali218/OpenHIS` 主副本**:不可访问(账号注销或转私有)。
- **`sensay/hisystem`(njpaper/hisystem 父仓)**:Not Found。
- **成熟的中医医馆连锁专有开源系统**:未发现 star 高、持续维护、以中医医馆连锁管理为主定位的成熟开源项目。中医特色项目集中在毕设级或个人 MVP,活跃度与完成度明显低于通用门诊 HIS——这一空档本身是调研结论之一。

## 仲正堂 App 公开资料核验

**结论:公开渠道未查到「仲正堂」官方 App。**

- Apple App Store(中国区)iTunes Search API 检索「仲正堂」返回 0 个相关应用(对照组「中医馆」可正常返回天大馆、小鹿中医等真实医馆 App,说明检索有效)。
- 应用宝 / 华为应用市场 / 豌豆荚等 Android 渠道有反爬机制,未能完成机器核验。
- 公开资料中「仲正堂」的形态是**中医推拿/艾灸养生连锁与培训机构**(上海仲正堂教育,2013 年创立,特色推拿、关元灸;安心加盟网称上海 26 家分店,该站为招商聚合站、可信度中等),其数字化可见线索是**第三方 SaaS(有赞美业)**而非自研 App。

因此本项目的功能范围以**源码实现 + 上述开源项目实况**为依据,对仲正堂仅作「参照」其门店业态(推拿、艾灸等疗程型服务场景),不引用无法核验的功能清单。

## 参考价值排序

1. **业务模型**:Yukang(MIT、活跃,四诊/辨证/中药处方/连锁分店)与本项目中医医馆需求重合度最高;
2. **诊所模块闭环**:openhis-clinic(5 模块,活跃,注意 GPL-3.0)与 ZainZhao/HIS(Apache-2.0,六工作站角色模型);
3. **中医特有领域对象**:中药饮片/颗粒分类、病症处方模板、配伍禁忌审方、药材进销存——见 chinese_medicine_store_cos、his_mvp、herb-ms-ssm;
4. **许可证注意**:多数中医特色小项目无许可证(不可直接复用代码);tntlinking 系为 GPL-3.0 + 商业双授权;大宅门 OpenHIS 原仓失联。
