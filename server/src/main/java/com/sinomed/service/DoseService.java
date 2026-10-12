package com.sinomed.service;

import com.sinomed.vo.DoseResultView;
import com.sinomed.vo.PrescriptionItemView;

import java.util.List;

/**
 * 剂量审方：按药材字典单剂克数上限比对当前药味，提示不拦截——开方是否照用由医师判断。
 */
public interface DoseService {

    /**
     * 按药材字典 dose_max（单剂克数上限，可空=不预警）比对当前药味单剂克数：
     * weight 严格大于上限才命中（等值不提示）。字典未收录药名进 unmatched（无参照），
     * 未设上限的药味不参与比对。全部为空抛「药材名单不能为空」。
     */
    DoseResultView check(List<PrescriptionItemView> herbs);
}
