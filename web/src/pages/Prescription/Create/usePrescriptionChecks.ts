import { useEffect, useRef, useState } from 'react';
import {
  checkAllergy,
  checkCompatibility,
  checkDose,
  pricePrescription,
  type AllergyResult,
  type CompatibilityResult,
  type DoseResult,
  type PrescriptionHerb,
  type Pricing,
} from '@/services/ant-design-pro/prescription';
import { listCustomerHistories } from '@/services/ant-design-pro/customer';
import type { CustomerBrief } from './helpers';

/**
 * 安全审方四查 + 实时计价：配伍（十八反/十九畏）、过敏史、剂量上限、字典试算。
 * 全部随写随查（500ms 防抖 + 序号防竞态），提示不拦截。
 */
const usePrescriptionChecks = (
  namedHerbs: string[],
  herbs: PrescriptionHerb[],
  doses: number,
  customer?: CustomerBrief,
) => {
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

  // 配伍审方：药名停顿 500ms 自动比对十八反/十九畏；少于两味不查
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
          herbs
            .filter((r) => (r.herb || '').trim())
            .map((r) => ({ herb: r.herb!, weight: r.weight })),
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

  return { compat, checking, allergy, allergyRecordCount, dose, pricing };
};

export default usePrescriptionChecks;
