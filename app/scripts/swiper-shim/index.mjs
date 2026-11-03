// 兼容层：把 swiper 12（仅 .mjs、用原生 el/wrapperEl DOM）桥接回
// Taro 3.6.40 @tarojs/components 的 swiper web-component 期望的旧 API：
//   - 导入路径 swiper/swiper-bundle.esm.js（v12 已改为 .mjs，经 webpack alias 指到本文件）
//   - 实例属性 $wrapperEl / $el（v6 的 jQuery 风格数组，v12 已改为原生 el / wrapperEl）
import Swiper12 from 'swiper/bundle';

class SwiperCompat extends Swiper12 {
  constructor(selector, params) {
    super(selector, params);
    const self = this;
    // v6 的 $el / $wrapperEl 是数组（[domNode]），v12 用原生 el / wrapperEl。
    // Taro 代码访问 this.swiper.$wrapperEl[0] 与 this.swiper.$el[0]。
    Object.defineProperty(this, '$el', {
      get() { return self.el ? [self.el] : undefined; },
      configurable: true,
    });
    Object.defineProperty(this, '$wrapperEl', {
      get() { return self.wrapperEl ? [self.wrapperEl] : undefined; },
      configurable: true,
    });
  }
}

export default SwiperCompat;
