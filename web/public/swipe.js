(() => {
  let all = [];
  let queue = [];
  let index = 0;
  let history = [];
  let animating = false;
  let hasStarted = false;

  const favorites = new Set(
    JSON.parse(localStorage.getItem('mm_favorites') || '[]').map(String),
  );
  const filters = { audience: '', size: '' };
  const motionMs = 280;
  const $ = (id) => document.getElementById(id);
  const intro = $('discoverIntro');
  const swipe = $('swipeView');
  const catalog = $('catalogView');
  const favView = $('favoritesView');
  const deck = $('swipeDeck');

  function saveFav() {
    localStorage.setItem('mm_favorites', JSON.stringify([...favorites]));
    $('favoritesCount').textContent = favorites.size;
  }

  function normalize(value) {
    return String(value || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }

  function audienceMatch(product, audience) {
    if (audience === 'wszystkie') return true;
    const text = normalize([product.name, product.description, product.brand].join(' '));
    if (audience === 'damskie') return !/mesk|męsk/.test(text) || /damsk|kobiet/.test(text);
    if (audience === 'meskie') return !/damsk|kobiet/.test(text) || /mesk|męsk/.test(text);
    return true;
  }

  function sizeMatch(product, selectedSize) {
    return !selectedSize || (product.sizes || []).map(String).includes(selectedSize);
  }

  async function load() {
    try {
      const response = await fetch('/api/products', { cache: 'no-store' });
      const data = await response.json();
      all = data.products || [];
      // Remove stale favorites that no longer exist in the currently available catalog.
      const availableIds = new Set(all.map((product) => String(product.id)));
      [...favorites].forEach((id) => {
        if (!availableIds.has(id)) favorites.delete(id);
      });
      const sizes = [...new Set(all.flatMap((product) => product.sizes || []).map(String))]
        .sort((a, b) => parseFloat(a) - parseFloat(b));
      $('shoeSize').innerHTML = '<option value="">Wszystkie rozmiary</option>'
        + sizes.map((size) => `<option>${esc(size)}</option>`).join('');
      saveFav();
    } catch (error) {
      deck.innerHTML = '<div class="empty-deck"><div><h2>Nie udało się pobrać butów</h2><p>Spróbuj ponownie za chwilę.</p></div></div>';
    }
  }

  function esc(value) {
    const element = document.createElement('div');
    element.textContent = value ?? '';
    return element.innerHTML;
  }

  function price(product) {
    return Number(product.price || 0).toLocaleString('pl-PL', {
      style: 'currency',
      currency: 'PLN',
    });
  }

  function audienceLabel() {
    if (filters.audience === 'meskie') return 'Męskie';
    if (filters.audience === 'damskie') return 'Damskie';
    return 'Wszystkie';
  }

  function start() {
    queue = all.filter((product) => (
      sizeMatch(product, filters.size) && audienceMatch(product, filters.audience)
    ));
    index = 0;
    history = [];
    hasStarted = true;
    intro.hidden = true;
    catalog.hidden = true;
    favView.hidden = true;
    swipe.hidden = false;
    $('filterSummary').textContent = filters.size
      ? `${audienceLabel()} • rozmiar ${filters.size}`
      : `${audienceLabel()} • wszystkie rozmiary`;
    render();
  }

  function resetUnderCard() {
    const under = deck.querySelector('[data-position="back"]');
    if (!under) return;
    under.style.transition = `transform ${motionMs}ms cubic-bezier(.2,.78,.25,1)`;
    under.style.transform = 'translate3d(0,10px,0) scale(.965)';
  }

  function card(product) {
    const element = document.createElement('article');
    element.className = 'swipe-card';
    element.innerHTML = '<span class="swipe-stamp nope-stamp">DALEJ</span>'
      + '<span class="swipe-stamp like-stamp">❤ TAK</span>'
      + (product.imageUrl
        ? `<img draggable="false" decoding="async" src="${esc(product.imageUrl)}" alt="${esc(`${product.brand || ''} ${product.name || ''}`.trim())}">`
        : '<div style="height:72%;background:#f3f3f3"></div>')
      + `<div class="swipe-info"><h2>${esc([product.brand, product.name].filter(Boolean).join(' '))}</h2>`
      + `<div class="price">${price(product)}</div>`
      + `<div class="meta">Rozmiar ${esc((product.sizes || []).join(', '))} • dotknij, aby zobaczyć szczegóły</div></div>`;

    const likeStamp = element.querySelector('.like-stamp');
    const nopeStamp = element.querySelector('.nope-stamp');
    let startX = 0;
    let startY = 0;
    let dx = 0;
    let dy = 0;
    let lastClientX = 0;
    let lastTime = 0;
    let velocityX = 0;
    let dragging = false;
    let moved = false;
    let frame = 0;

    const draw = () => {
      frame = 0;
      const rotation = Math.max(-12, Math.min(12, dx / 24));
      element.style.transform = `translate3d(${dx}px,${dy * 0.08}px,0) rotateZ(${rotation}deg)`;
      likeStamp.style.opacity = Math.max(0, Math.min(1, dx / 100));
      nopeStamp.style.opacity = Math.max(0, Math.min(1, -dx / 100));

      const under = deck.querySelector('[data-position="back"]');
      if (under) {
        const progress = Math.min(1, Math.abs(dx) / Math.max(140, element.clientWidth * 0.42));
        const scale = 0.965 + (0.035 * progress);
        const offset = 10 * (1 - progress);
        under.style.transition = 'none';
        under.style.transform = `translate3d(0,${offset}px,0) scale(${scale})`;
      }
    };

    const scheduleDraw = () => {
      if (!frame) frame = requestAnimationFrame(draw);
    };

    const finishPointer = (cancelled = false) => {
      if (!dragging) return;
      dragging = false;
      element.classList.remove('is-dragging');
      if (frame) {
        cancelAnimationFrame(frame);
        draw();
      }

      const distanceThreshold = Math.min(96, element.clientWidth * 0.22);
      const quickFlick = Math.abs(dx) > 24 && Math.abs(velocityX) > 0.45;
      if (!cancelled && (Math.abs(dx) > distanceThreshold || quickFlick)) {
        act(dx > 0);
        return;
      }

      element.style.transition = `transform ${motionMs}ms cubic-bezier(.2,.78,.25,1)`;
      element.style.transform = 'translate3d(0,0,0) rotateZ(0deg)';
      likeStamp.style.transition = 'opacity 180ms ease-out';
      nopeStamp.style.transition = 'opacity 180ms ease-out';
      likeStamp.style.opacity = '0';
      nopeStamp.style.opacity = '0';
      resetUnderCard();
    };

    element.addEventListener('pointerdown', (event) => {
      if (animating || (event.pointerType === 'mouse' && event.button !== 0)) return;
      dragging = true;
      moved = false;
      dx = 0;
      dy = 0;
      velocityX = 0;
      startX = event.clientX;
      startY = event.clientY;
      lastClientX = event.clientX;
      lastTime = performance.now();
      element.style.transition = 'none';
      likeStamp.style.transition = 'none';
      nopeStamp.style.transition = 'none';
      element.classList.add('is-dragging');
      element.setPointerCapture(event.pointerId);
    });

    element.addEventListener('pointermove', (event) => {
      if (!dragging) return;
      dx = event.clientX - startX;
      dy = event.clientY - startY;
      moved = moved || Math.abs(dx) > 7 || Math.abs(dy) > 7;
      const now = performance.now();
      const elapsed = Math.max(1, now - lastTime);
      velocityX = (event.clientX - lastClientX) / elapsed;
      lastClientX = event.clientX;
      lastTime = now;
      if (Math.abs(dx) > Math.abs(dy)) event.preventDefault();
      scheduleDraw();
    }, { passive: false });

    element.addEventListener('pointerup', () => {
      if (performance.now() - lastTime > 80) velocityX = 0;
      finishPointer(false);
    });
    element.addEventListener('pointercancel', () => finishPointer(true));
    element.addEventListener('dragstart', (event) => event.preventDefault());
    element.addEventListener('click', () => {
      if (!moved && !animating && typeof window.openDetails === 'function') {
        window.openDetails(product.id);
      }
    });
    return element;
  }

  function render() {
    animating = false;
    deck.innerHTML = '';
    $('progress').textContent = queue.length
      ? `${Math.min(index + 1, queue.length)} / ${queue.length}`
      : '';

    if (index >= queue.length) {
      const heading = filters.size
        ? `To wszystkie pary w rozmiarze ${esc(filters.size)}.`
        : 'To wszystkie dostępne pary.';
      deck.innerHTML = `<div class="empty-deck"><div><h2>${heading}</h2><p>Masz ${favorites.size} zapisanych ulubionych.</p><button class="start-btn" id="showFavEnd">Zobacz ulubione ❤</button></div></div>`;
      $('showFavEnd').onclick = showFavorites;
      return;
    }

    if (queue[index + 1]) {
      const backCard = card(queue[index + 1]);
      backCard.classList.add('is-back');
      backCard.dataset.position = 'back';
      backCard.style.transform = 'translate3d(0,10px,0) scale(.965)';
      deck.appendChild(backCard);
    }

    const frontCard = card(queue[index]);
    frontCard.dataset.position = 'front';
    deck.appendChild(frontCard);
  }

  function act(like) {
    if (animating || index >= queue.length) return;
    animating = true;
    const product = queue[index];
    const top = deck.querySelector('[data-position="front"]');
    if (!top) {
      animating = false;
      return;
    }

    history.push({
      index,
      liked: favorites.has(String(product.id)),
      id: String(product.id),
    });
    if (like) favorites.add(String(product.id));
    saveFav();

    const under = deck.querySelector('[data-position="back"]');
    const selectedStamp = top.querySelector(like ? '.like-stamp' : '.nope-stamp');
    const direction = like ? 1 : -1;
    const exitDistance = Math.max(window.innerWidth, deck.clientWidth) + (top.clientWidth * 0.6);
    let finished = false;

    top.classList.remove('is-dragging');
    top.style.transition = `transform ${motionMs}ms cubic-bezier(.18,.8,.25,1),opacity 220ms ease-out`;
    selectedStamp.style.transition = 'opacity 120ms ease-out';
    selectedStamp.style.opacity = '1';
    if (under) {
      under.style.transition = `transform ${motionMs}ms cubic-bezier(.18,.8,.25,1)`;
      under.style.transform = 'translate3d(0,0,0) scale(1)';
    }

    const finish = () => {
      if (finished) return;
      finished = true;
      index += 1;
      render();
    };

    top.addEventListener('transitionend', (event) => {
      if (event.propertyName === 'transform') finish();
    });

    requestAnimationFrame(() => {
      top.style.transform = `translate3d(${direction * exitDistance}px,0,0) rotateZ(${direction * 15}deg)`;
      top.style.opacity = '0';
    });
    window.setTimeout(finish, motionMs + 90);
  }

  function undo() {
    if (animating) return;
    const previous = history.pop();
    if (!previous) return;
    index = previous.index;
    if (previous.liked) favorites.add(previous.id);
    else favorites.delete(previous.id);
    saveFav();
    render();
  }

  function showCatalog() {
    intro.hidden = true;
    swipe.hidden = true;
    favView.hidden = true;
    catalog.hidden = false;
  }

  function showFavorites() {
    intro.hidden = true;
    swipe.hidden = true;
    catalog.hidden = true;
    favView.hidden = false;
    const items = all.filter((product) => favorites.has(String(product.id)));
    $('favoritesGrid').innerHTML = items.length
      ? items.map((product) => `<article class="panel"><div onclick="window.openDetails&&openDetails(${Number(product.id)})" style="cursor:pointer">${product.imageUrl ? `<img src="${esc(product.imageUrl)}" alt="" style="width:100%;aspect-ratio:1/1;object-fit:cover;border-radius:14px">` : ''}<h3>${esc([product.brand, product.name].join(' '))}</h3><strong>${price(product)}</strong><div class="favorite-note">Rozmiar ${esc((product.sizes || []).join(', '))}</div></div><button type="button" class="remove-favorite-btn" data-remove-favorite="${Number(product.id)}" aria-label="Usuń z ulubionych">Usuń z ulubionych ♡</button></article>`).join('')
      : '<div class="panel">Nie masz jeszcze ulubionych. Przesuń wybraną parę w prawo.</div>';
    $('favoritesGrid').querySelectorAll('[data-remove-favorite]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        favorites.delete(String(button.dataset.removeFavorite));
        saveFav();
        showFavorites();
      });
    });
  }

  function back() {
    catalog.hidden = true;
    favView.hidden = true;
    if (hasStarted) swipe.hidden = false;
    else intro.hidden = false;
  }

  $('discoverForm').addEventListener('submit', (event) => {
    event.preventDefault();
    filters.audience = new FormData(event.currentTarget).get('audience');
    filters.size = $('shoeSize').value;
    start();
  });
  $('rejectButton').onclick = () => act(false);
  $('likeButton').onclick = () => act(true);
  $('undoButton').onclick = undo;
  $('changeFilters').onclick = () => {
    swipe.hidden = true;
    intro.hidden = false;
  };
  $('catalogButton').onclick = showCatalog;
  $('introCatalogButton').onclick = showCatalog;
  $('favoritesButton').onclick = showFavorites;
  document.querySelectorAll('.backToSwipe').forEach((button) => {
    button.onclick = back;
  });
  document.addEventListener('keydown', (event) => {
    if (!swipe.hidden && event.key === 'ArrowLeft') act(false);
    if (!swipe.hidden && event.key === 'ArrowRight') act(true);
  });
  load();
})();
