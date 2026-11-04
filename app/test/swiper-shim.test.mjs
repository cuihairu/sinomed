import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';

function setupDom() {
  const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', {
    pretendToBeVisual: true,
    url: 'http://localhost/',
  });
  const { window } = dom;
  // Expose browser globals that swiper (ssr-window) reads at runtime.
  const globals = {
    window,
    document: window.document,
    navigator: window.navigator,
    HTMLElement: window.HTMLElement,
    Element: window.Element,
    Node: window.Node,
    HTMLSlotElement: window.HTMLSlotElement,
    HTMLDivElement: window.HTMLDivElement,
    HTMLCollection: window.HTMLCollection,
    NodeList: window.NodeList,
    MutationObserver: window.MutationObserver,
    ResizeObserver: (window.ResizeObserver || class { observe(){} unobserve(){} disconnect(){} }),
    CustomEvent: window.CustomEvent,
    Event: window.Event,
    getComputedStyle: window.getComputedStyle.bind(window),
    requestAnimationFrame: window.requestAnimationFrame.bind(window),
    cancelAnimationFrame: window.cancelAnimationFrame.bind(window),
    addEventListener: window.addEventListener.bind(window),
    removeEventListener: window.removeEventListener.bind(window),
    matchMedia: (window.matchMedia || ((q) => ({ matches: false, media: q, addListener(){}, removeListener(){}, addEventListener(){}, removeEventListener(){}, dispatchEvent(){ return false; } }))),
    screen: window.screen,
    setTimeout,
    clearTimeout,
  };
  for (const [k, v] of Object.entries(globals)) {
    if (v === undefined) continue;
    try {
      Object.defineProperty(globalThis, k, { value: v, configurable: true, writable: true });
    } catch {
      globalThis[k] = v;
    }
  }
  return { dom, window };
}

function makeSwiperDom(window, direction = 'horizontal') {
  const doc = window.document;
  const root = doc.createElement('div');
  root.className = 'swiper-container';
  const wrapper = doc.createElement('div');
  wrapper.className = 'swiper-wrapper';
  for (let i = 0; i < 3; i += 1) {
    const slide = doc.createElement('div');
    slide.className = 'swiper-slide';
    slide.textContent = `slide-${i}`;
    wrapper.appendChild(slide);
  }
  const pagination = doc.createElement('div');
  pagination.className = 'swiper-pagination';
  root.appendChild(wrapper);
  root.appendChild(pagination);
  doc.body.appendChild(root);
  return { root, wrapper, pagination };
}

async function loadShim() {
  const mod = await import('../scripts/swiper-shim/index.mjs');
  return mod.default;
}

test('shim emits swiper-container-* modifier classes (Taro v6 CSS contract)', async () => {
  const { window } = setupDom();
  const SwiperCompat = await loadShim();
  const { root, pagination } = makeSwiperDom(window, 'horizontal');

  const swiper = new SwiperCompat(root, {
    direction: 'horizontal',
    pagination: { el: pagination },
  });

  // Taro's bundled Swiper 6.8.0 CSS keys modifiers on .swiper-container-*.
  assert.ok(
    root.classList.contains('swiper-container-horizontal'),
    `expected swiper-container-horizontal, got: ${root.className}`
  );
  assert.ok(
    root.classList.contains('swiper-container-initialized'),
    `expected swiper-container-initialized, got: ${root.className}`
  );
  // Pagination bullets must be rendered so bullet positioning rules apply.
  assert.ok(
    pagination.querySelectorAll('.swiper-pagination-bullet').length >= 1,
    'pagination bullets not rendered'
  );
  // $wrapperEl / $el bridged back to Taro's jQuery-style arrays.
  assert.ok(Array.isArray(swiper.$wrapperEl), '$wrapperEl must be an array');
  assert.ok(Array.isArray(swiper.$el), '$el must be an array');
  assert.ok(swiper.$wrapperEl[0] && swiper.$wrapperEl[0].classList.contains('swiper-wrapper'));
  assert.ok(swiper.$el[0] && swiper.$el[0].classList.contains('swiper-container'));

  swiper.destroy();
});

test('shim emits swiper-container-vertical for vertical direction', async () => {
  const { window } = setupDom();
  const SwiperCompat = await loadShim();
  const { root, pagination } = makeSwiperDom(window, 'vertical');

  const swiper = new SwiperCompat(root, {
    direction: 'vertical',
    pagination: { el: pagination },
  });

  assert.ok(
    root.classList.contains('swiper-container-vertical'),
    `expected swiper-container-vertical, got: ${root.className}`
  );
  assert.ok(
    root.classList.contains('swiper-container-horizontal') === false,
    'vertical container must not carry swiper-container-horizontal'
  );

  swiper.destroy();
});
