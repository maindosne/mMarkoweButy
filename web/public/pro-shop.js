(() => {
'use strict';
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>Number(n||0).toLocaleString('pl-PL',{style:'currency',currency:'PLN'});
let products=[]; const RECENT='mm_recent_products_v1';

function toast(message,action){
 let box=$('#mmToast'); if(!box){box=document.createElement('div');box.id='mmToast';box.className='mm-toast';document.body.append(box)}
 box.innerHTML='<span>'+esc(message)+'</span>'+(action?'<button type="button">'+esc(action.label)+'</button>':'');
 box.classList.add('show'); if(action) box.querySelector('button').onclick=()=>{action.run();box.classList.remove('show')};
 clearTimeout(box._t); box._t=setTimeout(()=>box.classList.remove('show'),2800);
}
function skeletons(){
 const grid=$('#products'); if(!grid||grid.dataset.mmSkeleton)return; grid.dataset.mmSkeleton='1';
 grid.innerHTML=Array.from({length:6},()=>'<article class="mm-skeleton"><div></div><i></i><i></i><i></i></article>').join('');
}
function setupFilters(){
 const view=$('#catalogView'), grid=$('#products'); if(!view||!grid||$('#mmCatalogTools'))return;
 const brands=[...new Set(products.map(p=>p.brand).filter(Boolean))].sort();
 const sizes=[...new Set(products.flatMap(p=>p.sizes||[]).map(String))].sort((a,b)=>parseFloat(a)-parseFloat(b));
 const tools=document.createElement('div'); tools.id='mmCatalogTools'; tools.className='mm-catalog-tools';
 tools.innerHTML=`<input id="mmSearch" type="search" placeholder="Szukaj butów lub marki…" aria-label="Szukaj produktów">
 <select id="mmBrand"><option value="">Wszystkie marki</option>${brands.map(x=>`<option>${esc(x)}</option>`).join('')}</select>
 <select id="mmSize"><option value="">Każdy rozmiar</option>${sizes.map(x=>`<option>${esc(x)}</option>`).join('')}</select>
 <select id="mmSort"><option value="default">Polecane</option><option value="new">Najnowsze</option><option value="asc">Cena: od najniższej</option><option value="desc">Cena: od najwyższej</option></select>`;
 view.querySelector('.section-head')?.after(tools);
 const apply=()=>{
  const q=$('#mmSearch').value.trim().toLowerCase(), brand=$('#mmBrand').value, size=$('#mmSize').value, sort=$('#mmSort').value;
  const cards=$$('#products .product-card');
  cards.forEach(card=>{const t=card.textContent.toLowerCase(); card.hidden=!!(q&&!t.includes(q))||!!(brand&&!t.includes(brand.toLowerCase()))||!!(size&&!t.includes('rozmiar '+size));});
  if(sort!=='default'){
   const visible=cards.slice().sort((a,b)=>{const pa=Number((a.querySelector('[data-add]')?.dataset.add)||0),pb=Number((b.querySelector('[data-add]')?.dataset.add)||0); const A=products.find(x=>Number(x.id)===pa),B=products.find(x=>Number(x.id)===pb); if(sort==='new')return pb-pa; return sort==='asc'?Number(A?.price)-Number(B?.price):Number(B?.price)-Number(A?.price)});
   visible.forEach(c=>grid.append(c));
  }
 };
 tools.addEventListener('input',apply); tools.addEventListener('change',apply);
}
function recentIds(){try{return JSON.parse(localStorage.getItem(RECENT)||'[]')}catch{return[]}}
function remember(id){let ids=recentIds().filter(x=>Number(x)!==Number(id));ids.unshift(Number(id));localStorage.setItem(RECENT,JSON.stringify(ids.slice(0,6)));renderRecent()}
function miniCard(p){return `<button type="button" class="mm-mini-card" data-details="${p.id}">${p.imageUrl?`<img src="${esc(p.imageUrl)}" alt="">`:''}<span>${esc([p.brand,p.name].filter(Boolean).join(' '))}</span><strong>${money(p.price)}</strong></button>`}
function renderRecent(){
 let sec=$('#mmRecent'); if(!sec){sec=document.createElement('section');sec.id='mmRecent';sec.className='mm-recommend';sec.innerHTML='<h2>Ostatnio oglądane</h2><div></div>';$('#catalogView')?.after(sec)}
 const rows=recentIds().map(id=>products.find(p=>Number(p.id)===Number(id))).filter(Boolean);
 sec.hidden=!rows.length; sec.querySelector('div').innerHTML=rows.map(miniCard).join('');
}
function renderSimilar(id){
 const p=products.find(x=>Number(x.id)===Number(id)), body=$('#detailBody'); if(!p||!body)return;
 const sim=products.filter(x=>Number(x.id)!==Number(id)&&(x.brand===p.brand||(x.sizes||[]).some(s=>(p.sizes||[]).map(String).includes(String(s))))).slice(0,4);
 if(sim.length){const sec=document.createElement('section');sec.className='mm-similar';sec.innerHTML='<h3>Podobne produkty</h3><div>'+sim.map(miniCard).join('')+'</div>';body.append(sec)}
 const add=body.querySelector('[data-add-details]'); if(add){add.classList.add('mm-sticky-add')}
}
function makeCartDrawer(){
 const modal=$('#cartModal'), box=modal?.querySelector('.modal-box'); if(!modal||!box)return; modal.classList.add('mm-cart-drawer'); box.setAttribute('role','dialog'); box.setAttribute('aria-label','Twój koszyk');
}
async function init(){
 skeletons();
 try{const r=await fetch('/api/products',{cache:'no-store'});const d=await r.json();products=d.products||[]}catch{}
 const wait=()=>{if($$('#products .product-card').length){setupFilters();renderRecent();makeCartDrawer()}else setTimeout(wait,120)};wait();
 document.addEventListener('click',e=>{
  const add=e.target.closest('[data-add],[data-add-details]'); if(add) setTimeout(()=>toast('Dodano do koszyka ✓'),40);
  const rem=e.target.closest('[data-remove]'); if(rem) setTimeout(()=>toast('Usunięto z koszyka'),40);
  const det=e.target.closest('[data-details]'); if(det){remember(det.dataset.details);setTimeout(()=>renderSimilar(det.dataset.details),80)}
  const mini=e.target.closest('.mm-mini-card'); if(mini){e.preventDefault(); window.openDetails?.(Number(mini.dataset.details));remember(mini.dataset.details);setTimeout(()=>renderSimilar(mini.dataset.details),80)}
  if(e.target.closest('[data-remove-favorite]')) setTimeout(()=>toast('Usunięto z ulubionych'),40);
 });
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();