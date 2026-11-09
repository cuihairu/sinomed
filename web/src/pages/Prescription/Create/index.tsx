import React, { useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, Select, message } from 'antd';
import moment from 'moment';
import {
  createPrescription,
  createTemplate,
  type PrescriptionHerb,
} from '@/services/ant-design-pro/prescription';
import { queryStaffByPage } from '@/services/ant-design-pro/staff';
import useCustomerLookup from './useCustomerLookup';
import usePrescriptionChecks from './usePrescriptionChecks';
import usePrescriptionImports from './usePrescriptionImports';
import CustomerPanel from './CustomerPanel';
import HerbRows from './HerbRows';
import CheckPanels from './CheckPanels';
import PricingPanel from './PricingPanel';
import RxMetaForm from './RxMetaForm';
import SaveTemplateModal from './SaveTemplateModal';
import { maskPhone, SPECIAL_FX } from './helpers';
import './index.less';

const Create: React.FC = () => {
  const { customer, phone, setPhone, finding, findCustomer, treats, treatId, setTreatId } =
    useCustomerLookup();
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

  const {
    templates,
    templateId,
    setTemplateId,
    applying,
    loadTemplates,
    applyTemplate,
    formulas,
    formulaId,
    setFormulaId,
    loadFormulas,
    applyFormula,
  } = usePrescriptionImports(setHerbs, setDoses, setDecoction, setUsage, setRemark);

  /** 从目录选中：填入第一个空药味行，无空行则追加 */
  const pickHerb = (name: string) => {
    setHerbs((rows) => {
      const empty = rows.findIndex((r) => !(r.herb || '').trim());
      if (empty >= 0) return rows.map((r, k) => (k === empty ? { ...r, herb: name } : r));
      return [...rows, { herb: name, weight: undefined }];
    });
  };

  const setHerb = (i: number, patch: Partial<PrescriptionHerb>) => {
    setHerbs((rows) => rows.map((r, k) => (k === i ? { ...r, ...patch } : r)));
  };
  const addHerb = () => setHerbs((rows) => [...rows, { herb: '', weight: undefined }]);
  const removeHerb = (i: number) => setHerbs((rows) => rows.filter((_, k) => k !== i));

  // 有效药名列表：审方四查与计价的比对输入（去空白行）
  const namedHerbs = herbs.map((r) => (r.herb || '').trim()).filter(Boolean);
  const { compat, checking, allergy, allergyRecordCount, dose, pricing } = usePrescriptionChecks(
    namedHerbs,
    herbs,
    doses,
    customer,
  );

  const loadStaff = async () => {
    if (staffOptions.length > 0) return;
    const staffs = await queryStaffByPage({ current: 1, pageSize: 100 });
    setStaffOptions(
      (staffs?.data || []).map((s) => ({ label: s.name || `员工${s.id}`, value: s.id! })),
    );
  };

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
        herbs: rows.map((r) => ({
          herb: (r.herb || '').trim(),
          weight: r.weight,
          special: r.special || undefined,
        })),
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
          `处方 Rx-${moment(created.createTime).format('YYYYMMDD')}-${String(created.id).padStart(
            3,
            '0',
          )} 已开` +
            (ptype === 1
              ? `，膏方${craft ? `（${craft}）` : ''}已转药房待制作`
              : decoction
              ? `，代煎 ${doses} 袋已转药房待煎`
              : ''),
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
        <CustomerPanel
          phone={phone}
          setPhone={setPhone}
          finding={finding}
          findCustomer={findCustomer}
          customer={customer}
        />

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
              options={templates.map((t) => ({
                label: `${t.name}（${t.doses ?? 7} 付）`,
                value: t.id!,
              }))}
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
              options={formulas.map((f) => ({
                label: `${f.name} ${f.pinyin} · ${f.herbs?.length ?? 0} 味`,
                value: f.id!,
              }))}
              value={formulaId}
              onDropdownVisibleChange={loadFormulas}
              onFocus={loadFormulas}
              onChange={(v) => (v == null ? setFormulaId(undefined) : applyFormula(v))}
            />
          </h3>
          <div className={`dx-strip${treats.length === 0 ? ' none' : ''}`}>
            <span className="lab">辨证摘要</span>
            {treats.length === 0 ? (
              <span className="empty">
                该顾客暂无接诊单（诊疗记录），可先在「诊疗 → 开单」建档，或直接开方
              </span>
            ) : (
              <>
                <Select
                  className="src"
                  size="small"
                  value={treatId}
                  onChange={setTreatId}
                  options={treats.map((t) => ({
                    label: `#${t.id} · ${
                      t.createTime ? moment(t.createTime).format('YYYY-MM-DD') : ''
                    }${
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
                {treat?.acuCourse ? (
                  <span className="cell course">疗程：{treat.acuCourse}</span>
                ) : null}
              </>
            )}
          </div>
          <HerbRows
            herbs={herbs}
            setHerb={setHerb}
            addHerb={addHerb}
            removeHerb={removeHerb}
            pickHerb={pickHerb}
          />

          <CheckPanels
            namedCount={namedHerbs.length}
            checking={checking}
            compat={compat}
            specialFxCount={specialFxCount}
            allergy={allergy}
            allergyRecordCount={allergyRecordCount}
            hasCustomer={!!customer}
            dose={dose}
          />

          <PricingPanel
            pricing={pricing}
            recvFen={recvFen}
            decoctFeeFen={decoctFeeFen}
            doses={doses}
            showDecoctionLine={ptype === 0 && decoction}
          />

          <RxMetaForm
            ptype={ptype}
            setPtype={setPtype}
            craft={craft}
            setCraft={setCraft}
            decoction={decoction}
            setDecoction={setDecoction}
            delivery={delivery}
            setDelivery={setDelivery}
            staffOptions={staffOptions}
            loadStaff={loadStaff}
            staffId={staffId}
            setStaffId={setStaffId}
            reviewStaffId={reviewStaffId}
            setReviewStaffId={setReviewStaffId}
            checkStaffId={checkStaffId}
            setCheckStaffId={setCheckStaffId}
            doses={doses}
            setDoses={setDoses}
            usage={usage}
            setUsage={setUsage}
            takeMethod={takeMethod}
            setTakeMethod={setTakeMethod}
            remark={remark}
            setRemark={setRemark}
            decoctFeeFen={decoctFeeFen}
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
            <span className="hint">
              演示口径：计价按药材字典实时试算（无快照），库存为规划功能；代煎费计入应收合计、领取流转见处方查询；审方提示不拦截
            </span>
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
          <SaveTemplateModal
            open={tplModal}
            saving={savingTpl}
            decoction={decoction}
            tplName={tplName}
            setTplName={setTplName}
            onOk={saveAsTemplate}
            onCancel={() => setTplModal(false)}
          />
        </div>
      </div>
    </PageContainer>
  );
};

export default Create;
