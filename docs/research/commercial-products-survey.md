# Commercial Products Survey: TCM / Clinic Management Systems in China and Abroad

> Survey date: 2026-10-08. The subject is **commercial products** (SaaS, desktop licenses, project-based HIS), complementing the [open-source project inventory](/research/projects).
>
> Method and credibility policy: features and prices are taken first from vendor official sites / official version tables; where the official site has nothing, from government procurement notices, broker research reports on listed companies, and vendor articles in industry media; failing that, from third-party review sites. Every vendor's own marketing number (e.g. "95% accuracy", "90% less charting time") is marked in the body as a **vendor's own claim** and never cited as fact. All UI screenshots live in `docs/research/assets/`, each with its original URL noted underneath; all were accessed on 2026-10-08. Products whose UI material could not be obtained are explicitly marked "not obtained" in the body — no stand-in images are used.

## Summary: four conclusions up front

1. **Domestic clinic-grade products fall into three price bands**: free up to ¥2,980/year (云中医 Professional lists officially at ¥2,980/year/store); several thousand to tens of thousands of yuan as an annual fee or buyout (一心医学 is annual or one-time perpetual; industry articles put SaaS annual fees commonly at ¥5,000–30,000); and hospital-grade project delivery (卫宁健康's Outpatient Information System V6.0 bid at ¥1.38 million for a single set; 东华医为 at ¥2.8 million for a two-hospital merged HIS project). For a TCM medical hall, the third band is waste and the first is insufficient; the mainstream landing zone for commercial products is the top of band one and the bottom of band two.
2. **TCM features are already standard configuration items in domestic clinic SaaS, not a selling point**: tongue presentation and pulse presentation are two ordinary chart fields; the formula library (classic formulas / agreed formulas / historical formulas) loads in one click; processed herbs, granules and patent medicines in separate storerooms; the acupoint dictionary; external therapy prescription types; prescription review alerts — all visible directly in the interfaces of all four: 康博嘉, 云中医, ABC and 一心. By contrast, none of the mainstream overseas EHRs (Practice Fusion, Tebra, athenahealth, eClinicalWorks, Epic) has a TCM module, and TCM practitioners rely on products like Unified Practice and Jane to fill the gap.
3. **Interface forms are highly convergent**: a three-pane workstation — left module navigation + center chart editing + right assist panel — with chart fields fixed as "chief complaint / main symptom / accompanying symptoms / tongue presentation / pulse presentation / disease differentiation / pattern differentiation / prescription", and a formula reference with contraindication alerts hanging beside the prescription area. This layout has exactly one purpose: let the physician complete a full visit without switching pages.
4. **Pricing model determines product shape**: subscription per physician (Practice Fusion, USD 199/physician/month); a percentage of collections (athenahealth, officially "a share of receivables"); tiers by physician type plus claim volume (Tebra, USD 49–799); one-time project investment (Epic, from USD 50 million to USD 1.2 billion per single project). Domestic clinic SaaS mostly copies "per store per year"; hospital grade copies "tendered project delivery".

### Table 1: Overview of 17 products

| Product | Region | Form | Pricing model (publicly stated) | TCM features | UI material |
| ---- | ---- | ---- | ---- | ---- | ---- |
| 康博嘉云诊所 | Beijing | SaaS | Subscription; no public price, quoted by clinic size/modules (vendor FAQ) | Chart fields include tongue/pulse presentation, pattern-element differentiation, classic formula library, processed-herb storeroom | 3 (official site) |
| 云中医(九明珠) | Jiangsu | SaaS | Mini/Standard free, Professional ¥2,980/year/store (official version table) | Classic/agreed/historical formulas, automatic prescription review, processed herbs/granules/patent medicines, decoction & courier | 5 (official site) |
| ABC 诊所管家 | Chengdu | SaaS | Free trial; no public quote | AI tongue/facial diagnosis, 898 acupoints, 6 external therapy types, granule machine integration, multi-storeroom | 2 (official site) |
| 一心医学中医诊所管理系统 | Shenzhen | Windows desktop | 3-day trial; annual fee or one-time buyout; single-machine deployment (official site) | Prescribe by formula name, acupuncture plans, Ten Questions Song-assisted intake, symptom-pattern prescriptions | 4 (official site) |
| 卫宁健康 WiNEX / outpatient system | Shanghai | Project-based HIS | Tender procurement; Outpatient Information System V6.0 at ¥1.38 million per set | No dedicated TCM module; TCM hospitals customized to tender requirements | 1 (健康界 report) |
| 东华医为 iMedical | Beijing | Project-based HIS | Tender procurement; ¥2.8 million for a two-hospital merged HIS project | No public description of a dedicated TCM module found | Not obtained |
| 创业慧康 | Hangzhou | Project-based / cloud HIS | Tender procurement; monthly winning bids at the tens-of-millions-yuan level (broker research report figure) | Claims to cover TCM outpatient, no details | Not obtained |
| 用友医疗 | Beijing | Project-based HIS/HRP | Custom quote by module and user count (third-party figure) | Strong on hospital HR/materials; no TCM features described | Not obtained |
| 金蝶医疗 | Shenzhen | Project-based / cloud | Industry articles say from ¥30,000–100,000 project-based (competitor article figure) | No TCM features described | Not obtained |
| Practice Fusion | USA | Cloud EHR | USD 199/physician/month, annual commitment, single tier | None | 2 (third-party review site) |
| Tebra (Kareo+PatientPop) | USA | Cloud EHR+PM | USD 49–799/physician/month, tiered by physician type and claim volume | None | 1 (official site) |
| athenahealth athenaOne | USA | Cloud EHR+RCM | Percentage of collections; no official number (third-party estimate 4–8%) | None | 1 (official screenshot page) |
| eClinicalWorks | USA | Cloud EHR | Third-party figure USD 449 (EHR) / USD 599 (with PM) per physician per month | None | 1 (official site) |
| Epic | USA | Hospital-grade EHR | Project-based; public cases from USD 50 million to USD 1.2 billion | None; patient-side MyChart has official App screenshots | 1 (App Store) |
| Jane App | Canada | Acupuncture/wellness clinic PM | Three tiers USD 59/79/99, extra headcount priced up | No TCM-dedicated features; templates customizable | 2 (official site) |
| Unified Practice | USA | TCM vertical EHR+PM | Third-party figure USD 49–99/physician/month | Acupoint database + formula library + TCM charting | 2 (official site) |
| myClinic | Singapore | TCM clinic SaaS | Itemized quotes by team size; no public numbers | Granules/consumables managed by the gram, tongue/pulse written into booking notes | 3 (official site) |

---

## 1. Domestic products

### 1. 康博嘉云诊所 (Karrytech, Beijing)

**Form**: SaaS. The official site claims service to over a thousand medical institutions with more than 50% chains, and says it has passed the Public Security Bureau's Level 3 information security certification (all of the above are vendor's own claims; sources: <https://www.karrytech.com/marketing_dry.html>, <https://www.karrytech.com/zhensuofuwu.html>).

**Core function modules** (sources: official clinic SaaS page and FAQ, <https://www.karrytech.com/zhensuofuwu.html>, <https://www.karrytech.cn/related_detail/1445.html>): appointment & registration, triage and queue calling, EMR, smart prescribing, charging & settlement (with in-clinic payment), drug purchasing/sales/inventory, financial reports, patient follow-up make up the main chain. Around it: membership (RFM tag grouping, family groups), WeChat-side booking and online consultation, chain-group control (unified price codes, cross-store record and member interoperability, central warehouse transfers), and OpenAPI integration with medical insurance/LIS/PACS/decoction delivery.

**Pricing model**: subscription; no published numbers, and the FAQ only says "SaaS subscription, no server purchase, up and running in 1–2 days". A third-party industry article gives a range of ¥8,000–30,000/year for the basic tier and per-module subscriptions for mid-to-high-end vertical tiers — but that article sits on a competitor's domain (karrytech.com/share_detail/1451.html is its own marketing piece), so the range is reference only, not a quote.

**Interface**:

![康博嘉 TCM EMR workstation: full-process navigation on the left, structured chart in the center, intelligent diagnosis on the right](assets/karrytech-tcm-2.jpg)
Figure 1 source: <http://www.karrytech.com/share_detail/776.html> (illustration from Karrytech's official article "A brief look at the intelligent TCM features in Karrytech cloud clinic management software", test environment screenshot, accessed 2026-10-08)

![康博嘉 chart item maintenance page: fields such as chief complaint / main symptom / accompanying symptoms / tongue presentation / pulse presentation / TCM diagnosis are configurable, with default row counts and priority rules](assets/karrytech-tcm-1.jpg)
Figure 2 source: same as above

![Smart recommendation popup triggered by the pulse presentation field: pushes disease names, pattern types, formulas and sources by symptom and tongue/pulse](assets/karrytech-tcm-3.jpg)
Figure 3 source: same as above

**TCM features**: chart fields place "tongue presentation, pulse presentation, disease differentiation, pattern differentiation" alongside the chief complaint (Figure 2). The knowledge base is claimed at 1000+ TCM diseases, 2000+ TCM pattern types, 100+ classic formulas from the Shanghan Lun, and 200+ classic formulas from the Jingui Yaolue (vendor's own claim, <http://www.karrytech.com/share_detail/776.html>). Prescription permissions support usage scope, dose limits and a review workflow for toxic, narcotic, precious and fine medicinal materials (FAQ). Storerooms support purchasing, stocktaking and transfer of processed herbs, Chinese patent medicines and consumables; a storeroom keeper can take stock from a phone.

**Corresponding analysis — why it is designed this way**. The business driver of TCM outpatient care is "inexperienced experience cannot be replicated": good TCM doctors are scarce, and young doctors must lean on the system to supply pattern differentiation thinking, so the diagnosis panel on the right is not an optional plugin but a permanent column — enter symptoms and it pushes disease names, pattern types and formulas with sources, merging "write the chart" and "do pattern differentiation" into one entry. By user role, this interface serves three people at once: doctors want speed (pinyin/Wubi/voice input, template reuse), the front desk wants flow (booking-registration-in-clinic payment without queues), and the owner wants cross-store data (unified pricing baseline, group reports). The efficiency/compliance trade-off is placed in two spots: chart fields are configurable with a "group > clinic > department > chart type" priority (Figure 2), so a chain headquarters sets standards while a single store keeps flexibility; Level 3 certification, self-service data export, and prescription permissions with a review workflow exist to pass regulation and to dispel the TCM hall's worry that SaaS will take its data — these three points map directly to the interoperability and security-certification requirements of the Specification for Information and Digitalization Construction of TCM Hospitals (that specification name is quoted in Karrytech's FAQ).

---

### 2. 云中医 (九明珠)

**Form**: cloud platform. The official site splits into five systems — counter business, physician-assisted diagnosis and treatment, pharmacy purchase/sales/inventory, financial settlement, business decision-making — plus self-service registration kiosks, queue-call screens, dispensing terminals and WeChat official account integration (sources: <https://www.yunzhongyi.com/>, <https://www.yunzhongyi.com/html/ClinicFunction.html>).

**Core function modules**: appointments, registration, pricing & charging, member stored value, treatment-course management, EMR, e-prescribing, smart prescribing, automatic prescription review, processed herb/granule/patent medicine inventory, pills-powders-pastes-elixirs processing, decoction with courier delivery, physician commission and supplier settlement, business and patient analysis reports, chain data sharing (source: official version table <https://www.yunzhongyi.com/html/product_his.html>).

**Pricing model**: the official version table is the clearest — Mini edition (solo practice without staff) free, Standard edition (small clinics) free, Professional edition (large and chain) ¥2,980/year/store; data retention 12 months for Mini/Standard, permanent for Professional; terminal count 5/5/unlimited (same source). The free editions do customer acquisition; Professional monetizes chains and data retention.

**Interface**:

![云中医 registration & charging page: select physician – registration info – registration & charging, a three-part flow; supports membership card/cash/UnionPay/WeChat/Alipay and other payments](assets/yz-yuyue-guahao.jpg)
Figure 4 source: <https://www.yunzhongyi.com/html/ClinicFunction.html> (云中医 official clinic system function page, accessed 2026-10-08)

![云中医 physician workstation prescribing page: Chinese herbal formula entry, dosage and decoction method, save as agreed formula](assets/yz-kaichufang.jpg)
Figure 5 source: same as above

![云中医 pricing & charging page: tabs for Chinese herbs/patent medicines/paste products pricing, daily reconciliation, physician workload and more](assets/yz-huajia.jpg)
Figure 6 source: same as above

![云中医 appointment page](assets/yz-yuyue.jpg)
Figure 7 source: same as above

![云中医 member management page](assets/yz-huiyuan.jpg)
Figure 8 source: same as above

**TCM features**: four prescription types — Chinese herbal, patent medicine, acupuncture and physiotherapy; classic formulas, agreed formulas and historical formulas load in one click (the official homepage copy "prescribe in 30 seconds" is a vendor's own claim); automatic prescription review; the pharmacy separates processed herbs, granules and patent medicines into three inventories, supports pills-powders-pastes-elixirs processing and decoction courier slips (version table).

**Corresponding analysis — why it is designed this way**. The registration, pricing and prescribing screens share an identical layout: left module tree, top tabs, center form — this is designed for clinics where the front desk and the consulting physician share one machine, driving training cost to the minimum: a new hire can click through by watching the old system. Making "physician commission settlement", "supplier settlement" and "daily reconciliation" first-class functions shows the target customer is the private TCM hall that treats doctors as revenue-sharing partners — if settlement is unclear, doctors will not stay. Permanent data retention only for Professional is the free edition's retention hook: a solo practice runs for a year, its historical charts remain on the platform, and upgrading becomes a migration cost. Its TCM features lean to the "dispensing side" rather than the "pattern differentiation side" — there is prescription review and multi-dosage-form inventory but no pattern-type recommendation; it is positioned as an operations system that keeps the pharmacy and the accounts in order.

---

### 3. ABC 诊所管家 (Chengdu, ABC 数字医疗云)

**Form**: SaaS. The official homepage claims 100,000+ clinics in use (vendor's own claim, <https://www.abcyun.cn/>).

**Core function modules**: AI voice charting, AI tongue diagnosis/facial diagnosis, point-and-select chart input, 300+ chart templates, AI large-model diagnosis and prescribing recommendations, AI prescription review (formula incompatibility and medical-insurance violation warnings), 200,000+ drug package inserts, classic and proven formula library, drug receiving with expiry/stock/gross-margin alerts; the TCM features page adds pharmacy and external therapy (source: <https://www.abcyun.cn/features/tcm-specialties>).

**Pricing model**: the official site gives only "free trial" and a phone number; no public quote. The open platform sits on a separate domain (open.abcyun.cn), which means APIs are negotiated per partnership.

**Interface**:

![ABC TCM three-pane consultation interface: patient queue, chart with Chinese herbal/external therapy prescriptions, AI pattern differentiation points and recommended formulas on the right](assets/abc-tcm-workstation.webp)
Figure 9 source: <https://www.abcyun.cn/features/tcm-specialties> (official caption "ABC TCM specialty diagnosis and treatment function interface illustration", accessed 2026-10-08)

![ABC AI diagnosis and treatment configuration: dropdowns for diagnosis system / differentiation school / treatment method / number of ingredients, plus one-click adoption and AI diagnosis with prescribing](assets/abc-ai-diagnosis.webp)
Figure 10 source: same as above

**TCM features**: tongue photo capture and pulse selection are built directly into chart fields (Figure 9). External therapy prescriptions cover six categories — herbal application, acupuncture, moxibustion, cupping, gua sha and tuina — and the acupoint dictionary claims 898 standard acupoints (official site). The pharmacy supports Chinese/Western patent medicines, processed herbs and granules in separate storerooms with multi-person dispensing; once a prescription is charged it can drive a granule machine directly for automatic dispensing; there is also a "cloud pharmacy" fulfillment option and mini-program self-service refill. AI tongue and facial diagnosis claims 95% accuracy, and AI large-model diagnosis 98% (all vendor's own claims).

**Corresponding analysis — why it is designed this way**. The three panes each serve one person: the left pane, the patient queue, mirrors the front desk and queue calling; the center pane is the doctor's working surface; the right pane is the AI's opinion zone, and the placement of the "one-click adopt" button fixes the boundary that AI can only suggest and never sign — prescription responsibility stays with the doctor; this interaction boundary is drawn by medical compliance requirements, not product preference. Voice charting and point-and-select input address the grassroots reality of not enough time to write: the vendor's figure is 90% of charting time saved, but broken into actions there are only two — speaking and selecting — neither requiring typing. Granule machine integration and the cloud pharmacy sit in the pharmacy module because decoction service and granules are the hall's profit items; one fewer manual transcription step is one fewer wrong-medicine risk, and this is also what the processed-herb regulation's "traceability" requirement looks like once it lands on the interface.

---

### 4. 一心医学中医诊所管理系统 (Shenzhen)

**Form**: Windows desktop software, single-machine deployment (one computer acts as the server); the official site states Windows only (source: <http://www.yixinyixue.com/aizs.html>).

**Core function modules** (12 items on the official site): smart formula recommendation, acupuncture plans, EMR, e-prescribing, outpatient charging, physician commission, membership management, appointment & registration, customer follow-up, return-visit reminders, pharmacy management, data statistics. The charging module covers prescription charging, monthly/yearly statistics, refunds and physician performance commission; the pharmacy covers dispensing, purchase plans, receiving, loss reporting and stocktaking.

**Pricing model**: 3-day trial; purchase requires submitting clinic license photos and a licensed physician qualification certificate; annual payment or a one-time purchase of usage rights (same source). This is the traditional license model: no cloud subscription, no ongoing data hosting.

**Interface**:

![一心医学 diagnosis page: patient list on the left, chief complaint / history / tongue presentation / pulse presentation / pattern differentiation / prescription in the center, formula and acupuncture reference on the right](assets/yixin-clinic-workstation.jpg)
Figure 11 source: <http://www.yixinyixue.com/aizs.html> (official caption "TCM clinic management software (TCM hall management system) diagnosis module", accessed 2026-10-08)

![一心医学 pharmacy management: sales outbound, purchase plan sheets, goods receiving, loss, inventory statistics](assets/yixin-pharmacy.jpg)
Figure 12 source: same as above

![一心医学 charging and reports: charging module, monthly statistics, yearly statistics](assets/yixin-billing-report.jpg)
Figure 13 source: same as above

![一心医学 customer records: registration, booked/pending follow-up/return-visit customers, top-up, service packages, spending and member balance](assets/yixin-customer.jpg)
Figure 14 source: same as above

**TCM features**: prescribing by formula name auto-generates the prescription; symptom-pattern prescription templates; acupuncture plans (claimed 1000+ acupuncture prescriptions across symptoms and diseases); intake assistance designed on the Ten Questions Song to prevent missed questions; and a formula detail popup listing contraindicated populations with "modern full dose" and "combined formula" buttons (Figure 11). It squeezes acupuncture, chart and prescription onto one page, and the right pane of Figure 11 also carries a "formula reference / acupuncture plans (8 sets)" toggle.

**Corresponding analysis — why it is designed this way**. The target users are solo TCM doctors and small halls, whose decision factors are price and keeping data in their own hands, hence the standalone buyout: prescribing works offline, and the vendor cannot walk away with anything — at the cost of no mobile client, no chain aggregation, and upgrades via reinstall. Writing contraindications under the formula name in the popup is for junior doctors — such customers are often freshly trained doctors who most fear stepping on a contraindication when prescribing classic formulas; the warning matters more than the recommendation. Follow-up and return visits as two buttons on top of a list (Figure 14) match TCM care's repurchase logic: a finished treatment course needs someone to remind the patient to return — a low priority in a Western clinic, a revenue source in a TCM hall. Putting monthly/yearly reports next to commission on the charging page shows the buyer is the owner personally; the reports must let him reconcile and split the money directly.

---

### 5. 卫宁健康 (WiNEX / outpatient information system)

**Form**: listed healthcare IT vendor delivering to hospitals as projects. Under IDC's "China Hospital Core Clinical System Market Share, 2024" it holds 12.6% (cited from <http://www.cn-witmed.com/list/8/20398.html>); product lines are HIS, EMR, PACS, LIS and integration platform; WiNEX shipped in 2020 and WiNEX2025 in 2025 (vendor's own claim, <https://www.cinn.cn/hz/2025/10-21/Zr5zYyzD.html>).

**Core function modules**: outpatient doctor station, inpatient doctor station, ward nurse station, medical record management, etc.; the WiNEX outpatient doctor station puts consultation, orders and charting on one screen, with chief-complaint search, ICD-11 diagnosis, body-diagram click-to-order exams, smart common-drug recommendations, over-prescription risk alerts, and in-clinic registration (source: 健康界 report <https://www.cn-healthcare.com/article/20210803/content-558191.html>). TCM hospital scenarios are customized per tender documents; the official site has no standalone "TCM system" product page.

**Pricing model**: government tender procurement. A publicly checkable award is the Leshan People's Hospital project in Sichuan (2024-08-30, Sichuan provincial government procurement site): total winning bid ¥26.36 million, of which the **Outpatient Information System V6.0 alone was ¥1.38 million per set**, outpatient pharmacy management ¥217,000, outpatient nurse infusion ¥224,000, and time-slotted outpatient second check-in ¥205,000 (<https://ggzyjy.sc.gov.cn/jyxx/002002/002002003/20240830/8a69e1999184f5f101919dd2e09f0606.html>). Under broker research report figures, a single Winning county-hospital IT project mostly lands at ¥10–20 million (<https://pdf.dfcfw.com/pdf/H3_AP202408111639231211_1.pdf>).

**Interface**:

![卫宁 WiNEX outpatient doctor station: patient info and allergy flags on top, chief complaint/diagnosis/exam quick area on the left, treatment content in the center, chart records and action buttons on the right](assets/winning-windex-outpatient-station.png)
Figure 15 source: <https://www.cn-healthcare.com/article/20210803/content-558191.html> (illustration "WiNEX outpatient doctor station interface" in 健康界's article "How well does 卫宁健康's WiNEX system perform?", accessed 2026-10-08)

**TCM features**: no public TCM-dedicated module. When TCM hospitals procure, tongue/pulse, pattern differentiation and processed-herb prescriptions are written into the tender's technical parameters and realized as project customization — publicly checkable Winning award lists include "Dongming County TCM Hospital informatization construction project, ¥15.5 million" and "Tianjin Academy of TCM affiliated hospital information integration upgrade, ¥15.88 million" (broker research report figures), showing TCM requirements are absorbed as projects, not sold as standard products.

**Corresponding analysis — why it is designed this way**. This screen is designed for a hospital doctor seeing dozens of patients a day: the top bar keeps allergy, insurance type and chronic-disease tags as always-visible color blocks — a medical safety requirement, allergies must be seen before prescribing; the left side turns exams, labs and imaging into quick buttons plus a body diagram, because in hospital workflow ordering tests is more frequent than writing charts, and one fewer page jump is one shorter queue. Its complexity comes from roles and compliance: the doctor station must hook into queue calling, in-clinic charging and record quality control, none of which exists in a TCM hall. The million-yuan price has the same reason: what is bought is integration with in-hospital LIS/PACS/insurance interfaces, acceptance and three years of operations — the software itself is only one line item in the bid (see the itemized quote above). For a buyer at TCM hall scale, this tier buys "not having to run your own IT", not "more features".

---

### 6. Same-tier large vendor comparison (东华医为 / 创业慧康 / 用友医疗 / 金蝶医疗)

This group gets no per-vendor sections, for the reason stated up front: all four official sites and public channels lead with solution diagrams and architecture diagrams, and this survey **obtained not a single confirmable software UI screenshot** (东华医为's official site <http://www.mediway.com.cn/> timed out on this pass; the others' product pages show no UI). Features and prices still have checkable public evidence, listed in the table.

| Vendor | Public products and positioning | Checkable price evidence | TCM relevance |
| ---- | ---- | ---- | ---- |
| 东华医为 (iMedical/HOS) | Smart hospitals, regional healthcare, internet healthcare, insurance cost control; HOS is delivered on a subscription (annual) basis | Two-hospital merged HIS multi-campus retrofit project won at ¥2.8 million (Wencheng County, Zhejiang government procurement, 2024-10-25, <https://www.wl.gov.cn/art/2024/10/25/art_1456452_59065648.html>) | No public description of a TCM-dedicated module found |
| 创业慧康 | Cloud HIS, internet hospitals, regional healthcare; high coverage of grassroots and TCM outpatient (industry article figure) | Jan–Nov 2024 cumulative winning bids ¥813 million, tens of millions monthly (broker research report figure, <https://pdf.dfcfw.com/pdf/H3_AP202412061641205136_1.pdf>) | Industry article claims TCM outpatient coverage, no feature details |
| 用友医疗 | Hospital HIS, HRP, medical consortium solutions; strong on materials and finance | Third party: custom quote by module and user count; Yonyou ERP basic from ¥580/year covers purchase/sales/inventory only, not a healthcare system | No TCM features described |
| 金蝶医疗 | Strong on private healthcare and DRG settlement tools (industry article figure) | Industry article says from ¥30,000–100,000 project-based; the article is from a competitor site, reference only | No TCM features described |

**Corresponding analysis — why it is designed this way (and why this group yields no UI)**. Their customers are hospitals and health commissions; procurement judges qualifications, case counts, interface lists and acceptance clauses, and the official site's role is brand endorsement before bidding — no need to show operating UI; what actually closes the deal is the technical parameters in the tender, not screenshots. This explains two things: why this tier's prices run into millions (integration and operations dominate), and why TCM features can only be customized inside tender documents (there is no standardized product to sell). For a TCM hall project, the borrowable point of this group is the procurement structure — itemized quotes by module and one year of post-acceptance operations written into the contract — not their interfaces.

---

## 2. Overseas clinic EHR and TCM vertical products

The shared premise of the overseas group: US outpatient care is centered on insurance claims (RCM); appointments, charting, prescribing and claims are sold as one bundle; TCM/acupuncture is not in the mainstream products' field systems, and vertical products fill the gap.

### 7. Practice Fusion (USA, under Veradigm)

**Core function modules**: charting, appointment scheduling, e-prescribing (including controlled substances EPCS), lab and imaging orders, patient portal, MIPS/compliance reports; the billing service (RCM) is a separate add-on requiring a quote (source: <https://www.practicefusion.com/pricing/>).

**Pricing model**: **USD 199/physician/month, single tier, annual commitment, billed monthly**; includes 3 signing accounts and unlimited non-signing accounts (front desk, document upload are not billed; same source). Third-party write-ups note the billing service and sales tax are not included in the USD 199 (<https://pabau.com/blog/practice-fusion-pricing/>). The official site also posts the ONC-mandated cost disclosure sheet (xlsx) download — compliance requires publishing the price composition.

**Interface**:

![Practice Fusion patient overview: left navigation, patient tabs, diagnosis list (ICD-10), allergy and medication panels](assets/pf-charting.webp)
Figure 16 source: <https://www.findemr.com/practice-fusion-ehr> (Practice Fusion screenshot carried by the third-party review site FindEMR, accessed 2026-10-08; the official site itself publishes no UI images)

![Practice Fusion SOAP chart editing](assets/pf-soap-notes.webp)
Figure 17 source: same as above

**TCM features**: none. Neither the official site nor the feature pages have acupuncture, herbal or TCM fields.

**Corresponding analysis — why it is designed this way**. The single USD 199 tier is for 1–5 person independent practices: they have no procurement manager and do not want to compare four packages; a single price cuts the decision cost to "try it for two weeks"; unlimited non-signing accounts get the front desk, nurses and part-timers on board, and per-physician billing avoids the internal friction of "one more person, one more fee". The chart interface places diagnosis, allergies and medications side by side on the first screen (Figure 16) — an arrangement demanded jointly by insurance claims and medication safety: the diagnosis must carry an ICD-10 code before billing, and allergies must be visible before prescribing. Its interface carries no TCM fields not by omission — the target customers (general/family practitioners) simply never write them.

---

### 8. Tebra (Kareo + PatientPop merged, USA)

**Core function modules**: patient experience and marketing, EHR and billing as three separately purchasable products, or two bundles, Practice Essentials / Practice Automation; includes e-prescribing, lab orders, HIPAA telehealth, patient portal, claim eligibility verification (2700+ payers), AI note/voice/billing assistants (source: <https://www.tebra.com/pricing/overview>).

**Pricing model**: the official site lists 7 tiers — marketing from USD 49, EHR Starter from USD 99, Billing Starter from USD 99, therapist 225, non-physician clinician (NP/PA) 399, physician 599, automation tier 799 USD/physician/month; the low-volume tier (≤100 claims/month) is cheaper, with automatic upgrade after 3 consecutive months over the cap; AI add-ons USD 99–149/physician/month (same source). Payment processing 2.75% + USD 0.30 per transaction, claim attachments USD 0.99 each, etc. are written on the pricing policy page (<https://www.tebra.com/tebra-pricing-policy>).

**Interface**:

![Tebra EHR interface: patient list on the left, patient details in the center, visit history filters on the right, calendar and telehealth scheduling below](assets/tebra-product.png)
Figure 18 source: <https://www.tebra.com/> (official homepage product image, accessed 2026-10-08)

**TCM features**: none. The non-physician tier covers chiropractic, psychotherapy and the like, with no TCM fields.

**Corresponding analysis — why it is designed this way**. The seven price tiers are not for users to pick from; they encode "which kind of practitioner are you": the physician/non-physician/therapist split maps to scope of practice and claims capability, and the low-volume tier bills by actual claim volume so as to capture part-timers and newly opened practices — the customers most afraid of annual contracts; usage-based pricing drops the entry bar from USD 599 to USD 99. The interface puts calendar and telehealth below the chart page (Figure 18), because in an independent practice scheduling and seeing patients are run by the same person, and splitting them into two systems only adds a switch. The marketing module (site building, SEO, reviews) is placed at the first price point, which shows the company positions itself not just as a recording tool but as customer acquisition for the practice — Kareo's billing genes plus PatientPop's acquisition genes; the pricing structure is the merged result.

---

### 9. athenahealth athenaOne (USA)

**Core function modules**: EHR (athenaClinicals), billing (athenaCollector), patient engagement, telehealth, plus an integration marketplace of 300+ partners; the claims rule base is maintained centrally across the network, so clinics do not keep their own rules (source: <https://www.athenahealth.com/solutions/athenaone>).

**Pricing model**: the official position is **a percentage of the clinic's collections**, no long-term contract, data can be taken away at any time, and the percentage is not published (same source). Third-party industry estimates put it at USD 140/physician/month base plus 4–8% of collections, with implementation at USD 15,000–40,000 (<https://transcure.net/medical-billing/software/emr/athenahealth-pricing/>, <https://saasrat.com/products/athenahealth>), marked as estimates, not an official price list.

**Interface**:

![athenaOne EHR interface: chart sections on the left (allergies/medications/immunizations/vitals/results/visits), diagnosis and care gaps in the center, past-diagnosis tracking on the right](assets/athena-ehr-interface.png)
Figure 19 source: <https://www.athenahealth.com/solutions/athenaone> (official product page screenshot, accessed 2026-10-08, with the page's bottom cookie bar cropped out)

**TCM features**: none.

**Corresponding analysis — why it is designed this way**. The percentage model ties vendor revenue to clinic revenue, so the product must claw the money back for the clinic: the "care gaps / CMS-HCC scores" block on the right of the interface is not a clinical tool but a claims tool, prompting the doctor which diagnoses are not yet coded into the chart — an unwritten one is an uncollected one. The rule base lives network-side and updates centrally because US payer rules change yearly and small clinics cannot afford a compliance team; the vendor turned that into a scale business. For TCM halls the reference value lies in the "bill as a share of revenue" logic itself; the concrete implementation (insurance claims, HCC coding) does not apply domestically.

---

### 10. eClinicalWorks (USA)

**Core function modules**: cloud EHR, telehealth, patient engagement (healow), revenue cycle management; the official homepage and EHR page emphasize the AI suite and RCM visualization (source: <https://www.eclinicalworks.com/products/ehr.html>).

**Pricing model**: no official numbers; third-party figures are USD 449/physician/month for the EHR and USD 599/physician/month with PM, with RCM at another 2.9% of collections (<https://softwarefinder.com/resources/practice-fusion-vs-eclinicalworks>, <https://startupstash.com/electronic-medical-records-emr-tools/>), marked as third-party figures.

**Interface**:

![eClinicalWorks revenue cycle management interface: weekly receivables movement on iPhone and monthly revenue analysis on iPad, multi-dimensional CPT/ICD breakdowns](assets/ecw-rcm-ui.webp)
Figure 20 source: <https://www.eclinicalworks.com/products/ehr.html> (illustration on the official EHR page, accessed 2026-10-08)

**TCM features**: none.

**Corresponding analysis — why it is designed this way**. Putting the revenue analysis chart in the most prominent spot on the product page shows the core promise sold to clinic owners is "the money can be accounted for": receivables movement, claims-completion delay, average ticket and revenue split by CPT are the four numbers an operator checks daily; the clinical interface is deliberately not used as marketing. The mobile end comes in two granularities, weekly and monthly reports, matching an owner who does not sit at a desk. For TCM halls the lesson of this vendor is the report layering — daily close, weekly trend, monthly structure is more useful than a single total-revenue chart.

---

### 11. Epic (USA, hospital-grade)

**Core function modules**: Hyperspace/Hyperdrive clinical workstation, MyChart patient portal, Tapestry commercial insurance claims, Cosmos research data network; aimed at hospitals and large medical groups (sources: <https://www.epic.com/software/>, <https://open.epic.com/Hyperdrive>).

**Pricing model**: no published price list; project-based. Public cases: Health First, USD 160 million over five years; Sarasota Memorial, about USD 160 million (<https://www.beckershospitalreview.com/healthcare-information-technology/ehrs/florida-health-system-invests-160m-in-epic-6-things-to-know/>); Becker's tally of the ten most expensive Epic projects in 2024 runs from USD 50 million to USD 1.2 billion; the largest historical project, Kaiser, USD 4 billion; Sacra estimates implementation from USD 1 million to 500 million and annual license fees at about USD 1,500/physician (<https://sacra.com/c/epic/>).

**Interface**:

![Epic MyChart patient-side app interface (official App Store screenshot)](assets/epic-mychart-1.png)
Figure 21 source: Apple App Store, MyChart — Epic Systems Corporation (official screenshots obtained via the iTunes Search API, accessed 2026-10-08); the physician-side Hyperspace interface is bound by hospital confidentiality and HIPAA and was not obtained through public channels

**TCM features**: none.

**Corresponding analysis — why it is designed this way**. Epic's interface is barely shown publicly, because what it sells is implementation and integration: a hospital buying Epic buys cross-campus record interoperability, research data and ten years of operations; the operating interface is configured by the implementer to the hospital's workflows, so screenshots on the vendor site carry no decision value. The patient side, MyChart, is generously illustrated — it is the storefront by which the hospital retains patients and reduces phone inquiries; appointments, test results, bills and messages all live in one app, which is Epic's moat at the patient-facing front door. The only reference point for TCM halls is one: a patient self-service entry (appointments, reports, payments) is worth building early; the physician-side interface can neither be copied nor is it there for copying.

---

### 12. Jane App (Canada, common at wellness and acupuncture clinics)

**Core function modules**: scheduling (1-on-1/group/classes), online booking site, chart templates and outcome measures, telehealth, invoices and CMS1500, SMS reminders, AI notes, packages and memberships, waitlist management (source: <https://janeapp.com/pricing/>).

**Pricing model**: three official tiers — Balance USD 59/month (solo practitioner, capped at 20 appointments per month), Practice USD 79/month (includes 1 full-time; part-time USD 17.5/person/month, full-time USD 35/person/month), Thrive USD 99/month (rooms and equipment, waitlist, packages and memberships, reviews); insurance claims billing is an add-on (same source).

**Interface**:

![Jane schedule page: booking calendar in columns by service line, appointment detail panel, messaging and video consultation windows](assets/jane-scheduling-hero.png)
Figure 22 source: <https://janeapp.com/> (official homepage product screenshot, accessed 2026-10-08)

![Jane chart page](assets/jane-charting.png)
Figure 23 source: same as above

**TCM features**: no TCM-dedicated fields; it serves acupuncture and TCM practitioners through customizable chart templates; the official site positions it as general-purpose for health and wellness clinics.

**Corresponding analysis — why it is designed this way**. Jane's schedule page lays "different service lines side by side in columns" (Figure 22), directly matching the reality of acupuncture/physiotherapy halls where several practitioners book at the same time: massage, acupuncture and consulting run in parallel in one hall, and columns by person show open slots more easily than columns by time. Tying the first tier to appointment volume and pricing the later tiers by headcount aligns the price precisely with practice size — a solo part-timer can start at USD 59, the same idea as a domestic TCM hall buying the single-store edition first and the chain edition later. It has no TCM fields yet is popular among acupuncture practitioners, which shows **if scheduling and billing are done well, clinical fields can be added later through templates** — supporting evidence for a product route that does workflow first and TCM dictionaries second.

---

### 13. Unified Practice (USA, TCM vertical EHR + PM)

**Core function modules**: scheduling (by service/room/practitioner), charting, prescribing, invoicing, payments, real-time reports, patient communication, patient portal, patient segment marketing; the official tagline reads verbatim "Schedules. Communicates. Charts. Prescribes. Invoices. Manages." (source: <https://www.unifiedpractice.com/>).

**Pricing model**: no official numbers, free trial offered; third-party figures are USD 49–99/physician/month (Starter from USD 49; lower if unified payments are included, higher if not), plus USD 32–38/physician (annual-payment basis, <https://www.vendorpick.ai/acupuncture>).

**Interface**:

![Unified Practice chart interface](assets/unifiedpractice-charting.webp)
Figure 24 source: <https://www.unifiedpractice.com/> (official features section screenshot, accessed 2026-10-08)

![Unified Practice scheduling interface](assets/unifiedpractice-scheduling.webp)
Figure 25 source: same as above

**TCM features**: third-party reviews describe a built-in **acupuncture acupoint database and herbal formula library**, with charting flows designed to TCM habits (<https://www.vendorpick.ai/acupuncture>); the official tagline is "TCM Solutions Crafted by TCM Practitioners". Details on formulas and processed-herb inventory are not elaborated on the official site.

**Corresponding analysis — why it is designed this way**. A vertical product's reason to exist is fields: mainstream EHR chart templates are SOAP (subjective/objective/assessment/plan), while TCM writes pattern differentiation and formulas, and forcing that into SOAP means explaining every field. Unified Practice puts the acupoint library and formula library into the prescribing flow, eliminating the "flip the book – transcribe – verify" three steps; its users often practice one person per room, where time is revenue. Marketing segmentation is also inside the product, because US acupuncturists rely heavily on self-pay patients, and repurchase reminders matter more than insurance claims — this matches domestic TCM halls' membership and follow-up logic, only under different names.

---

### 14. myClinic (Singapore, TCM clinic SaaS)

**Core function modules**: registration, appointments, consultation, charging, staff and inventory; the official site explicitly says "front desk, physician and cashier share the same real-time state" (source: <https://www.sgmyclinic.com/zh>).

**Pricing model**: the FAQ says "clear itemized quotes by team size and scope, no hidden fees", with no public numbers; a demo takes about 15 minutes, go-live takes days, and data migration is supported.

**Interface**:

![myClinic appointment page: navigation on the left for operations/sales/finance/inventory/system, appointment status tabs, tongue and pulse written into the notes](assets/myclinic-patients.png)
Figure 26 source: <https://www.sgmyclinic.com/zh> (official "real interface showcase" section, accessed 2026-10-08)

![myClinic inventory and alerts page](assets/myclinic-inventory.png)
Figure 27 source: same as above

![myClinic monthly revenue report page](assets/myclinic-revenue.png)
Figure 28 source: same as above

**TCM features**: granules and consumables are managed **by the gram** with automatic out-of-stock and near-expiry alerts (official site); tongue and pulse presentation are written into the appointment notes (visible in Figure 26's notes column, including records such as "tongue and pulse" and "pale tongue with white coating, deep weak pulse"); the pattern-differentiation clues the physician needs are visible as soon as the patient file is opened.

**Corresponding analysis — why it is designed this way**. Singapore TCM clinics are mostly small and medium, self-pay heavy, and regulated under PDPA, so the product sells "shared real-time state" as the number-one feature: the front desk changes a status and the physician and cashier both see it at once, eliminating shouting across the shop — the biggest waste in a small hall is asking each other. Per-gram inventory pricing is the granule reality: a one-gram price difference times dozens of formulas a day is gross margin, so alerts are built before reports. Putting tongue and pulse in notes rather than structured fields is a lightweight trade-off: first make the information flow to the consulting room, and upgrade to fields when the customer has grown to the point of needing pattern-differentiation statistics. This "workflow before fields" ordering matches the conclusion on Jane in section 12.

---

## 3. Pricing model comparison

Conclusion first: 17 products use only five ways of collecting money, and choosing one largely decides who the product sells to. What fits TCM hall scale best is "per store per year + a low-barrier free tier", because it dodges both extremes — the implementation cost of hospital-grade projects, and the revenue coupling of a per-physician share of collections. Domestically someone has already cut the tiers (云中医, free/¥2,980); overseas the per-physician math is done in fine detail (Jane, Tebra); both ways of doing the arithmetic are worth copying — see section 5 for how.

| Revenue model | Representative products | Public numbers | Who pays | Evidence |
| ---- | ---- | ---- | ---- | ---- |
| Per store per year + free tier | 云中医 | Free / ¥2,980/year/store | Solo and chain TCM halls | Official version table |
| Annual or buyout desktop license | 一心医学 | 3-day trial; annual or one-time buyout | Solo clinics | Official site |
| SaaS subscription (no public numbers) | 康博嘉, ABC, myClinic | Official sites offer only trial and quote requests | Small and medium TCM halls | Official FAQ |
| Per-physician subscription | Practice Fusion, Tebra, Jane, Unified Practice | USD 199/physician/month; 49–799; 59/79/99; 49–99 | Independent practices | Each vendor's pricing page |
| Percentage of collections | athenahealth, eClinicalWorks RCM | Not officially published (third-party estimates 4–8% / 2.9%) | Clinics with insurance revenue | Official + third party |
| Tendered project delivery | 卫宁, 东华医为, Epic | Outpatient system ¥1.38 million/set; project ¥2.8 million; Epic USD 160 million | Hospitals and groups | Government procurement notices, Becker's |

---

## 4. TCM feature comparison

Conclusion first: the TCM features of the four domestic clinic products sit on the same chain — **chart records tongue and pulse → dictionary picks pattern type → formula library brings out the formula → processed herbs/granules/patent medicines in separate storerooms → prescription review and contraindication alerts → decoction/courier**; the difference is emphasis: 康博嘉 leans pattern-differentiation assistance, 云中医 leans pharmacy and settlement, ABC leans AI and external therapy with acupoints, 一心 leans formulas and acupuncture plans. For mainstream overseas EHRs this column is all "none", which is not a defect — the market never asked. The gap-filler Unified Practice only built an acupoint library and formula library, shallower than the domestic products. Tongue image capture (photo/tongue diagnostic device) currently has a UI entry point only at 康博嘉 and ABC, and no product has yet been seen turning pulse diagnosis into objective capture — pulse presentation remains a dropdown or point-select text field.

| TCM capability | 康博嘉 | 云中医 | ABC | 一心医学 | 卫宁 | Unified Practice | myClinic | Mainstream overseas EHR |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| Tongue presentation recording/capture | Field + smart recommendation | Photo capture | Photo + AI tongue diagnosis | Tongue field | Per-project customization | None | Note text | None |
| Pulse presentation recording | Field + smart recommendation | In EMR | Pulse selection | Pulse field | Per-project customization | None | Note text | None |
| Pattern type / differentiation recommendation | Pattern elements → pattern type → prescription | None (review only) | AI differentiation highlights and school settings | Recommends formulas by differentiation | None | None | None | None |
| Formula library | 400+ classic formulas (vendor's own claim) | Classic/agreed/historical formulas | Classic and proven formulas | Prescribe by formula name + contraindication popup | None | Formula library | None | None |
| Processed herb/granule/patent medicine multi-storeroom | Processed herbs, patent medicines, consumables | Processed herbs, granules, patent medicines | Chinese/Western patent medicines, processed herbs, granules | Five pharmacy modules | Per-project customization | Details not public | Granules by gram | None |
| Acupoint dictionary / external therapy | Not seen | Acupuncture prescription type | 898 acupoints, 6 external therapy types | 1000+ acupuncture plans | Per-project customization | Acupoint database | None | None |
| Prescription review / formula incompatibilities | Prescription permissions + review workflow | Automatic prescription review | AI review + insurance alerts | Formula contraindication alerts | Per-project customization | Not public | None | None |
| Decoction / processing / courier | Integrates decoction delivery platforms | Decoction courier, pills/powders/pastes/elixirs | Cloud pharmacy, direct granule machine | Decoction status column | None | None | None | None |

---

## 5. Applicable analysis

Conclusion first: all 17 products are classified one by one, each into exactly one table — **directly usable: 6** (the four domestic clinic products + Jane + Unified Practice, whose interfaces and fields can go straight into design drafts), **usable as reference: 5** (pricing and reporting practices, copy the idea not the implementation), **not applicable: 6** (the five hospital-grade project-delivery vendors + athenahealth, due to scale or business-model mismatch). The classification standard is a single test: put their practice into sinomed's prototype or feature list and see whether you can write the next todo; if you can, it's directly usable; if it only helps pricing and reports, it's usable as reference; if nothing comes out, it's not applicable.

### Directly usable (can go straight into design drafts or prototypes)

| Source | Verdict | Why | Lands in sinomed as |
| ---- | ---- | ---- | ---- |
| 康博嘉 chart item maintenance (Figure 2) | **Directly usable** | Configurable fields + "group > clinic > department > chart type" priority lets one chart template serve both chains and single stores; sinomed's consultation form fields are currently hardcoded in the `treats` table | A design draft for consultation-form field/template configuration; model the API as template–field–priority; on the roadmap |
| 一心医学 three-part consultation page (Figure 11) | **Directly usable** | Patient list \| chart & prescriptions \| formula/acupuncture reference, the same thinking as sinomed's existing consultation form (four examinations / pulse presentation / five phases / acupoint selection / diagnosis / plan); what it adds is the **formula popup with contraindications** and **pulling up the whole formula by name**; its customer list makes "pending follow-up" and "return visits" two pinned buttons (Figure 14), showing these two queues must be individually filterable in a TCM hall | Add a "formula pull-up + contraindication alerts" area to the Chinese herbal prescription prototype `admin-herbprescription` (needs a formula dictionary first); add a "return-visit customer" filter beside the follow-up to-do in the daily report |
| ABC three panes + AI suggestion area (Figure 9) | **Directly usable** | The accountability boundary of "AI only suggests, the doctor clicks adopt" is worth copying verbatim: a suggestion panel in its own pane, and only the adopt button writes into the chart | Reserve a "suggestions" sidebar slot and an adopt action on the diagnosis page; AI itself is unplanned — leave the structure first |
| Jane schedule columns by service line (Figure 22) | **Directly usable** | Multi-practitioner halls need per-person gap views; columns by time cannot show who is free | Finalize the appointment & scheduling prototype `admin-booking` with columns by physician/practitioner |
| myClinic inventory by the gram + alerts (Figure 27) | **Directly usable** | Processed herbs priced by the gram, out-of-stock and near-expiry alerts arrive before reports | Field and alert rule source for the pharmacy inventory module ("processed herbs inventory" is currently unimplemented in the feature list) |
| Unified Practice acupoint and formula libraries | **Directly usable** | The difference between a TCM vertical product and a mainstream EHR is exactly these two dictionaries: prescriptions pull formulas from a library instead of hand-copying | sinomed has no formula or acupoint dictionary yet (`acupoint_left/right` store plain text); build the dictionary tables first, then discuss prescription structuring |

### Usable as reference (copy the idea, not the implementation)

| Source | Verdict | Why | Lands in sinomed as |
| ---- | ---- | ---- | ---- |
| 云中医 three-tier pricing (official version table) | **Usable as reference** | The free tier keeps solo practices, the Professional tier charges chains; ¥2,980/year/store is the only domestic list price in this survey coming from an official version table | If a multi-store edition is built someday, cut tiers along "terminal count + data retention period + chain sharing" |
| Practice Fusion single-tier pricing | **Usable as reference** | A single USD 199 tier, non-signing accounts like the front desk are not billed, sparing small customers price comparison | If sinomed does subscriptions, define a single tier plus "staff free" first — do not launch with four tiers |
| Tebra's tiering algorithm | **Usable as reference** | Prices by physician/non-physician/therapist identity; ≤100 claims/month lands in the low tier, upgrade only after three over-cap months | Use as an accounting worksheet when discussing pricing; unrelated to product code |
| eClinicalWorks three report layers (Figure 20) | **Usable as reference** | Daily close, weekly trend and monthly structure each serve a purpose; one total-revenue chart is not enough | Add weekly/monthly views beside the daily report, within the existing reports module |
| Epic MyChart (Figure 21) | **Usable as reference** | A patient self-service entry (appointments/reports/payments/messages) precedes physician-side complexity | The "online booking" placeholder in the mini-program homepage prototype can complete its information architecture along these lines |

### Not applicable (reasons stated)

| Source | Verdict | Why |
| ---- | ---- | ---- |
| 卫宁 WiNEX, 东华医为, 创业慧康, 用友医疗, 金蝶医疗 | **Not applicable** | Project-based hospital HIS: million-yuan bids, customized per tender, integrated with insurance and in-hospital platforms; at TCM hall scale one can neither use that integration nor afford implementation and operations. The only borrowable piece is the procurement wording "itemized quotes by module, 1 year of post-acceptance operations" |
| athenahealth | **Not applicable** | Revenue depends on US insurance RCM and HCC/CPT coding; domestic TCM halls are mostly self-pay and membership, and a percentage model has no matching revenue to skim |

### Gap check against the feature list

Take the items that sinomed currently lists as "unimplemented" in the [feature list](/research/features) and rank them by how far commercial products have gotten (commercial products have it = right column of this table):

| Feature point | sinomed status | Where commercial products generally are | Suggested order |
| ---- | ---- | ---- | ---- |
| Appointment & registration | Implemented (create booking / awaiting arrival / reception-to-consult handoff + physician × time-slot scheduling grid + mini-program self-service booking with a no-login entry) | A first-class module in all four domestic products, including pick physician / registration / cancellation | 1 |
| Charging & settlement | Partial (settlement desk + stored value shipped; refunds/reconciliation/invoicing not done) | Pricing–charging–daily reconciliation–refund closed loop, with member card deduction | 2 |
| Chinese herbal prescription (processed herbs/dosage/number of doses) | Partial (structured model shipped: per-herb dosage/decoction method/number of doses; no herb dictionary/pricing, formula pull-up not done) | Structured to per-herb dosage, decoction method, number of doses; formula pull-up | 3 (build together with the formula dictionary) |
| Pharmacy and processed-herb inventory | Unimplemented | Five modules (outbound/purchasing/receiving/loss/inventory) + expiry alerts | 4 |
| Membership cards and stored value | Partial (`customers.level` + stored-value ledger wired up) | Stored value, discounts, packages, cross-store spending | 5 |
| Return visits and follow-up | Present (in the daily report) | Independent queues + due reminders | Already in place, add filters |
| Scheduling | Partial (physician × time-slot grid shipped; recurring shift patterns and online self-service booking not done) | Columns by physician calendar + online booking | 6 |
| Multi-store / chain | Unimplemented | Headquarters unified pricing, aggregated store data | Long-term |
| Formula / acupoint dictionaries | Unimplemented | Formula library and 898-acupoint dictionary are standard kit | Same batch as item 3 |

## Source list (all accessed 2026-10-08)

- Domestic official sites: <https://www.karrytech.com/>, <https://www.karrytech.cn/related_detail/1445.html>, <http://www.karrytech.com/share_detail/776.html>, <https://www.yunzhongyi.com/>, <https://www.yunzhongyi.com/html/ClinicFunction.html>, <https://www.yunzhongyi.com/html/product_his.html>, <https://www.abcyun.cn/>, <https://www.abcyun.cn/features/tcm-specialties>, <http://www.yixinyixue.com/aizs.html>, <https://www.winning.com.cn/>
- Domestic public reports and procurement: <https://www.cn-healthcare.com/article/20210803/content-558191.html> (卫宁 UI), <https://ggzyjy.sc.gov.cn/jyxx/002002/002002003/20240830/8a69e1999184f5f101919dd2e09f0606.html> (卫宁 itemized quote), <https://www.wl.gov.cn/art/2024/10/25/art_1456452_59065648.html> (东华医为), <https://pdf.dfcfw.com/pdf/H3_AP202412061641205136_1.pdf> (创业慧康/卫宁 wins)
- Overseas official sites: <https://www.practicefusion.com/pricing/>, <https://www.tebra.com/pricing/overview>, <https://www.tebra.com/tebra-pricing-policy>, <https://www.athenahealth.com/solutions/athenaone>, <https://www.eclinicalworks.com/products/ehr.html>, <https://www.epic.com/software/>, <https://janeapp.com/pricing/>, <https://www.unifiedpractice.com/>, <https://www.sgmyclinic.com/zh>
- Third parties (price and UI supplements, all marked as such): <https://www.findemr.com/practice-fusion-ehr>, <https://pabau.com/blog/practice-fusion-pricing/>, <https://transcure.net/medical-billing/software/emr/athenahealth-pricing/>, <https://www.vendorpick.ai/acupuncture>, <https://www.beckershospitalreview.com/healthcare-information-technology/ehrs/florida-health-system-invests-160m-in-epic-6-things-to-know/>, <https://sacra.com/c/epic/>
