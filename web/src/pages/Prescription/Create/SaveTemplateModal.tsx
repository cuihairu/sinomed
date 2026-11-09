import { Input, Modal } from 'antd';
import React from 'react';

/** 存入我的方剂库弹窗：以病症名（1–20 字唯一）把当前方存为启用模板 */
const SaveTemplateModal: React.FC<{
  open: boolean;
  saving: boolean;
  decoction: boolean;
  tplName: string;
  setTplName: (v: string) => void;
  onOk: () => void;
  onCancel: () => void;
}> = ({ open, saving, decoction, tplName, setTplName, onOk, onCancel }) => (
  <Modal
    title="存入我的方剂库"
    open={open}
    confirmLoading={saving}
    okText="存入"
    cancelText="取消"
    onOk={onOk}
    onCancel={onCancel}
  >
    <div className="tpl-tip">
      以病症名存为启用模板（1–20 字，唯一），含当前药味、剂数{decoction ? '与代煎建议' : ''}
      ；可在开方页「套用模板」带出。
    </div>
    <Input
      placeholder="病症名，如 脾胃虚寒"
      maxLength={20}
      value={tplName}
      onChange={(e) => setTplName(e.target.value)}
    />
  </Modal>
);

export default SaveTemplateModal;
