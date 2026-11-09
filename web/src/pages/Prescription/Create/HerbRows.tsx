import { Button, Input, InputNumber, Select } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import React, { useState } from 'react';
import { queryHerbByPage, type Herb } from '@/services/ant-design-pro/herb';
import type { PrescriptionHerb } from '@/services/ant-design-pro/prescription';
import { fenToYuan, filterHerbDir } from './helpers';

/** 药味表 + 饮片目录：行内写药名/克数/炮制/煎法；目录字典全量一次拉齐做检索 */
const HerbRows: React.FC<{
  herbs: PrescriptionHerb[];
  setHerb: (i: number, patch: Partial<PrescriptionHerb>) => void;
  addHerb: () => void;
  removeHerb: (i: number) => void;
  pickHerb: (name: string) => void;
}> = ({ herbs, setHerb, addHerb, removeHerb, pickHerb }) => {
  /** 饮片目录：字典全量做检索（演示字典规模小，一次拉齐；拼音首字母在过滤时算并缓存） */
  const [herbDir, setHerbDir] = useState<Herb[]>([]);
  const [dirLoading, setDirLoading] = useState(false);

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

  return (
    <>
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
    </>
  );
};

export default HerbRows;
