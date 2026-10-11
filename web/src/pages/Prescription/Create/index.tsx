import React, { useEffect, useRef, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, Checkbox, Input, InputNumber, Radio, Select, message } from 'antd';
import { CheckCircleFilled, MinusCircleOutlined, PlusOutlined, WarningFilled } from '@ant-design/icons';
import moment from 'moment';
import {
  checkAllergy,
  checkCompatibility,
  createPrescription,
  pricePrescription,
  queryEnabledTemplates,
  type AllergyResult,
  type CompatibilityResult,
  type PrescriptionHerb,
  type PrescriptionTemplate,
  type Pricing,
} from '@/services/ant-design-pro/prescription';
import { fetchCustomerByPhone } from '@/services/ant-design-pro/customer';
import { queryFormulaPage, type Formula } from '@/services/ant-design-pro/formula';
import { queryStaffByPage } from '@/services/ant-design-pro/staff';
import { queryTreatByPage } from '@/services/ant-design-pro/treat';
import './index.less';

/** 会员等级展示口径（同顾客档案）：1 普通 / 2 银卡 / 3 金卡 */
const LEVEL_TEXT: Record<number, string> = { 1: '普通会员', 2: '银卡会员', 3: '金卡会员' };

/** 分 → 元（计价展示） */
const fenToYuan = (fen?: number) => `¥${((fen ?? 0) / 100).toFixed(2)}`;

type CustomerBrief = { id?: number; name?: string; phone?: string; level?: number };

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
  const [usage, setUsage] = useState('');
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  const [compat, setCompat] = useState<CompatibilityResult | undefined>();
  const [checking, setChecking] = useState(false);
  const compatSeq = useRef(0);
  /** 过敏审方：按顾客过敏史比对药味（提示不拦截），定位顾客后随写随查 */
  const [allergy, setAllergy] = useState<AllergyResult | undefined>();
  const allergySeq = useRef(0);
  const [pricing, setPricing] = useState<Pricing | undefined>();
  const priceSeq = useRef(0);
  /** 病症处方模板：套用后只填当前开方单，不写回模板 */
  const [templates, setTemplates] = useState<PrescriptionTemplate[]>([]);
  const [templateId, setTemplateId] = useState<number | undefined>();
  const [applying, setApplying] = useState(false);
  /** 方剂库：按方名/拼音检索带出全方，不写回方剂库 */
  const [formulas, setFormulas] = useState<Formula[]>([]);
  const [formulaId, setFormulaId] = useState<number | undefined>();

  const loadTemplates = async () => {
    if (templates.length > 0) return;
    const list = await queryEnabledTemplates();
    setTemplates(list || []);
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
        doses,
        prescriptionType: ptype || undefined,
        craft: ptype === 1 && craft ? craft : undefined,
        decoction: ptype === 0 && decoction ? true : undefined,
        usage: usage.trim() || undefined,
        remark: remark.trim() || undefined,
        herbs: rows.map((r) => ({ herb: (r.herb || '').trim(), weight: r.weight, special: r.special || undefined })),
      });
      if (created?.id) {
        message.success(
          `处方 #${created.id} 已开` +
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
      content="按手机号定位顾客 → 写药味与剂数；定位后自动带出最近接诊单的辨证与治法（可切换，随处方落 treatId）。配伍审方（十八反/十九畏）与实时计价随写随查。药材名按字典精确同名比价，未收录药名不计费；字典维护见「处方 → 药材字典」。"
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
                  placeholder="特殊煎法，如 先煎/后下（选填）"
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
          ) : null}

          <div className="pricing">
            <h3>4 · 计价</h3>
            {!pricing || (pricing.herbCount ?? 0) === 0 ? (
              <div className="tip">写药名后按药材字典自动试算</div>
            ) : (
              <div className="sum">
                <span className="total">{fenToYuan(pricing.totalFen)}</span>
                <span className="detail">
                  单剂 {fenToYuan(pricing.perDoseFen)} × {pricing.doses} 付
                  {pricing.pricedHerbCount === pricing.herbCount
                    ? ''
                    : ` · 已比价 ${pricing.pricedHerbCount}/${pricing.herbCount} 味`}
                </span>
                {(pricing.unknownHerbs?.length ?? 0) > 0 ? (
                  <div className="unknown">未收录（不计费）：{pricing.unknownHerbs!.join('、')}</div>
                ) : null}
                <div className="note">按药材字典实时试算、无快照；收费仍以卡项订单结算为准</div>
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
            <label>
              剂数
              <InputNumber min={1} precision={0} value={doses} onChange={(v) => setDoses(v ?? 7)} /> 付
            </label>
            {ptype === 0 ? (
              <label>
                <Checkbox checked={decoction} onChange={(e) => setDecoction(e.target.checked)}>
                  代煎 {doses} 袋
                </Checkbox>
                <i className="dfee">服务费 {fenToYuan(doses * 300)}（¥3/袋）仅提示，收费以卡项订单结算为准</i>
              </label>
            ) : (
              <label>
                <i className="dfee">膏方按料计，开方后转药房待制作；领取流转见处方查询页</i>
              </label>
            )}
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
          </div>
          <Input.TextArea
            className="usage"
            placeholder="用法：煎服法/频次/代煎说明，如「水煎服，日一剂，早晚温服；代煎 7 袋」"
            rows={2}
            value={usage}
            onChange={(e) => setUsage(e.target.value)}
          />
          <Input
            className="remark"
            placeholder="备注（选填）：复诊提醒、禁忌等"
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
          />
          <div className="foot">
            <span className="hint">演示口径：计价按药材字典实时试算（无快照），库存为规划功能；代煎费仅提示、领取流转见处方查询；审方提示不拦截</span>
            <Button type="primary" size="large" loading={busy} onClick={submit}>
              开方
            </Button>
          </div>
        </div>
      </div>
    </PageContainer>
  );
};

export default Create;
