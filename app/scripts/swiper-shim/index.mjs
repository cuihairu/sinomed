// 兼容层：把 swiper 12（仅 .mjs、用原生 el/wrapperEl DOM）桥接回
// Taro 3.6.40 @tarojs/components 的 swiper web-component 期望的旧 API：
//   - 导入路径 swiper/swiper-bundle.esm.js（v12 已改为 .mjs，经 webpack alias 指到本文件）
//   - 实例属性 $wrapperEl / $el（v6 的 jQuery 风格数组，v12 已改为原生 el / wrapperEl）
//   - 容器修饰类前缀（v6 为 swiper-container-，v12 默认改为 swiper-；而
//     @tarojs/components 内联的是 Swiper 6.8.0 的 CSS（.swiper-container-* 修饰类），
//     若不回退该前缀，.swiper-container-horizontal>.swiper-pagination-bullets 等
//     纵向布局/分页圆点定位规则会因类名不匹配而失效）
import Swiper12 from 'swiper/bundle';

class SwiperCompat extends Swiper12 {
  constructor(selector, params) {
    // 在 super 初始化（含 addClasses）前把修饰类前缀回退到 v6 的 swiper-container-，
    // 使 v12 实际加到容器上的类与 Taro 内联的 Swiper 6.8.0 CSS 选择器一致。
    super(selector, {
      ...(params || {}),
      containerModifierClass: 'swiper-container-',
    });
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
