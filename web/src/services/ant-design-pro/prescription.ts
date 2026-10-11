import { request } from '@umijs/max';

/** 药味：药材名为自由文本（MVP 无药材字典/库存），weight 为单剂克数 */
export interface PrescriptionHerb {
  id?: number;
  herb?: string;
  weight?: number;
  /** 特殊煎法：先煎/后下/包煎等，可空 */
  special?: string;
  /** 炮制：麸炒/蜜炙/切段生用等，可空 */
  process?: string;
  sort?: number;
}

export interface Prescription {
  id?: number;
  treatId?: number;
  customerId?: number;
  /** 列表联出的展示字段 */
  customerName?: string;
  staffId?: number;
  /** 列表联出的展示字段 */
  staffName?: string;
  /** 剂数（几付） */
  doses?: number;
  /** 煎法：水煎服，日一剂等 */
  usage?: string;
  /** 服法：分早晚温服等，可空 */
  takeMethod?: string;
  remark?: string;
  /** 详情/列表联出的药味（按 sort 排序） */
  herbs?: PrescriptionHerb[];
  /** 按药材字典实时算出的计价（无快照） */
  pricing?: Pricing;
  /** 代煎状态：0 无需代煎 / 1 待煎 / 2 可取 / 3 已取 */
  decoctionStatus?: number;
  /** 代煎袋数（=剂数；无需代煎为空） */
  decoctionBags?: number;
  /** 代煎服务费（分，袋数×300，实时算不落库） */
  decoctionFeeFen?: number;
  /** 处方类型：0 汤剂 / 1 膏方 */
  prescriptionType?: number;
  /** 膏方领取状态：0 非膏方 / 1 待制作 / 2 可取 / 3 已取 */
  pasteStatus?: number;
  /** 收膏方式（仅膏方） */
  craft?: string;
  /** 发药状态：0 待发药 / 1 已发药（代煎/膏方「已取」自动置 1） */
  dispenseStatus?: number;
  /** 发药时间（待发药为空） */
  dispenseTime?: string;
  /** 药师审核人 staff id（签名行，可空） */
  reviewStaffId?: number;
  /** 调配 / 复核人 staff id（签名行，可空） */
  checkStaffId?: number;
  /** 领取方式：0 自取 / 1 同城配送（仅代煎有意义，可空=自取） */
  deliveryType?: number;
  createTime?: string;
}

/** 代煎状态文案 */
export const DECOCTION_TEXT: Record<number, string> = {
  0: '无需代煎',
  1: '待煎',
  2: '可取',
  3: '已取',
};

/** 膏方状态文案 */
export const PASTE_TEXT: Record<number, string> = {
  0: '非膏方',
  1: '待制作',
  2: '可取',
  3: '已取',
};

