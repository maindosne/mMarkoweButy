
(() => {
  'use strict';

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const money = (n) => Number(n || 0).toLocaleString('pl-PL', { style: 'currency', currency: 'PLN' });
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const norm = (v) => String(v || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/ł/g, 'l');

  let products = [];

  function bringThemeToFront() {
    const link = document.getElementById('luxuryTheme');
    if (link) document.head.appendChild(link);
  }

  function preferredWomen(list) {
    return list.find((p) => /damsk|kobiet/.test(norm([p.name, p.description, p.brand].join(' ')))) || list[0];
  }

  function preferredMen(list) {
    return list.find((p) => /mesk|męsk/.test(norm([p.name, p.description, p.brand].join(' '))))
      || list.find((p) => !/damsk|kobiet/.test(norm([p.name, p.description].join(' '))))
      || list[1]
      || list[0];
  }

  function currentImage(p) {
    if (!p) return '';
    if (p.imageUrl) return p.imageUrl;
    if (Array.isArray(p.imageUrls) && p.imageUrls.length) return p.imageUrls[0];
    return '';
  }

  function setHero(p) {
    const img = $('#luxHeroImage');
    const meta = $('#luxHeroMeta');
    if (!p || !img || !meta) return;

    const src = currentImage(p);
    if (src) {
      img.src = src;
      img.alt = [p.brand, p.name].filter(Boolean).join(' ');
      img.dataset.details = p.id;
      img.style.cursor = 'pointer';
    }
    meta.innerHTML =
      '<span>Polecana para</span>' +
      '<strong>' + esc([p.brand, p.name].filter(Boolean).join(' ')) + '</strong>' +
      '<b>' + money(p.price) + '</b>';
    meta.dataset.details = p.id;
    meta.style.cursor = 'pointer';
  }

  function setCategoryImage(selector, product) {
    const img = $(selector);
    const src = currentImage(product);
    if (img && src) {
      img.src = src;
      img.alt = [product.brand, product.name].filter(Boolean).join(' ');
    }
  }

  function productCard(p, index) {
    const old = Number(p.oldPrice || 0);
    const sale = old > Number(p.price || 0);
    const image = currentImage(p);
    return '<article class="lux-product-card">' +
      '<div class="lux-product-photo" data-details="' + Number(p.id) + '">' +
      (image ? '<img src="' + esc(image) + '" alt="' + esc([p.brand,p.name].filter(Boolean).join(' ')) + '" loading="lazy" decoding="async">' : '') +
      (index === 0 ? '<span class="lux-product-badge">Nowość</span>' : '') +
      '<span class="lux-product-heart" aria-hidden="true">♡</span>' +
      '</div>' +
      '<div class="lux-product-body">' +
      '<div class="lux-product-brand">' + esc(p.brand || 'MARKOWE BUTY') + '</div>' +
      '<div class="lux-product-name">' + esc(p.name || 'Dostępna para') + '</div>' +
      '<div class="lux-product-size">Rozmiar ' + esc((p.sizes || []).join(', ') || '—') + '</div>' +
      '<div class="lux-product-bottom"><div>' +
      '<span class="lux-product-price">' + money(p.price) + '</span>' +
      (sale ? '<span class="lux-product-old">' + money(old) + '</span>' : '') +
      '</div>' +
      '<button class="lux-add" type="button" data-add="' + Number(p.id) + '" aria-label="Dodaj do koszyka">' +
      '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3.5 4.5h2l1.8 10.2h10.5l2-7.2H7.2M9.4 20a1.4 1.4 0 1 0 0-2.8 1.4 1.4 0 0 0 0 2.8ZM17 20a1.4 1.4 0 1 0 0-2.8A1.4 1.4 0 0 0 17 20Z" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '</button></div></div></article>';
  }

  function renderFeatured() {
    const box = $('#luxFeaturedProducts');
    if (!box) return;
    if (!products.length) {
      box.innerHTML = '<div class="panel empty">Aktualnie nie ma dostępnych ofert.</div>';
      return;
    }
    box.innerHTML = products.slice(0, 5).map(productCard).join('');
  }

  function choosePromo() {
    return products.find((p) => Number(p.oldPrice || 0) > Number(p.price || 0))
      || products[3]
      || products[0];
  }

  function selectAudience(value) {
    const radio = document.querySelector('input[name="audience"][value="' + value + '"]');
    const section = $('#discoverIntro');
    if (radio) radio.checked = true;
    if (section) {
      section.hidden = false;
      section.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  function openCatalog(search) {
    search = search || '';
    $('#catalogButton')?.click();
    const apply = () => {
      const field = $('#mmSearch') || $('#mm-product-search');
      if (!field) return false;
      if (search) {
        field.value = search;
        field.dispatchEvent(new Event('input', { bubbles: true }));
      }
      $('#catalogView')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return true;
    };
    if (!apply()) {
      let tries = 0;
      const timer = setInterval(() => {
        tries++;
        if (apply() || tries > 20) clearInterval(timer);
      }, 120);
    }
  }

  function bindNavigation() {
    $$('[data-lux-audience]').forEach((el) => {
      el.addEventListener('click', (e) => {
        e.preventDefault();
        selectAudience(el.dataset.luxAudience);
      });
    });

    $$('[data-lux-catalog]').forEach((el) => {
      el.addEventListener('click', (e) => {
        e.preventDefault();
        openCatalog(el.dataset.luxCatalog || '');
      });
    });

    $('#luxFindPair')?.addEventListener('click', () => {
      $('#discoverIntro')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });

    $('#luxHeaderCart')?.addEventListener('click', () => $('#cartButton')?.click());

    $('#luxHeaderSearchForm')?.addEventListener('submit', (e) => {
      e.preventDefault();
      openCatalog($('#luxHeaderSearch')?.value?.trim() || '');
    });

    $('#luxBrowseAll')?.addEventListener('click', () => openCatalog(''));
    $('#luxNewsletterBrowse')?.addEventListener('click', () => openCatalog(''));
    $('#luxNewsletterPair')?.addEventListener('click', () => $('#discoverIntro')?.scrollIntoView({ behavior: 'smooth' }));
  }

  async function loadProducts() {
    try {
      const r = await fetch('/api/products', { cache: 'no-store' });
      const d = await r.json();
      products = Array.isArray(d.products) ? d.products : [];
    } catch {
      products = [];
    }

    if (!products.length) return;

    const women = preferredWomen(products);
    const men = preferredMen(products);
    const newer = products[1] || women;
    const promo = choosePromo();

    setHero(women);
    setCategoryImage('#luxCategoryWomenImg', women);
    setCategoryImage('#luxCategoryMenImg', men);
    setCategoryImage('#luxCategoryNewImg', newer);
    setCategoryImage('#luxCategoryPromoImg', promo);
    renderFeatured();
  }

  function keepThemeLastForStartup() {
    bringThemeToFront();
    const observer = new MutationObserver(() => bringThemeToFront());
    observer.observe(document.head, { childList: true });
    setTimeout(() => observer.disconnect(), 5500);
    [250, 750, 1500, 3000, 5000].forEach((ms) => setTimeout(bringThemeToFront, ms));
  }

  async function init() {
    keepThemeLastForStartup();
    bindNavigation();
    await loadProducts();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();