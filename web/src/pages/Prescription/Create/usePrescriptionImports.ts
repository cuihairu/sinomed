import { useState } from 'react';
import { message } from 'antd';
import {
  queryEnabledTemplates,
  type PrescriptionHerb,
  type PrescriptionTemplate,
} from '@/services/ant-design-pro/prescription';
import { queryFormulaPage, type Formula } from '@/services/ant-design-pro/formula';

/** 模板/方剂带入共用的药味行写入方式：整表替换（带炮制/煎法备注） */
type ApplyHerbs = (rows: PrescriptionHerb[]) => void;

/**
 * 病症处方模板 + 方剂库带入：两者都只填当前开方单，不写回模板/方剂库。
 * 模板额外带出剂数、代煎、用法与备注。
 */
const usePrescriptionImports = (
  setHerbs: ApplyHerbs,
  setDoses: (v: number) => void,
  setDecoction: (v: boolean) => void,
  setUsage: (v: string) => void,
  setRemark: (v: string) => void,
) => {
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
        setHerbs(
          tpl.herbs.map((h) => ({
            herb: h.herb || '',
            weight: h.weight,
            special: h.special || undefined,
          })),
        );
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
    setHerbs(
      fml.herbs.map((h) => ({
        herb: h.herb || '',
        weight: h.weight,
        special: h.special || undefined,
      })),
    );
    message.success(`已按方剂「${fml.name}」带出全方 ${fml.herbs.length} 味，可继续增减`);
  };

  return {
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
  };
};

export default usePrescriptionImports;
