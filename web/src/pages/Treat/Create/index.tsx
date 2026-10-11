import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  createTreat,
  fetchTreatById,
  updateTreat,
  queryTreatByPage,
  fuzzyQueryTreatByPage,
} from '@/services/ant-design-pro/treat';
import { fetchCustomerById } from '@/services/ant-design-pro/customer';
import {
  PageContainer,
  ProForm,
  ProFormSelect,
  ProFormText,
  ProFormDigit,
  ProFormDatePicker,
  ProFormTextArea,
  ProCard,
} from '@ant-design/pro-components';
import type { ProFormInstance } from '@ant-design/pro-components';
import { Button, Select, message, Form } from 'antd';
import { useSearchParams, history } from '@umijs/max';
import moment from 'moment';
import ScannerInput from '@/components/ScannerInput';
import { queryAcupointPage, type Acupoint } from '@/services/ant-design-pro/acupoint';
import { queryEnabledTherapyTemplates, type TherapyTemplate } from '@/services/ant-design-pro/therapy';
import './index.less';

type Treat = {
  id?: number;
  customerId?: number;
  // line 1
  name?: string;
  gender?: number;
  address?: string;
  birthday?: string;
  age?: number;
  firstTime?: string;
  desc?: string;
  inquiry?: string;
  observation?: string;
  palpation?: string;
  pulse?: string;
  pulseLeft?: string;
  pulseRight?: string;
  pulseInfoLeft?: string;
  pulseInfoRight?: string;
  pulseCunLeft?: string;
  pulseGuanLeft?: string;
  pulseChiLeft?: string;
  pulseCunRight?: string;
  pulseGuanRight?: string;
  pulseChiRight?: string;
  fiveBenZiLeft?: string;
  fiveZiBenLeft?: string;
  fiveBenZiRight?: string;
  fiveZiBenRight?: string;
  fiveBenKeLeft?: string;
  fiveKeBenLeft?: string;
  fiveBenKeRight?: string;
  fiveKeBenRight?: string;
  fiveNanJinLeft?: string;
  fiveNanJinBigLeft?: string;
  fiveNanJinSmallLeft?: string;
  fiveNanJinRight?: string;
  fiveNanJinBigRight?: string;
  fiveNanJinSmallRight?: string;
  fiveRateLeft?: string;
  fiveRateRight?: string;
  fiveAuxLeft?: string;
  fiveAuxRight?: string;
  acupointLeft?: string;
  acupointRight?: string;
  diagnose?: string;
  plan?: string;
  diet?: string;
  conditioning?: string;
  review?: string;
};

type TreatRow = { id?: number; createTime?: string; diagnose?: string; desc?: string; plan?: string };

/** 会员等级展示口径：1 普通 / 2 银卡 / 3 金卡（与顾客档案页一致） */
const LEVEL_TEXT: Record<number, string> = { 1: '普通会员', 2: '银卡会员', 3: '金卡会员' };
const levelText = (level?: number) => LEVEL_TEXT[level ?? 0] ?? (level ? `会员 L${level}` : '普通会员');
const maskPhone = (p?: string) => (p && p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p || '—');

