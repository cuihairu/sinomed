
import React, {useState} from 'react';
import {createItem, fetchItem} from '@/services/ant-design-pro/item';
import {ProForm, ProFormDigit, ProFormSelect, ProFormText, ProFormTextArea} from '@ant-design/pro-components'
import {Button, Image, message, Upload} from "antd";
import {UploadOutlined} from '@ant-design/icons';
import {useSearchParams} from "@@/exports";
import {MATERIAL_UPLOAD_ACTION, uploadAuthHeaders} from '@/services/ant-design-pro/ads';

type Item = {
  id?: number;
  name?: string;
  price?: number;
  description?: string;
  /** 1 上架、0 下架（顾客 Kiosk 只展示上架项） */
  enabled?: number;
  /** 封面图相对 url（/media 托管） */
  cover?: string;
  /** Kiosk 展示顺序，小者在前 */
  sort?: number;
  createTime?: string;
  updateTime?: string;
}

/** 封面选择：走媒体上传通道（ads 的 multipart 接口），value 为返回的相对 url */
function CoverInput({value, onChange}: { value?: string; onChange?: (url: string) => void }) {
  const [uploading, setUploading] = useState(false);
  return (
    <div style={{display: 'flex', alignItems: 'center', gap: 12}}>
      {value && <Image src={value} width={64} height={64} style={{objectFit: 'cover'}}/>}
      <Upload
        accept="image/*"
        showUploadList={false}
        customRequest={async ({file, onSuccess, onError}) => {
          setUploading(true);
          try {
            const form = new FormData();
            form.append('file', file as File);
            const res = await fetch(MATERIAL_UPLOAD_ACTION, {
              method: 'POST',
              headers: uploadAuthHeaders(),
              body: form,
            });
            const data = await res.json();
            if (!res.ok || !data?.url) {
              throw new Error(data?.errorMessage || `上传失败（${res.status}）`);
            }
            onChange?.(data.url);
            onSuccess?.(data);
          } catch (e: any) {
            message.error(e?.message || '上传失败');
            onError?.(e as Error);
          } finally {
            setUploading(false);
          }
        }}
      >
        <Button loading={uploading} icon={<UploadOutlined/>}>{value ? '换图' : '上传封面'}</Button>
      </Upload>
    </div>
  );
}

const Create: React.FC = () => {
  const [searchParams ] = useSearchParams();
  return (
      <ProForm<Item>
        grid
        autoFocusFirstInput
        omitNil
        request={async ()=>{
          const itemId = searchParams.get("itemId");
          if(!itemId){
            return Promise.resolve({});
          }
          return fetchItem(itemId);
        }}
        layout={'vertical'}
        onFinish={async (values) => {
          const result = await createItem(values);
          console.log(result)
          if (result.id) {
            message.success("创建成功")
            return true;
          }
          return false;
        }

        }
      >
        <ProFormText name="id" hidden />
        <ProFormText colProps={{ md: 4, xl: 4 }} name="name" label="卡项姓名" tooltip="长度范围2-20字符,选填项" placeholder="输入卡项名字" required={true} />
        <ProFormText colProps={{ md: 4, xl: 2 }} name="price" label="价格" tooltip="价格" placeholder="20"/>
        <ProFormDigit
          colProps={{ md: 4, xl: 2 }}
          name="durationMinutes"
          label="时长（分钟）"
          placeholder="40"
          min={1}
          max={600}
          fieldProps={{ precision: 0 }}
          tooltip="单次服务时长，仅展示用，选填"
        />
        <ProFormTextArea colProps={{ md: 12, xl: 6 }} name="description" label="描述卡项的服务" tooltip="给顾客介绍卡项的内容" placeholder="养生专家"/>
        <ProFormSelect
          colProps={{ md: 4, xl: 2 }}
          name="enabled"
          label="上架状态"
          initialValue={1}
          options={[{ label: '上架', value: 1 }, { label: '下架', value: 0 }]}
          tooltip="下架后顾客 Kiosk 不再展示"
        />
        <ProFormDigit
          colProps={{ md: 4, xl: 2 }}
          name="sort"
          label="排序"
          initialValue={0}
          fieldProps={{ precision: 0 }}
          tooltip="Kiosk 展示顺序，小者在前"
        />
        <ProForm.Item colProps={{ md: 8, xl: 4 }} name="cover" label="封面图" tooltip="顾客 Kiosk 网格展示用，选填">
          <CoverInput />
        </ProForm.Item>
      </ProForm>);
};

export default Create;
