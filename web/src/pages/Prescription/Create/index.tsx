import React, { useEffect, useRef, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, Checkbox, Input, InputNumber, Modal, Radio, Select, message } from 'antd';
import { CheckCircleFilled, MinusCircleOutlined, PlusOutlined, WarningFilled } from '@ant-design/icons';
import moment from 'moment';
import {
  checkAllergy,
  checkCompatibility,
  checkDose,
  createPrescription,
  createTemplate,
  pricePrescription,
  queryEnabledTemplates,
  type AllergyResult,
  type CompatibilityResult,
  type DoseResult,
  type PrescriptionHerb,
  type PrescriptionTemplate,
  type Pricing,
} from '@/services/ant-design-pro/prescription';
import { fetchCustomerByPhone, listCustomerHistories } from '@/services/ant-design-pro/customer';
import { queryFormulaPage, type Formula } from '@/services/ant-design-pro/formula';
import { queryHerbByPage, type Herb } from '@/services/ant-design-pro/herb';
import { queryStaffByPage } from '@/services/ant-design-pro/staff';
import { queryTreatByPage } from '@/services/ant-design-pro/treat';
import { pinyin } from 'pinyin-pro';
import './index.less';

/** 会员等级展示口径（同顾客档案）：1 普通 / 2 银卡 / 3 金卡 */
const LEVEL_TEXT: Record<number, string> = { 1: '普通会员', 2: '银卡会员', 3: '金卡会员' };

/** 分 → 元（计价展示） */
const fenToYuan = (fen?: number) => `¥${((fen ?? 0) / 100).toFixed(2)}`;

/** 手机掩码：137****3333（原型 rx-head 口径） */
const maskPhone = (p?: string) => (p ? p.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '');

/** 特殊煎法提醒关键词：命中即计入「N 味需标注」 */
const SPECIAL_FX = /先煎|后下|包煎|烊化|冲服|另煎/;

/** 药名 → 拼音首字母串（目录检索用，如 黄芪→hq）；同名反复输入，带缓存避免逐键重算 */
const initialsCache = new Map<string, string>();
const herbInitials = (name: string) => {
  let v = initialsCache.get(name);
  if (v === undefined) {
    v = pinyin(name, { pattern: 'first', toneType: 'none', type: 'array' }).join('');
    initialsCache.set(name, v);
  }
  return v;
};

type CustomerBrief = { id?: number; name?: string; phone?: string; level?: number; gender?: number; age?: number };