const Create: React.FC = () => {
  const [searchParams] = useSearchParams();
  const formRef = useRef<ProFormInstance>();
  const customerId = searchParams.get('customerId');
  const treatId = searchParams.get('treatId');
  const [customer, setCustomer] = useState<API.Customer>();
  const [treats, setTreats] = useState<TreatRow[]>([]);
  const [treatTotal, setTreatTotal] = useState(0);
  const [todayTotal, setTodayTotal] = useState<number>();
  /** 穴位字典：接诊取穴选穴辅助（按穴名/拼音检索，选中即追加进取穴文本，仍可自由写） */
  const [acupoints, setAcupoints] = useState<Acupoint[]>([]);
  /** 外治模板：上架模板即点即套（方法/疗程回填针灸处方区，取穴追加进取穴文本，仍可随时改） */
  const [therapyTemplates, setTherapyTemplates] = useState<TherapyTemplate[]>([]);

  const loadAcupoints = async () => {
    if (acupoints.length > 0) return;
    const page = await queryAcupointPage({ current: 1, pageSize: 200 });
    setAcupoints(page?.data || []);
  };

  const appendAcupoint = (field: 'acupointLeft' | 'acupointRight', name: string) => {
    const cur = ((formRef.current?.getFieldValue(field) as string) || '').trim();
    const next = cur ? `${cur}、${name}` : name;
    formRef.current?.setFieldsValue({ [field]: next });
  };

  const loadTherapyTemplates = async () => {
    if (therapyTemplates.length > 0) return;
    const list = await queryEnabledTherapyTemplates();
    setTherapyTemplates(list || []);
  };

  /** 套用外治模板：方法/疗程直接回填，取穴建议追加进取穴-左；只回填一次建议值，不写回模板 */
  const applyTherapyTemplate = (id?: number) => {
    const tpl = therapyTemplates.find((t) => t.id === id);
    if (!tpl) return;
    const values: Record<string, string> = {};
    if (tpl.method) values.acuMethod = tpl.method;
    if (tpl.course) values.acuCourse = tpl.course;
    const target = (tpl.target || '').trim();
    if (target) {
      const cur = ((formRef.current?.getFieldValue('acupointLeft') as string) || '').trim();
      values.acupointLeft = cur ? `${cur}、${target}` : target;
    }
    formRef.current?.setFieldsValue(values);
    message.success(`已套用「${tpl.name}」：方法/疗程/取穴按建议回填，可继续自由修改`);
  };

  // 顾客头卡数据：customerId 直取；编辑态（treatId）从单据反查；定位扫码经 onLocated 改写 customerId 后重跑
  useEffect(() => {
    let cancelled = false;
    (async () => {
      let cid = customerId;
      if (!cid && treatId) {
        const t = await fetchTreatById(treatId);
        cid = t?.customerId != null ? String(t.customerId) : '';
      }
      if (!cid || cancelled) return;
      const v = await fetchCustomerById(cid);
      if (cancelled) return;
      setCustomer(v);
      const res = await queryTreatByPage({ customerId: Number(cid), pageSize: 3, current: 1 });
      if (cancelled) return;
      setTreats(res?.data || []);
      setTreatTotal(res?.total || 0);
      // 新建态才回填患者行；编辑态字段以单据为准（request 分支已带出）
      if (!treatId) {
        formRef.current?.setFieldsValue({
          customerId: Number(cid),
          name: v?.name,
          gender: v?.gender,
          age: v?.age,
        });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [customerId, treatId]);

  // 今日工作站·今日接诊单数：/treat/fuzzy 不传顾客条件即全店按时间段查（接口全参数可空）
  useEffect(() => {
    let cancelled = false;
    fuzzyQueryTreatByPage({
      current: 1,
      pageSize: 1,
      startTime: moment().format('YYYY-MM-DD'),
      endTime: moment().add(1, 'day').format('YYYY-MM-DD'),
    }).then((res) => {
      if (!cancelled) setTodayTotal(res?.total ?? 0);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // 扫码定位：就地换顾客（toast 由 ScannerInput 发），改写地址栏让头卡 effect 重跑
  const onLocated = useCallback((c: API.Customer) => {
    if (!c.id) return;
    history.replace(`/treat/create?customerId=${c.id}`);
  }, []);

  const lastVisit = treats.map((t) => t.createTime).filter(Boolean).sort().pop();
  const lastVisitText = lastVisit
    ? moment().diff(moment(lastVisit), 'days') === 0
      ? '今天到店'
      : `最近到店 ${moment().diff(moment(lastVisit), 'days')} 天前`
    : '暂无到店记录';

  return (
    <PageContainer
      title={customer?.name ? `${customer.name} · 创建接诊单` : '创建接诊单'}
      className="tc-page"
    >
      <ProForm<Treat>
        formRef={formRef}
        layout={'vertical'}
        submitter={false}
        request={async () => {
          if (treatId) {
            const ret = (await fetchTreatById(treatId)) || {};
            if (ret?.customerId) {
              const v = await fetchCustomerById(ret?.customerId);
              ret.name = v?.name;
              ret.gender = v?.gender;
              ret.age = v?.age;
            }
            return Promise.resolve<Treat>(ret);
          }
          if (customerId && !isNaN(parseInt(customerId))) {
            // 患者行由头卡 effect 回填，这里只落 customerId
            return Promise.resolve<Treat>({ customerId: parseInt(customerId) });
          }
          message.error('没有发现顾客ID，可从顾客档案「发起接诊」进入或扫码定位');
          return Promise.resolve<Treat>({});
        }}
        onFinish={async (values) => {
          // 带 id 为编辑态：走 PUT 全量更新；否则新建
          const result = values.id ? await updateTreat(values) : await createTreat(values);
          if (result.id) {
            formRef.current?.setFieldsValue({ id: result.id });
            message.success(values.id ? '更新成功' : '创建成功');
          }
          return true;
        }}
      >
        <ProFormText name="id" hidden={true} />
        <ProFormText name="customerId" hidden={true} />

        <div className="tc-wrap">
          <div className="tc-main">
            <div className="tc-scan">
              <ScannerInput onLocated={onLocated} />
            </div>

            {customer ? (
              <div className="tc-strip">
                <div className="avatar">{(customer.name || '客').slice(0, 1)}</div>
                <div className="cs">
                  <div className="nm">
                    {customer.name || '—'}
                    <span className="tier">{levelText(customer.level)}</span>
                  </div>
                  <div className="mt">
                    {maskPhone(customer.phone)} · 到店 {treatTotal} 次 · {lastVisitText}
                  </div>
                </div>
                <div className="cs-bal">
                  持卡卡项余次
                  <span className="hold">规划功能 · 无卡项持有实体</span>
                </div>
              </div>
            ) : null}

            <section className="tc-panel">
              <h3>患者信息</h3>
              <div className="tc-row cols-3">
                <ProFormText name="name" label="名字" placeholder="输入名字" />
                <ProFormSelect
                  name="gender"
                  label="性别"
                  placeholder="男"
                  options={[
                    { value: 0, label: '女' },
                    { value: 1, label: '男' },
                  ]}
                />
                <ProFormText name="age" label="年龄" placeholder="20" />
              </div>
              <div className="tc-row cols-3">
                <ProFormDatePicker name="birthday" label="出生年月" placeholder="2000-01-01" />
                <ProFormDatePicker name="firstTime" label="初诊日期" placeholder="2000-01-01" />
              </div>
            </section>

            <section className="tc-panel">
              <h3>四诊</h3>
              <div className="tc-row cols-2">
                <ProFormTextArea name="desc" label="主诉" placeholder="患者自述的主要不适" />
                <ProFormTextArea name="inquiry" label="问诊" placeholder="问诊所见" />
                <ProFormTextArea name="observation" label="望诊" placeholder="望诊所见" />
                <ProFormTextArea name="palpation" label="切诊" placeholder="切诊所见" />
              </div>
            </section>

            <section className="tc-panel">
              <h3>
                脉象<span className="tc-hint">左右手寸关尺分部记录</span>
              </h3>
              <div className="tc-row cols-3">
                <ProFormText name="pulse" label="脉象" placeholder="总述" />
                <ProFormText name="pulseLeft" label="左手脉象（总）" placeholder="寸关尺概述" />
                <ProFormText name="pulseRight" label="右手脉象（总）" placeholder="寸关尺概述" />
              </div>
              <div className="pulse-hands">
                <div className="hand">
                  <div className="hn">左手</div>
                  <ProFormText name="pulseCunLeft" label="寸" placeholder="寸脉" />
                  <ProFormText name="pulseGuanLeft" label="关" placeholder="关脉" />
                  <ProFormText name="pulseChiLeft" label="尺" placeholder="尺脉" />
                </div>
                <div className="hand">
                  <div className="hn">右手</div>
                  <ProFormText name="pulseCunRight" label="寸" placeholder="寸脉" />
                  <ProFormText name="pulseGuanRight" label="关" placeholder="关脉" />
                  <ProFormText name="pulseChiRight" label="尺" placeholder="尺脉" />
                </div>
              </div>
              <ProFormTextArea name="pulseInfo" label="脉象信息" placeholder="脉象补充说明" />
            </section>

            <section className="tc-panel">
              <h3>五行脉用法</h3>
              <WuxingLive />
              <ProCard ghost headerBordered split="vertical">
                <ProCard title="左手" ghost headerBordered split="horizontal" layout="center">
                  <ProCard title="子 _生_" ghost split="vertical">
                    <ProCard ghost>
                      <ProFormText name="fiveBenZiLeft" label="本子" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveZiBenLeft" label="子本" placeholder="" />
                    </ProCard>
                  </ProCard>
                  <ProCard title="克 _克_" ghost split="vertical">
                    <ProCard ghost>
                      <ProFormText name="fiveBenKeLeft" label="本子" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveKeBenLeft" label="子本" placeholder="" />
                    </ProCard>
                  </ProCard>
                  <ProCard ghost split="vertical">
                    <ProCard ghost>
                      <ProFormText name="fiveNanJinLeft" label="难经" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveNanJinBigLeft" label="大" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveNanJinSmallLeft" label="小" placeholder="" />
                    </ProCard>
                  </ProCard>
                  <ProCard ghost>
                    <ProFormText name="fiveRateLeft" label="缓急" placeholder="" />
                  </ProCard>
                  <ProCard ghost>
                    <ProFormText name="fiveAuxLeft" label="辅助" placeholder="" />
                  </ProCard>
                </ProCard>
                <ProCard title="右手" ghost headerBordered split="horizontal" layout="center">
                  <ProCard title="子 _生_" ghost split="vertical">
                    <ProCard ghost>
                      <ProFormText name="fiveBenZiRight" label="本子" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveZiBenRight" label="子本" placeholder="" />
                    </ProCard>
                  </ProCard>
                  <ProCard title="克 _克_" ghost split="vertical">
                    <ProCard ghost>
                      <ProFormText name="fiveBenKeRight" label="本子" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveKeBenRight" label="子本" placeholder="" />
                    </ProCard>
                  </ProCard>
                  <ProCard ghost split="vertical">
                    <ProCard ghost>
                      <ProFormText name="fiveNanJinRight" label="难经" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveNanJinBigRight" label="大" placeholder="" />
                    </ProCard>
                    <ProCard ghost>
                      <ProFormText name="fiveNanJinSmallRight" label="小" placeholder="" />
                    </ProCard>
                  </ProCard>
                  <ProCard ghost>
                    <ProFormText name="fiveRateRight" label="缓急" placeholder="" />
                  </ProCard>
                  <ProCard ghost>
                    <ProFormText name="fiveAuxRight" label="辅助" placeholder="" />
                  </ProCard>
                </ProCard>
              </ProCard>
            </section>

            <section className="tc-panel">
              <h3>取穴</h3>
              <div className="tc-row cols-2">
                <div className="tc-ap">
                  <ProFormText name="acupointLeft" label="取穴-反应点 左" placeholder="穴位名，如 足三里、三阴交" />
                  <Select
                    className="tc-ap-pick"
                    showSearch
                    optionFilterProp="label"
                    allowClear
                    value={null}
                    placeholder="字典选穴"
                    options={acupoints.map((a) => ({ label: `${a.name} ${a.pinyin}`, value: a.name! }))}
                    onDropdownVisibleChange={loadAcupoints}
                    onFocus={loadAcupoints}
                    onChange={(v) => v && appendAcupoint('acupointLeft', v as string)}
                  />
                </div>
                <div className="tc-ap">
                  <ProFormText name="acupointRight" label="取穴-反应点 右" placeholder="穴位名，如 内关、太冲" />
                  <Select
                    className="tc-ap-pick"
                    showSearch
                    optionFilterProp="label"
                    allowClear
                    value={null}
                    placeholder="字典选穴"
                    options={acupoints.map((a) => ({ label: `${a.name} ${a.pinyin}`, value: a.name! }))}
                    onDropdownVisibleChange={loadAcupoints}
                    onFocus={loadAcupoints}
                    onChange={(v) => v && appendAcupoint('acupointRight', v as string)}
                  />
                </div>
              </div>
              <div className="tc-ap-note">穴位字典：按穴名/拼音检索（如 足三 / zusanli），选中即追加；维护见「接诊 → 穴位字典」</div>
            </section>

            <section className="tc-panel">
              <h3>针灸处方</h3>
              <div className="tc-row">
                <Select
                  className="tc-tpl-pick"
                  showSearch
                  optionFilterProp="label"
                  allowClear
                  value={undefined}
                  placeholder="套用外治模板（带出方法/取穴/疗程）"
                  options={therapyTemplates.map((t) => ({ label: `${t.name}｜${t.method}`, value: t.id! }))}
                  onDropdownVisibleChange={loadTherapyTemplates}
                  onFocus={loadTherapyTemplates}
                  onChange={(v) => v && applyTherapyTemplate(v as number)}
                />
              </div>
              <div className="tc-ap-note">
                外治模板只存建议值：套用后方法/取穴/疗程可随时改，不会写回模板；维护见「中药处方 → 外治模板」
              </div>
              <div className="tc-row cols-3">
                <ProFormText name="acuMethod" label="针法" placeholder="如 毫针、电针、温针、耳针" />
                <ProFormDigit
                  name="retentionMinutes"
                  label="留针（分钟）"
                  placeholder="如 25"
                  min={1}
                  max={240}
                  fieldProps={{ precision: 0 }}
                />
                <ProFormText name="manipulation" label="手法" placeholder="如 平补平泻、提插捻转、补法" />
              </div>
              <ProFormTextArea name="acuCourse" label="疗程/频次" placeholder="如 每周 2 次 × 4 周" rows={2} />
            </section>

            <section className="tc-panel">
              <h3>诊断与调理方案</h3>
              <div className="tc-row cols-2">
                <ProFormTextArea name="diagnose" label="诊断" placeholder="证型与疗程" />
                <ProFormTextArea name="plan" label="调理方案" placeholder="治法与取穴思路" />
              </div>
              <ProFormTextArea name="conditioning" label="调理过程" placeholder="" />
              <div className="tc-row cols-2">
                <ProFormTextArea name="diet" label="饮食" placeholder="宜忌" />
                <ProFormTextArea name="review" label="回访" placeholder="回访安排" />
              </div>
            </section>

            <section className="tc-panel tc-bill">
              <h3>
                接诊单 · {moment().format('YYYY-MM-DD')}
                <span className="plan-pill">规划功能</span>
              </h3>
              <table className="tc-table">
                <thead>
                  <tr>
                    <th>项目</th>
                    <th className="mid">次数</th>
                    <th className="num">单价</th>
                    <th className="num">小计</th>
                  </tr>
                </thead>
              </table>
              <div className="add-row">从卡项列表添加项目</div>
              <div className="plan-note">
                接诊单开单、卡项抵扣与应收合计属规划域（接诊流暂无订单创建接口），界面按原型
                docs/design/mockups/desktop-workstation 预留单据表结构，接口接入后在此渲染项目行与合计。
              </div>
            </section>
          </div>

          <aside className="tc-side">
            <div className="tc-total">
              <div className="tk">应收合计</div>
              <div className="tv hold">规划功能</div>
              <div className="td2">接诊流暂无订单/收款接口，按项目现价合计待接口接入后在此显示</div>
            </div>
            <Button className="tc-pay" type="primary" size="large" block onClick={() => formRef.current?.submit()}>
              保存接诊单
            </Button>
            <Button className="tc-pay ghost" size="large" block onClick={() => window.print()}>
              打印处方笺
            </Button>

            <section className="tc-panel">
              <h3>
                近三次诊疗
                {treatTotal > 3 && customerId ? (
                  <a onClick={() => history.push(`/treat/history?customerId=${customerId}`)}>
                    查看全部 {treatTotal} 次
                  </a>
                ) : null}
              </h3>
              {treats.length === 0 ? (
                <div className="tc-empty">该顾客暂无历史接诊，本单将是第一次</div>
              ) : (
                <div className="hx">
                  {treats.map((t) => (
                    <div className="h" key={t.id}>
                      <div className="d">
                        <b>{moment(t.createTime).format('M-DD')}</b>
                        {customer?.name || ''}
                      </div>
                      <div className="t">{t.diagnose || '接诊记录'}</div>
                      <div className="n">{t.desc || t.plan || '—'}</div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="tc-panel">
              <h3>今日工作站</h3>
              <div className="td2">
                今日接诊{' '}
                <b className="n">{todayTotal ?? '—'}</b> 单
                <br />
                打印小票 <b className="n hold">规划</b> · 扫码定位 <b className="n hold">规划</b>
              </div>
            </section>

            <section className="tc-panel">
              <h3>外设状态</h3>
              <div className="dev">
                <i className="st dim" />
                <span className="nm">80mm 小票机（USB 串口）</span>
                <span className="tag dim">桌面工作站</span>
              </div>
              <div className="dev">
                <i className="st" />
                <span className="nm">扫码枪（页顶扫码条）</span>
                <span className="tag">已就绪</span>
              </div>
              <div className="dev">
                <i className="st dim" />
                <span className="nm">A5 处方笺打印机</span>
                <span className="tag dim">系统打印</span>
              </div>
              <div className="plan-note">
                外设物理状态由桌面壳上报（serialport 串口监听），浏览器端按当前可用能力如实标注；
                小票打印待桌面壳外设链路接入。
              </div>
            </section>
          </aside>
        </div>
      </ProForm>
    </PageContainer>
  );
};

export default Create;

/** 五行生克定位图：五节点定位（火心/木肝/土脾/金肺/水肾，中宫为脾）+ 四指标实时值（随录入联动，对齐原型 admin-diagnosis.html） */
function WuxingLive() {
  const form = Form.useFormInstance();
  const benZiL = Form.useWatch('fiveBenZiLeft', form);
  const benZiR = Form.useWatch('fiveBenZiRight', form);
  const benKeL = Form.useWatch('fiveBenKeLeft', form);
  const benKeR = Form.useWatch('fiveBenKeRight', form);
  const nanJinL = Form.useWatch('fiveNanJinLeft', form);
  const nanJinR = Form.useWatch('fiveNanJinRight', form);
  const rateL = Form.useWatch('fiveRateLeft', form);
  const rateR = Form.useWatch('fiveRateRight', form);
  const pair = (l?: string, r?: string) => `${l || '-'} / ${r || '-'}`;
  return (
    <div className="wx-live">
      <div className="wx-map">
        <span className="wline h" />
        <span className="wline v" />
        <div className="wx fire">火<i>心</i></div>
        <div className="wx wood">木<i>肝</i></div>
        <div className="wx center">土<i>脾</i></div>
        <div className="wx metal">金<i>肺</i></div>
        <div className="wx water">水<i>肾</i></div>
      </div>
      <div className="wx-ratio">
        <div className="r">
          <div className="k">本<i>生</i></div>
          <div className="v">{pair(benZiL, benZiR)}</div>
        </div>
        <div className="r">
          <div className="k">克<i>乘</i></div>
          <div className="v">{pair(benKeL, benKeR)}</div>
        </div>
        <div className="r">
          <div className="k">难经</div>
          <div className="v">{pair(nanJinL, nanJinR)}</div>
        </div>
        <div className="r">
          <div className="k">缓急</div>
          <div className="v">{pair(rateL, rateR)}</div>
        </div>
      </div>
    </div>
  );
}