export interface PrescriptionPageResult {
  data?: Prescription[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 开方：customerId + herbs（至少 1 味）必填；doses 默认 7；服务端校验顾客与剂量 */
export async function createPrescription(
  body: {
    customerId: number;
    staffId?: number;
    /** 药师审核人 staff id（签名行，可空） */
    reviewStaffId?: number;
    /** 调配 / 复核人 staff id（签名行，可空） */
    checkStaffId?: number;
    treatId?: number;
    doses?: number;
    /** 是否代煎：袋数=剂数，落「待煎」（汤剂用） */
    decoction?: boolean;
    /** 领取方式：0 自取 / 1 同城配送（仅代煎有意义，可空=自取） */
    deliveryType?: number;
    /** 处方类型：0 汤剂（默认）/ 1 膏方（落「待制作」） */
    prescriptionType?: number;
    /** 收膏方式（仅膏方：炼蜜/清膏/糖膏/阿胶收膏等） */
    craft?: string;
    usage?: string;
    /** 服法（可空） */
    takeMethod?: string;
    remark?: string;
    herbs: PrescriptionHerb[];
  },
  options?: { [key: string]: any },
) {
  return request<Prescription>('/api/v1/prescription/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 处方分页（id 倒序，联出顾客/医师/药味）；customerId 可选过滤 */
export async function queryPrescriptionPage(
  params: { current?: number; pageSize?: number; customerId?: number },
  options?: { [key: string]: any },
) {
  return request<PrescriptionPageResult>('/api/v1/prescription/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 处方详情（含药味） */
export async function fetchPrescription(id: number, options?: { [key: string]: any }) {
  return request<Prescription>(`/api/v1/prescription/${id}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 删除处方（连同药味；演示环境口径，无留痕） */
export async function deletePrescription(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/prescription/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

/** 配伍审方命中条目：level 禁忌（十八反）/ 慎用（十九畏） */
export interface CompatibilityFinding {
  level?: string;
  rule?: string;
  a?: string;
  b?: string;
  note?: string;
}

export interface CompatibilityResult {
  /** 参与比对的药材数 */
  checked?: number;
  /** 空即未发现配伍禁忌 */
  findings?: CompatibilityFinding[];
}

/** 配伍审方：经典十八反/十九畏静态规则；自由文本药名按别名包含匹配；提示不拦截 */
export async function checkCompatibility(
  herbs: string[],
  options?: { [key: string]: any },
) {
  return request<CompatibilityResult>('/api/v1/prescription/compatibility', {
    method: 'POST',
    data: { herbs },
    ...(options || {}),
  });
}

/** 过敏审方命中条目：命中药味 + 顾客过敏史原文 */
export interface AllergyFinding {
  herb?: string;
  historyId?: number;
  content?: string;
}

export interface AllergyResult {
  customerId?: number;
  /** 参与比对的药材数 */
  checked?: number;
  /** 空即顾客过敏史未提到当前药味 */
  findings?: AllergyFinding[];
}

/** 过敏审方：按顾客过敏史（customer_histories type=0）比对，原文包含药名即命中；提示不拦截 */
export async function checkAllergy(
  customerId: number,
  herbs: string[],
  options?: { [key: string]: any },
) {
  return request<AllergyResult>('/api/v1/prescription/allergy-check', {
    method: 'POST',
    data: { customerId, herbs },
    ...(options || {}),
  });
}

/** 处方计价：按药材字典实时试算；totalFen 仅含已比价药味，分单位 */
export interface Pricing {
  totalFen?: number;
  perDoseFen?: number;
  doses?: number;
  herbCount?: number;
  pricedHerbCount?: number;
  unknownHerbs?: string[];
}

/** 代煎流转：待煎(1)→可取(2)→已取(3) 顺序推进，其余 400 */
export async function setDecoctionStatus(
  id: number,
  status: 2 | 3,
  options?: { [key: string]: any },
) {
  return request<Prescription>(`/api/v1/prescription/${id}/decoction?status=${status}`, {
    method: 'PUT',
    ...(options || {}),
  });
}

/** 膏方领取流转：待制作(1)→可取(2)→已取(3) 顺序推进，非膏方与回退/跳跃 400 */
export async function setPasteStatus(
  id: number,
  status: 2 | 3,
  options?: { [key: string]: any },
) {
  return request<Prescription>(`/api/v1/prescription/${id}/paste?status=${status}`, {
    method: 'PUT',
    ...(options || {}),
  });
}

/** 处方试算：不开方只算钱；未收录药名计入 unknownHerbs 不计费 */
export async function pricePrescription(
  body: { doses?: number; herbs: PrescriptionHerb[] },
  options?: { [key: string]: any },
) {
  return request<Pricing>('/api/v1/prescription/price', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 药房工作台行：联顾客姓名手机、代煎/膏方状态文案与药味数 */
export interface DispenseRow {
  id?: number;
  customerName?: string;
  customerPhone?: string;
  /** 处方类型：0 汤剂 / 1 膏方 */
  prescriptionType?: number;
  doses?: number;
  /** 药味数 */
  itemCount?: number;
  /** 服务端联出的状态文案：无需代煎/待煎/可取/已取 */
  decoctionText?: string;
  /** 服务端联出的状态文案：非膏方/待制作/可取/已取 */
  pasteText?: string;
  /** 0 待发药 / 1 已发药 */
  dispenseStatus?: number;
  dispenseTime?: string;
  createTime?: string;
}

export interface DispensePageResult {
  data?: DispenseRow[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 发药状态文案 */
export const DISPENSE_TEXT: Record<number, string> = {
  0: '待发药',
  1: '已发药',
};

/**
 * 药房发药：待发药(0)→已发药(1) 单向，落发药流水；操作人取当前登录账号；
 * note ≤100 字可选。已发药再发 400；代煎/膏方「已取」已自动置 1，无需重复点发药。
 */
export async function dispensePrescription(
  id: number,
  note?: string,
  options?: { [key: string]: any },
) {
  return request<DispenseRow>(`/api/v1/prescription/${id}/dispense`, {
    method: 'POST',
    data: note ? { note } : {},
    ...(options || {}),
  });
}

/** 药房工作台分页：dispenseStatus 可空（0 待发药 / 1 已发药），customerKeyword 按顾客姓名/手机模糊 */
export async function queryDispensePage(
  params: { current?: number; pageSize?: number; dispenseStatus?: number; customerKeyword?: string },
  options?: { [key: string]: any },
) {
  return request<DispensePageResult>('/api/v1/prescription/dispense/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 病症处方模板：模板只存建议值，套用后随开方单自由改（不改回模板） */
export interface PrescriptionTemplate {
  id?: number;
  /** 病症名（唯一，1–20 字） */
  name?: string;
  /** 建议剂数（默认 7） */
  doses?: number;
  /** 建议代煎：0 无需 / 1 代煎 */
  decoction?: number;
  usage?: string;
  remark?: string;
  /** 上架：0 停用 / 1 启用 */
  enabled?: number;
  herbs?: PrescriptionHerb[];
  createTime?: string;
  updateTime?: string;
}

export interface TemplatePageResult {
  data?: PrescriptionTemplate[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 建模板：name + herbs（至少 1 味）必填；doses 默认 7、enabled 默认 1；病症名唯一 */
export async function createTemplate(
  body: PrescriptionTemplate,
  options?: { [key: string]: any },
) {
  return request<PrescriptionTemplate>('/api/v1/prescription/template/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 更新模板：按 id 全量更新，药味全量替换；换名撞其他模板报 400 */
export async function updateTemplate(
  body: PrescriptionTemplate,
  options?: { [key: string]: any },
) {
  return request<PrescriptionTemplate>('/api/v1/prescription/template/', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

/** 模板分页：id 倒序；name 模糊过滤可选（含停用，管理端口径） */
export async function queryTemplatePage(
  params: { current?: number; pageSize?: number; name?: string },
  options?: { [key: string]: any },
) {
  return request<TemplatePageResult>('/api/v1/prescription/template/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 上架模板（enabled=1，id 升序，附药味）：开方页「套用模板」取数入口 */
export async function queryEnabledTemplates(
  options?: { [key: string]: any },
) {
  return request<PrescriptionTemplate[]>('/api/v1/prescription/template/enabled', {
    method: 'GET',
    ...(options || {}),
  });
}

/** 删除模板（连同药味；演示环境口径，无留痕） */
export async function deleteTemplate(
  id: number,
  options?: { [key: string]: any },
) {
  return request<{ message?: string }>(`/api/v1/prescription/template/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