const Create: React.FC = () => {
  const [customer, setCustomer] = useState<CustomerBrief | undefined>();
  const [phone, setPhone] = useState('');
  const [finding, setFinding] = useState(false);
  /** 接诊单联动：该顾客的诊疗记录（时间倒序），默认带最近一张的辨证与治法/方案 */
  const [treats, setTreats] = useState<API.Treat[]>([]);
  const [treatId, setTreatId] = useState<number | undefined>();
  const [staffOptions, setStaffOptions] = useState<{ label: string; value: number }[]>([]);
  const [staffId, setStaffId] = useState<number | undefined>();
  const [herbs, setHerbs] = useState<PrescriptionHerb[]>([{ herb: '', weight: undefined }]);
  const [doses, setDoses] = useState<number>(7);
  /** 处方类型：0 汤剂 / 1 膏方（膏方开方落「待制作」，领取流转在处方查询页） */
  const [ptype, setPtype] = useState<number>(0);
  /** 收膏方式：仅膏方，随方记录 */
  const [craft, setCraft] = useState<string | undefined>();
  /** 代煎：袋数=剂数，开方后落「待煎」，领取流转在处方查询页 */
  const [decoction, setDecoction] = useState(false);
  /** 领取方式：0 自取 / 1 同城配送（仅代煎有意义，随方存档） */
  const [delivery, setDelivery] = useState<number>(0);
  /** 签名行：药师审核 / 调配复核（可空，随方存档） */
  const [reviewStaffId, setReviewStaffId] = useState<number | undefined>();
  const [checkStaffId, setCheckStaffId] = useState<number | undefined>();
  /** 存入我的方剂库：弹窗填病症名（唯一 1–20 字） */
  const [tplModal, setTplModal] = useState(false);
  const [tplName, setTplName] = useState('');
  const [savingTpl, setSavingTpl] = useState(false);
  const [usage, setUsage] = useState('');
  const [takeMethod, setTakeMethod] = useState('');
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  const [compat, setCompat] = useState<CompatibilityResult | undefined>();
  const [checking, setChecking] = useState(false);
  const compatSeq = useRef(0);
  /** 过敏审方：按顾客过敏史比对药味（提示不拦截），定位顾客后随写随查 */
  const [allergy, setAllergy] = useState<AllergyResult | undefined>();
  const allergySeq = useRef(0);
  /** 顾客过敏史条数（type 0）：无命中时的 ✓ 常显态文案来源 */
  const [allergyRecordCount, setAllergyRecordCount] = useState(0);
  /** 剂量审方：按药材字典单剂上限比对药味单剂克数（提示不拦截） */
  const [dose, setDose] = useState<DoseResult | undefined>();
  const doseSeq = useRef(0);
  const [pricing, setPricing] = useState<Pricing | undefined>();
  const priceSeq = useRef(0);
  /** 病症处方模板：套用后只填当前开方单，不写回模板 */
  const [templates, setTemplates] = useState<PrescriptionTemplate[]>([]);
  const [templateId, setTemplateId] = useState<number | undefined>();
  const [applying, setApplying] = useState(false);
  /** 方剂库：按方名/拼音检索带出全方，不写回方剂库 */
  const [formulas, setFormulas] = useState<Formula[]>([]);
  const [formulaId, setFormulaId] = useState<number | undefined>();
  /** 饮片目录：字典全量做检索（演示字典规模小，一次拉齐；拼音首字母在过滤时算并缓存） */
  const [herbDir, setHerbDir] = useState<Herb[]>([]);
  const [dirLoading, setDirLoading] = useState(false);

  const loadTemplates = async () => {
    if (templates.length > 0) return;
    const list = await queryEnabledTemplates();
    setTemplates(list || []);
  };

  const loadHerbDir = async () => {
    if (herbDir.length > 0 || dirLoading) return;
    setDirLoading(true);
    try {
      const res = await queryHerbByPage({ current: 1, pageSize: 500 });
      setHerbDir(res?.data || []);
    } finally {
      setDirLoading(false);
    }
  };

  /** 目录检索：中文名包含或拼音首字母命中（原型口径 zz / fs / gcao） */
  const filterHerbDir = (input: string, option?: { value?: React.Key }) => {
    const q = input.trim().toLowerCase();
    if (!q) return true;
    const name = String(option?.value ?? '');
    return name.toLowerCase().includes(q) || herbInitials(name).includes(q);
  };

  /** 从目录选中：填入第一个空药味行，无空行则追加 */
  const pickHerb = (name: string) => {
    setHerbs((rows) => {
      const empty = rows.findIndex((r) => !(r.herb || '').trim());
      if (empty >= 0) return rows.map((r, k) => (k === empty ? { ...r, herb: name } : r));
      return [...rows, { herb: name, weight: undefined }];
    });
  };

  const applyTemplate = async (id: number) => {
    setTemplateId(id);
    const tpl = templates.find((t) => t.id === id);
    if (!tpl) return;
    if (tpl.herbs && tpl.herbs.length > 0) {
      setApplying(true);
      try {
        setHerbs(tpl.herbs.map((h) => ({ herb: h.herb || '', weight: h.weight, special: h.special || undefined })));
        if (tpl.doses && tpl.doses > 0) {
          setDoses(tpl.doses);
        }
        setDecoction(tpl.decoction === 1);
        if (tpl.usage) {
          setUsage(tpl.usage);
        }
        if (tpl.remark) {
          setRemark(tpl.remark);
        }
        message.success(`已套用模板「${tpl.name}」，可继续增减药味`);
      } finally {
        setApplying(false);
      }
    }
  };

  const loadFormulas = async () => {
    if (formulas.length > 0) return;
    const page = await queryFormulaPage({ current: 1, pageSize: 50 });
    setFormulas(page?.data || []);
  };

  const applyFormula = (id: number) => {
    setFormulaId(id);
    const fml = formulas.find((f) => f.id === id);
    if (!fml || !fml.herbs || fml.herbs.length === 0) return;
    setHerbs(fml.herbs.map((h) => ({ herb: h.herb || '', weight: h.weight, special: h.special || undefined })));
    message.success(`已按方剂「${fml.name}」带出全方 ${fml.herbs.length} 味，可继续增减`);
  };

  // 配伍审方：药名停顿 500ms 自动比对十八反/十九畏；少于两味不查
  const namedHerbs = herbs.map((r) => (r.herb || '').trim()).filter(Boolean);
  useEffect(() => {
    if (namedHerbs.length < 2) {
      setCompat(undefined);
      setChecking(false);
      return;
    }
    const seq = ++compatSeq.current;
    setChecking(true);
    const timer = setTimeout(async () => {
      try {
        const result = await checkCompatibility(namedHerbs);
        if (seq === compatSeq.current) {
          setCompat(result);
        }
      } catch {
        if (seq === compatSeq.current) {
          setCompat(undefined);
        }
      } finally {
        if (seq === compatSeq.current) {
          setChecking(false);
        }
      }
    }, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [namedHerbs.join('|')]);

  // 过敏史条数：定位顾客即取（过敏史 ✓ 常显态文案：无记录 / 有记录未命中）
  useEffect(() => {
    if (!customer?.id) {
      setAllergyRecordCount(0);
      return;
    }
    let cancelled = false;
    listCustomerHistories(String(customer.id))
      .then((list) => {
        if (!cancelled) setAllergyRecordCount((list || []).filter((h) => h.type === 0).length);
      })
      .catch(() => {
        if (!cancelled) setAllergyRecordCount(0);
      });
    return () => {
      cancelled = true;
    };
  }, [customer?.id]);

  // 过敏审方：定位顾客后按过敏史比对药味；未定位顾客或无药名即清空
  useEffect(() => {
    if (!customer?.id || namedHerbs.length < 1) {
      setAllergy(undefined);
      return;
    }
    const seq = ++allergySeq.current;
    const timer = setTimeout(async () => {
      try {
        const result = await checkAllergy(customer.id!, namedHerbs);
        if (seq === allergySeq.current) {
          setAllergy(result);
        }
      } catch {
        if (seq === allergySeq.current) {
          setAllergy(undefined);
        }
      }
    }, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [namedHerbs.join('|'), customer?.id]);

  // 剂量审方：按药材字典单剂上限比对（提示不拦截），与计价同款防抖；无药名即清空
  useEffect(() => {
    if (namedHerbs.length < 1) {
      setDose(undefined);
      return;
    }
    const seq = ++doseSeq.current;
    const timer = setTimeout(async () => {
      try {
        const result = await checkDose(
          herbs.filter((r) => (r.herb || '').trim()).map((r) => ({ herb: r.herb!, weight: r.weight })),
        );
        if (seq === doseSeq.current) {
          setDose(result);
        }
      } catch {
        if (seq === doseSeq.current) {
          setDose(undefined);
        }
      }
    }, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [namedHerbs.join('|'), herbs.map((r) => String(r.weight ?? '')).join('|')]);

  // 计价：同款防抖，按药材字典实时试算；无药名即清空
  useEffect(() => {
    if (namedHerbs.length < 1) {
      setPricing(undefined);
      return;
    }
    const seq = ++priceSeq.current;
    const timer = setTimeout(async () => {
      try {
        const result = await pricePrescription({
          doses,
          herbs: namedHerbs.map((n) => ({ herb: n })),
        });
        if (seq === priceSeq.current) {
          setPricing(result);
        }
      } catch {
        if (seq === priceSeq.current) {
          setPricing(undefined);
        }
      }
    }, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [namedHerbs.join('|'), doses]);

  const loadStaff = async () => {
    if (staffOptions.length > 0) return;
    const staffs = await queryStaffByPage({ current: 1, pageSize: 100 });
    setStaffOptions((staffs?.data || []).map((s) => ({ label: s.name || `员工${s.id}`, value: s.id! })));
  };

  const findCustomer = async () => {
    const p = phone.trim();
    if (!/^1\d{10}$/.test(p)) {
      message.warning('请输入 1 开头的 11 位手机号');
      return;
    }
    setFinding(true);
    try {
      const found = await fetchCustomerByPhone(p);
      if (found?.id) {
        setCustomer(found);
        const page = await queryTreatByPage({ current: 1, pageSize: 20, customerId: found.id });
        const list = page?.data || [];
        setTreats(list);
        setTreatId(list[0]?.id);
      } else {
        setCustomer(undefined);
        setTreats([]);
        setTreatId(undefined);
        message.warning('未找到该手机号顾客，请先在「顾客 → 新建顾客」建档');
      }
    } finally {
      setFinding(false);
    }
  };

  const setHerb = (i: number, patch: Partial<PrescriptionHerb>) => {
    setHerbs((rows) => rows.map((r, k) => (k === i ? { ...r, ...patch } : r)));
  };

  const addHerb = () => setHerbs((rows) => [...rows, { herb: '', weight: undefined }]);
  const removeHerb = (i: number) => setHerbs((rows) => rows.filter((_, k) => k !== i));

  /** 当前选中的接诊单：辨证（诊断）与治法/方案直读接诊单自由文本 */
  const treat = treats.find((t) => t.id === treatId);

  /** 处方笺头（原型 rx-head）：顾客 女·38 岁·掩码手机 / 医师·日期·诊号 / 处方编号预览（编号开方后生成） */
  const staffName = staffOptions.find((s) => s.value === staffId)?.label;
  const genderText = customer?.gender === 2 ? '女' : customer?.gender === 1 ? '男' : '';
  const rxNo = `Rx-${moment().format('YYYYMMDD')}-开方后生成`;
  /** 特殊煎法提醒：扫描药味备注命中关键词的味数（原型审方四查之一） */
  const specialFxCount = herbs.filter((r) => SPECIAL_FX.test(r.special || '')).length;

  /** 三行账应收（2026-10-12 拍板，对齐原型 .fee 口径）：饮片费 + 代煎服务费（勾代煎时 ¥3/袋）；运费不进合计 */
  const decoctFeeFen = doses * 300;
  const recvFen = (pricing?.totalFen ?? 0) + (ptype === 0 && decoction ? decoctFeeFen : 0);

  /** 存入我的方剂库：当前方随病症名存为启用模板（不写回、不影响开方单） */
  const saveAsTemplate = async () => {
    const name = tplName.trim();
    if (!name || name.length > 20) {
      message.warning('病症名限 1–20 字');
      return;
    }
    const rows = herbs.filter((r) => (r.herb || '').trim() !== '' && r.weight);
    if (rows.length === 0) {
      message.warning('处方至少要有 1 味已写剂量的药');
      return;
    }
    setSavingTpl(true);
    try {
      await createTemplate({
        name,
        doses,
        decoction: decoction ? 1 : 0,
        usage: usage.trim() || undefined,
        remark: remark.trim() || undefined,
        enabled: 1,
        herbs: rows.map((r) => ({ herb: (r.herb || '').trim(), weight: r.weight, special: r.special || undefined })),
      });
      message.success(`已存入我的方剂库「${name}」，可在「套用模板」带出`);
      setTplModal(false);
      setTplName('');
    } finally {
      setSavingTpl(false);
    }
  };

  const submit = async () => {
    if (!customer?.id) {
      message.warning('请先按手机号定位顾客');
      return;
    }
    const rows = herbs.filter((r) => (r.herb || '').trim() !== '' || r.weight != null);
    if (rows.length === 0) {
      message.warning('处方至少要有 1 味药');
      return;
    }
    for (let i = 0; i < rows.length; i++) {
      if (!(rows[i].herb || '').trim()) {
        message.warning(`第 ${i + 1} 味药名不能为空`);
        return;
      }
      if (!rows[i].weight || rows[i].weight! <= 0) {
        message.warning(`药材「${rows[i].herb!.trim()}」剂量必须大于 0`);
        return;
      }
    }
    setBusy(true);
    try {
      const created = await createPrescription({
        customerId: customer.id,
        treatId,
        staffId,
        reviewStaffId,
        checkStaffId,
        doses,
        prescriptionType: ptype || undefined,
        craft: ptype === 1 && craft ? craft : undefined,
        decoction: ptype === 0 && decoction ? true : undefined,
        deliveryType: ptype === 0 && decoction ? delivery : undefined,
        usage: usage.trim() || undefined,
        takeMethod: takeMethod.trim() || undefined,
        remark: remark.trim() || undefined,
        herbs: rows.map((r) => ({
          herb: (r.herb || '').trim(),
          weight: r.weight,
          special: r.special || undefined,
          process: r.process || undefined,
        })),
      });
      if (created?.id) {
        message.success(
          `处方 Rx-${moment(created.createTime).format('YYYYMMDD')}-${String(created.id).padStart(3, '0')} 已开` +
            (ptype === 1 ? `，膏方${craft ? `（${craft}）` : ''}已转药房待制作` : decoction ? `，代煎 ${doses} 袋已转药房待煎` : ''),
        );
        history.push('/prescription/query');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <PageContainer
      title="中药处方"
      content="按手机号定位顾客 → 写药味与剂数；定位后自动带出最近接诊单的辨证与治法（可切换，随处方落 treatId）。配伍审方（十八反/十九畏）与实时计价随写随查。药味可从字典目录检索添加（支持拼音首字母，如 zz / fs / gcao），比价按字典同名精确匹配，未收录药名不计费；字典维护见「处方 → 药材字典」。"
    >
      <div className="pr-create">
        <div className="pr-customer">
          <h3>1 · 顾客</h3>
          <div className="find">
            <input
              className="phone"
              placeholder="顾客手机号"
              value={phone}
              maxLength={11}
              onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
              onKeyDown={(e) => e.key === 'Enter' && findCustomer()}
            />
            <Button type="primary" loading={finding} onClick={findCustomer}>
              查找
            </Button>
          </div>
          {customer ? (
            <div className="found">
              <span className="nm">{customer.name}</span>
              <span className="tier">{LEVEL_TEXT[customer.level ?? 0] ?? '普通会员'}</span>
              <span className="tel">{customer.phone}</span>
            </div>
          ) : (
            <div className="none">未定位顾客，先查找再写方</div>
          )}
        </div>

        <div className="pr-sheet">
          <div className={`rx-head${customer ? '' : ' none'}`}>
            <div className="rx-title">处方笺</div>
            {customer ? (
              <div className="rx-meta">
                顾客 <b>{customer.name}</b>
                {genderText ? ` · ${genderText}` : ''}
                {customer.age ? ` · ${customer.age} 岁` : ''} · {maskPhone(customer.phone)}
                <br />
                医师 <b>{staffName || '未选'}</b> · {moment().format('YYYY-MM-DD')} · 诊号{' '}
                <b>{treatId ?? '—'}</b>
              </div>
            ) : (
              <div className="rx-meta">定位顾客后带出性别 / 年龄 / 手机与医师、诊号</div>
            )}
            <div className="rx-no">
              处方编号<b>{rxNo}</b>
            </div>
          </div>
          <h3>
            2 · 处方笺
            <Select
              className="tpl-apply"
              showSearch
              optionFilterProp="label"
              allowClear
              placeholder="套用模板（按病症带出药味）"
              style={{ minWidth: 260, marginLeft: 12 }}
              options={templates.map((t) => ({ label: `${t.name}（${t.doses ?? 7} 付）`, value: t.id! }))}
              value={templateId}
              onDropdownVisibleChange={loadTemplates}
              onFocus={loadTemplates}
              loading={applying}
              onChange={(v) => (v == null ? setTemplateId(undefined) : applyTemplate(v))}
            />
            <Select
              className="fml-apply"
              showSearch
              optionFilterProp="label"
              allowClear
              placeholder="方剂库（按方名/拼音带出全方）"
              style={{ minWidth: 260, marginLeft: 12 }}
              options={formulas.map((f) => ({ label: `${f.name} ${f.pinyin} · ${f.herbs?.length ?? 0} 味`, value: f.id! }))}
              value={formulaId}
              onDropdownVisibleChange={loadFormulas}
              onFocus={loadFormulas}
              onChange={(v) => (v == null ? setFormulaId(undefined) : applyFormula(v))}
            />
          </h3>
          <div className={`dx-strip${treats.length === 0 ? ' none' : ''}`}>
            <span className="lab">辨证摘要</span>
            {treats.length === 0 ? (
              <span className="empty">该顾客暂无接诊单（诊疗记录），可先在「诊疗 → 开单」建档，或直接开方</span>
            ) : (
              <>
                <Select
                  className="src"
                  size="small"
                  value={treatId}
                  onChange={setTreatId}
                  options={treats.map((t) => ({
                    label: `#${t.id} · ${t.createTime ? moment(t.createTime).format('YYYY-MM-DD') : ''}${
                      t.desc ? ` · ${t.desc.length > 12 ? `${t.desc.slice(0, 12)}…` : t.desc}` : ''
                    }`,
                    value: t.id!,
                  }))}
                />
                <span className="cell">
                  辨证
                  <b>{treat?.diagnose || '接诊单未填'}</b>
                </span>
                <span className="cell">
                  治法/方案
                  <b>{treat?.plan || '接诊单未填'}</b>
                </span>
                {treat?.acuCourse ? <span className="cell course">疗程：{treat.acuCourse}</span> : null}
              </>
            )}
          </div>
          <div className="rows">
            {herbs.map((row, i) => (
              <div className="row" key={i}>
                <span className="idx">{i + 1}</span>
                <Input
                  className="herb"
                  placeholder="药名，如 柴胡"
                  value={row.herb}
                  onChange={(e) => setHerb(i, { herb: e.target.value })}
                />
                <InputNumber
                  className="wt"
                  placeholder="克"
                  min={0.1}
                  value={row.weight}
                  onChange={(v) => setHerb(i, { weight: v ?? undefined })}
                />
                <Input
                  className="special"
                  placeholder="炮制，如 麸炒（选填）"
                  value={row.process}
                  onChange={(e) => setHerb(i, { process: e.target.value })}
                />
                <Input
                  className="special"
                  placeholder="煎法，如 先煎 30 分钟（选填）"
                  value={row.special}
                  onChange={(e) => setHerb(i, { special: e.target.value })}
                />
                <button
                  type="button"
                  className="del"
                  title="去掉这味"
                  disabled={herbs.length <= 1}
                  onClick={() => removeHerb(i)}
                >
                  <MinusCircleOutlined />
                </button>
              </div>
            ))}
          </div>
          <Button type="dashed" className="add" icon={<PlusOutlined />} onClick={addHerb}>
            添加一味
          </Button>
          {/* 从饮片目录添加（原型 admin-herbprescription 口径）：中文名或拼音首字母检索，选中即入行 */}
          <Select
            className="dir"
            showSearch
            value={undefined}
            filterOption={filterHerbDir}
            placeholder="从饮片目录添加（支持拼音首字母检索，如 zz / fs / gcao）"
            style={{ width: '100%', marginTop: 10 }}
            loading={dirLoading}
            onDropdownVisibleChange={loadHerbDir}
            onFocus={loadHerbDir}
            notFoundContent={dirLoading ? '目录加载中…' : '字典暂无药材，先到「处方 → 药材字典」维护'}
            options={herbDir.map((h) => ({
              label: (
                <span className="dir-opt">
                  {h.name}
                  <i>{fenToYuan(h.price)}/g</i>
                </span>
              ),
              value: h.name!,
            }))}
            onChange={(v) => v != null && pickHerb(String(v))}
          />

          <div className={`compat ${compat && (compat.findings?.length ?? 0) > 0 ? 'bad' : 'ok'}`}>
            <h3>3 · 配伍审方</h3>
            {namedHerbs.length < 2 ? (
              <div className="tip">写够两味药后自动比对十八反 / 十九畏</div>
            ) : checking ? (
              <div className="tip">审方中…</div>
            ) : compat && (compat.findings?.length ?? 0) > 0 ? (
              <div className="hits">
                <div className="head">
                  <WarningFilled /> 发现 {compat!.findings!.length} 组配伍提示
                </div>
                {compat!.findings!.map((f, i) => (
                  <div className="hit" key={i}>
                    <span className={`lv ${f.level === '禁忌' ? 'fan' : 'wei'}`}>{f.level}</span>
                    <span className="pair">
                      {f.a} × {f.b}
                    </span>
                    <span className="rule">
                      {f.rule} · {f.note}
                    </span>
                  </div>
                ))}
                <div className="note">提示不拦截，是否照用由医师判断</div>
              </div>
            ) : (
              <div className="pass">
                <CheckCircleFilled /> 未发现配伍禁忌（十八反 / 十九畏）
              </div>
            )}
          </div>

          {/* 特殊煎法提醒（原型审方四查之一）：扫描药味煎法备注，命中关键词即提示；剂量上限需药典区间数据，暂缓 */}
          <div className={`fx-check${specialFxCount > 0 ? ' warn' : ''}`}>
            {specialFxCount > 0 ? (
              <>
                <WarningFilled />
                <span>
                  <b>特殊煎法</b>：{specialFxCount} 味需标注（先煎 / 后下等已录入，随笺打印）
                </span>
              </>
            ) : (
              <>
                <CheckCircleFilled />
                <span>
                  <b>特殊煎法</b>：无需标注（先煎 / 后下 / 包煎 / 烊化 / 冲服 / 另煎）
                </span>
              </>
            )}
          </div>

          {allergy && (allergy.findings?.length ?? 0) > 0 ? (
            <div className="allergy">
              <div className="ahead">
                <WarningFilled /> 过敏提示 · 顾客过敏史提到 {allergy!.findings!.length} 味当前药方
              </div>
              {allergy!.findings!.map((f, i) => (
                <div className="hit" key={i}>
                  <span className="pair">{f.herb}</span>
                  <span className="rule">{f.content}</span>
                </div>
              ))}
              <div className="note">按顾客过敏史记录原文匹配（提示不拦截，是否照用由医师判断）</div>
            </div>
          ) : customer ? (
            // ✓ 常显态：定位顾客且未命中即显示（原型 admin-herbprescription 审方卡口径）
            <div className="allergy ok">
              <div className="ahead">
                <CheckCircleFilled /> 过敏史 ·{' '}
                {allergyRecordCount > 0
                  ? `档案 ${allergyRecordCount} 条过敏记录，未命中当前药方`
                  : '档案无药物过敏记录'}
              </div>
            </div>
          ) : null}

          {dose && (dose.findings?.length ?? 0) > 0 ? (
            <div className="dose">
              <div className="ahead">
                <WarningFilled /> 剂量提示 · {dose!.findings!.length} 味超单剂上限
              </div>
              {dose!.findings!.map((f, i) => (
                <div className="hit" key={i}>
                  <span className="pair">{f.herb}</span>
                  <span className="rule">
                    {f.weight} 克 ＞ 上限 {f.limit} 克
                  </span>
                </div>
              ))}
              <div className="note">按药材字典单剂上限比对（提示不拦截，是否照用由医师判断）</div>
            </div>
          ) : dose && (dose.checked ?? 0) > 0 ? (
            <div className="dose ok">
              <div className="ahead">
                <CheckCircleFilled /> 单剂剂量 ·{' '}
                {(dose.limited ?? 0) > 0
                  ? `${dose!.checked} 味中 ${dose!.limited} 味有单剂上限参照，均未超线`
                  : '药材字典未设单剂上限，本次无比对'}
              </div>
              {dose.unmatched && dose.unmatched.length > 0 ? (
                <div className="note">
                  未收录 {dose.unmatched.length} 味：{dose.unmatched.join('、')}（无剂量参照）
                </div>
              ) : null}
            </div>
          ) : null}

          <div className="pricing">
            <h3>4 · 计价</h3>
            {!pricing || (pricing.herbCount ?? 0) === 0 ? (
              <div className="tip">写药名后按药材字典自动试算</div>
            ) : (
              <div className="sum">
                <span className="total">
                  <em>应收</em>
                  {fenToYuan(recvFen)}
                </span>
                <span className="detail">
                  单剂 {fenToYuan(pricing.perDoseFen)} × {pricing.doses} 付
                  {pricing.pricedHerbCount === pricing.herbCount
                    ? ''
                    : ` · 已比价 ${pricing.pricedHerbCount}/${pricing.herbCount} 味`}
                </span>
                {/* 三行账（原型 admin-herbprescription .fee 口径）：饮片行 + 代煎服务行，合计=两者之和 */}
                <div className="lines">
                  <span>
                    饮片 {pricing.herbCount} 味 × {pricing.doses} 付
                  </span>
                  <span>{fenToYuan(pricing.totalFen)}</span>
                </div>
                {ptype === 0 && decoction ? (
                  <div className="lines">
                    <span>代煎服务（{doses} 袋）</span>
                    <span>{fenToYuan(decoctFeeFen)}</span>
                  </div>
                ) : null}
                {(pricing.unknownHerbs?.length ?? 0) > 0 ? (
                  <div className="unknown">未收录（不计费）：{pricing.unknownHerbs!.join('、')}</div>
                ) : null}
                <div className="note">按药材字典实时试算、无快照；应收=饮片+代煎，实际收费仍以卡项订单结算为准</div>
              </div>
            )}
          </div>

          <div className="meta">
            <label className="ttype">
              类型
              <Radio.Group
                value={ptype}
                onChange={(e) => {
                  const t = e.target.value as number;
                  setPtype(t);
                  if (t === 1) {
                    setDecoction(false);
                  } else {
                    setCraft(undefined);
                  }
                }}
              >
                <Radio value={0}>汤剂</Radio>
                <Radio value={1}>膏方</Radio>
              </Radio.Group>
              {ptype === 1 ? (
                <Select
                  allowClear
                  placeholder="收膏方式"
                  style={{ minWidth: 120 }}
                  value={craft}
                  options={[
                    { label: '炼蜜', value: '炼蜜' },
                    { label: '清膏', value: '清膏' },
                    { label: '糖膏', value: '糖膏' },
                    { label: '阿胶收膏', value: '阿胶收膏' },
                  ]}
                  onChange={(v) => setCraft(v)}
                />
              ) : null}
            </label>
            {ptype === 0 ? (
              <label>
                <Checkbox checked={decoction} onChange={(e) => setDecoction(e.target.checked)}>
                  代煎 {doses} 袋
                </Checkbox>
                <i className="dfee">
                  服务费 {fenToYuan(decoctFeeFen)}（¥3/袋）计入计价应收；实际收费仍以卡项订单结算为准
                  {decoction ? '；今日 17:00 前可取，加急 2 小时' : ''}
                </i>
              </label>
            ) : (
              <label>
                <i className="dfee">膏方按料计，开方后转药房待制作；领取流转见处方查询页</i>
              </label>
            )}
            {ptype === 0 && decoction ? (
              <label>
                领取方式
                <Radio.Group
                  value={delivery}
                  onChange={(e) => setDelivery(e.target.value as number)}
                  options={[
                    { label: '自取', value: 0 },
                    { label: '同城配送', value: 1 },
                  ]}
                  optionType="button"
                />
                <i className="dfee">{delivery === 1 ? '运费 ¥6.00 仅提示' : '前台领取'}</i>
              </label>
            ) : null}
            <label>
              医师
              <Select
                allowClear
                placeholder="选填"
                style={{ minWidth: 160 }}
                options={staffOptions}
                value={staffId}
                onDropdownVisibleChange={loadStaff}
                onChange={(v) => setStaffId(v)}
              />
            </label>
            <label>
              药师审核
              <Select
                allowClear
                placeholder="选填"
                style={{ minWidth: 140 }}
                options={staffOptions}
                value={reviewStaffId}
                onDropdownVisibleChange={loadStaff}
                onChange={(v) => setReviewStaffId(v)}
              />
            </label>
            <label>
              调配 / 复核
              <Select
                allowClear
                placeholder="选填"
                style={{ minWidth: 140 }}
                options={staffOptions}
                value={checkStaffId}
                onDropdownVisibleChange={loadStaff}
                onChange={(v) => setCheckStaffId(v)}
              />
            </label>
          </div>
          {/* 用法三格（原型 .usage：剂数/煎法/服法） */}
          <div className="usage-row">
            <div className="u">
              <div className="k">剂数</div>
              <div className="v">
                <InputNumber min={1} precision={0} value={doses} onChange={(v) => setDoses(v ?? 7)} /> 付
              </div>
            </div>
            <div className="u">
              <div className="k">煎法</div>
              <div className="v">
                <Input
                  placeholder="如 水煎服，日一剂"
                  value={usage}
                  onChange={(e) => setUsage(e.target.value)}
                />
              </div>
            </div>
            <div className="u">
              <div className="k">服法</div>
              <div className="v">
                <Input
                  placeholder="如 分早晚温服"
                  value={takeMethod}
                  onChange={(e) => setTakeMethod(e.target.value)}
                />
              </div>
            </div>
          </div>
          <Input
            className="remark"
            placeholder="备注（选填）：复诊提醒、禁忌等"
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
          />
          {/* 签名行（原型 sign-row）：随方存档，打印处方笺时随笺输出 */}
          <div className="sign-row">
            <span>
              医师签名 <b>{staffName || '—'}</b>
            </span>
            <span>
              药师审核 <b>{staffOptions.find((s) => s.value === reviewStaffId)?.label || '—'}</b>
            </span>
            <span>
              调配 / 复核 <b>{staffOptions.find((s) => s.value === checkStaffId)?.label || '—'}</b>
            </span>
          </div>
          <div className="foot">
            <span className="hint">演示口径：计价按药材字典实时试算（无快照），库存为规划功能；代煎费计入应收合计、领取流转见处方查询；审方提示不拦截</span>
            <div className="btns">
              <Button size="large" onClick={() => setTplModal(true)}>
                存入我的方剂库
              </Button>
              <Button size="large" onClick={() => window.print()}>
                打印处方笺
              </Button>
              <Button type="primary" size="large" loading={busy} onClick={submit}>
                存方并收费
              </Button>
            </div>
          </div>
          <Modal
            title="存入我的方剂库"
            open={tplModal}
            confirmLoading={savingTpl}
            okText="存入"
            cancelText="取消"
            onOk={saveAsTemplate}
            onCancel={() => setTplModal(false)}
          >
            <div className="tpl-tip">
              以病症名存为启用模板（1–20 字，唯一），含当前药味、剂数{decoction ? '与代煎建议' : ''}；可在开方页「套用模板」带出。
            </div>
            <Input
              placeholder="病症名，如 脾胃虚寒"
              maxLength={20}
              value={tplName}
              onChange={(e) => setTplName(e.target.value)}
            />
          </Modal>
        </div>
      </div>
    </PageContainer>
  );
};

export default Create;
