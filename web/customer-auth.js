(() => {
  'use strict';
  const ENDPOINT = '/api/customer-auth';
  let state = { user: null, profile: null }, mode = 'login', busy = false, refreshTimer, pendingCheck;
  let dialog, content, button, previousFocus;
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const channel = typeof BroadcastChannel === 'function' ? new BroadcastChannel('mm-customer-account') : null;
  const locked = task => navigator.locks ? navigator.locks.request('mm-customer-session', task) : task();

  async function request(action, fields = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(ENDPOINT, {
        method: action === 'session' ? 'GET' : 'POST', credentials: 'same-origin', cache: 'no-store',
        headers: action === 'session' ? { Accept: 'application/json' } : { 'Content-Type': 'application/json', Accept: 'application/json' },
        ...(action === 'session' ? {} : { body: JSON.stringify({ action, ...fields }) }), signal: controller.signal
      });
      const data = await response.json().catch(() => null);
      if (!response.ok || !data) throw new Error(data?.error || 'Nie udało się połączyć z kontem klienta. Spróbuj ponownie.');
      return data;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('Serwer nie odpowiedział. Sprawdź połączenie i spróbuj ponownie.');
      if (error instanceof TypeError) throw new Error('Brak połączenia ze sklepem. Sprawdź internet i spróbuj ponownie.');
      throw error;
    } finally { clearTimeout(timeout); }
  }
  function setState(data) {
    state = data;
    button.textContent = state.user ? 'Moje konto' : 'Zaloguj / Załóż konto';
    clearTimeout(refreshTimer);
    if (data.user && data.expiresAt) refreshTimer = setTimeout(() => checkSession().catch(() => {}), Math.max(30000, (data.expiresAt * 1000 - Date.now() - 180000)));
  }
  function checkSession() {
    if (pendingCheck) return pendingCheck;
    pendingCheck = locked(async () => {
      const previous = state.user?.id;
      setState(await request('session'));
      if (dialog.open && previous !== state.user?.id) render(state.user ? 'account' : 'login');
      return state;
    }).finally(() => { pendingCheck = null; });
    return pendingCheck;
  }
  function message(text, success = false) {
    const element = content.querySelector('.mm-account-message');
    if (element) { element.textContent = text; element.className = 'mm-account-message ' + (success ? 'success' : 'error'); }
  }
  function setBusy(value) {
    busy = value;
    content.setAttribute('aria-busy', String(value));
    content.querySelectorAll('button,input').forEach(el => { el.disabled = value; });
    const submit = content.querySelector('[type="submit"]');
    if (submit) submit.textContent = value ? 'Chwileczkę…' : (mode === 'register' ? 'Utwórz konto' : 'Zaloguj się');
  }
  function close() { if (!busy) dialog.close(); }
  function field(id, label, type, autocomplete, extra = '') {
    return `<div class="mm-account-field"><label for="mm-${id}">${label}</label><input id="mm-${id}" name="${id}" type="${type}" autocomplete="${autocomplete}" required ${extra}></div>`;
  }
  function render(nextMode) {
    mode = state.user ? 'account' : nextMode;
    const register = mode === 'register';
    content.innerHTML = state.user ? `
      <div class="mm-account-kicker">TWOJE KONTO</div><h2 id="mm-account-title">Miło Cię widzieć!</h2>
      <p class="mm-account-intro">Jesteś zalogowany w mMarkoweButy.</p>
      <div class="mm-account-summary"><span class="mm-account-avatar" aria-hidden="true">${esc((state.profile?.display_name || 'K').slice(0, 1).toUpperCase())}</span><div><strong>${esc(state.profile?.display_name || 'Klient')}</strong><span>${esc(state.user.email)}</span></div></div>
      <button type="button" class="mm-account-primary" data-continue>Wróć do zakupów <span aria-hidden="true">→</span></button>
      <button type="button" class="mm-account-secondary" data-logout>Wyloguj się</button>
      <div class="mm-account-message" role="status" aria-live="polite"></div>
    ` : `
      <div class="mm-account-kicker">MIEJSCE DLA CIEBIE</div>
      <h2 id="mm-account-title">${register ? 'Załóż swoje konto' : 'Witaj ponownie'}</h2>
      <p class="mm-account-intro">${register ? 'Kilka danych i możesz poczuć się u nas jak u siebie.' : 'Zaloguj się i wróć po swoją następną parę.'}</p>
      <div class="mm-account-tabs" role="tablist" aria-label="Dostęp do konta">
        <button type="button" role="tab" id="mm-login-tab" aria-controls="mm-account-panel" aria-selected="${!register}" tabindex="${register ? -1 : 0}" data-mode="login">Zaloguj się</button>
        <button type="button" role="tab" id="mm-register-tab" aria-controls="mm-account-panel" aria-selected="${register}" tabindex="${register ? 0 : -1}" data-mode="register">Załóż konto</button>
      </div>
      <form id="mm-account-panel" role="tabpanel" aria-labelledby="mm-${register ? 'register' : 'login'}-tab" novalidate>
        ${register ? field('name', 'Imię', 'text', 'given-name', 'maxlength="100"') : ''}
        ${field('email', 'Adres e-mail', 'email', 'username', 'maxlength="254" inputmode="email" autocapitalize="none" spellcheck="false"')}
        <div class="mm-account-field"><label for="mm-password">Hasło</label><div class="mm-account-password"><input id="mm-password" name="password" type="password" autocomplete="${register ? 'new-password' : 'current-password'}" required maxlength="128" ${register ? 'minlength="8" aria-describedby="mm-password-hint"' : ''}><button type="button" data-password aria-label="Pokaż hasło" aria-pressed="false">Pokaż</button></div>${register ? '<small id="mm-password-hint">Co najmniej 8 znaków. Użyj liter i cyfr.</small>' : ''}</div>
        <div class="mm-account-message" role="status" aria-live="polite"></div>
        <button type="submit" class="mm-account-primary">${register ? 'Utwórz konto' : 'Zaloguj się'}</button>
      </form>
      <p class="mm-account-footnote">${register ? 'Dane wykorzystujemy do obsługi Twojego konta. ' : 'Zakupy możesz robić również bez konta. '}<button type="button" data-privacy>Polityka prywatności</button></p>
    `;
    content.querySelectorAll('[data-mode]').forEach(tabButton => {
      tabButton.addEventListener('click', () => { if (!busy) { render(tabButton.dataset.mode); content.querySelector('[aria-selected="true"]').focus(); } });
      tabButton.addEventListener('keydown', event => {
        if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
          event.preventDefault(); render(mode === 'register' ? 'login' : 'register'); content.querySelector('[aria-selected="true"]').focus();
        }
      });
    });
    content.querySelector('[data-password]')?.addEventListener('click', event => {
      const input = content.querySelector('#mm-password'); const show = input.type === 'password';
      input.type = show ? 'text' : 'password'; event.currentTarget.textContent = show ? 'Ukryj' : 'Pokaż';
      event.currentTarget.setAttribute('aria-label', show ? 'Ukryj hasło' : 'Pokaż hasło'); event.currentTarget.setAttribute('aria-pressed', String(show));
    });
    content.querySelector('form')?.addEventListener('submit', submit);
    content.querySelector('[data-continue]')?.addEventListener('click', close);
    content.querySelector('[data-logout]')?.addEventListener('click', async () => {
      if (busy) return;
      setBusy(true);
      try { setState(await locked(() => request('logout'))); channel?.postMessage('changed'); render('login'); message('Wylogowano bezpiecznie.', true); }
      catch (error) { message(error.message); } finally { setBusy(false); }
    });
    content.querySelector('[data-privacy]')?.addEventListener('click', () => {
      close(); [...document.querySelectorAll('footer button')].find(el => el.textContent.trim() === 'Polityka prywatności')?.click();
    });
  }
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    const form = event.currentTarget;
    const emailInput = form.elements.email, passwordInput = form.elements.password;
    const email = emailInput.value.trim(), password = passwordInput.value;
    const name = form.elements.name?.value.trim() || '';
    if (mode === 'register' && !name) { message('Podaj swoje imię.'); form.elements.name.focus(); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { message('Podaj poprawny adres e-mail.'); emailInput.focus(); return; }
    if (!password || (mode === 'register' && password.length < 8)) { message(mode === 'register' ? 'Hasło musi mieć co najmniej 8 znaków.' : 'Wpisz hasło.'); passwordInput.focus(); return; }
    const action = mode === 'register' ? 'signup' : 'login';
    message(''); setBusy(true);
    try {
      const data = await locked(() => request(action, { email, password, display_name: name }));
      passwordInput.value = '';
      setState(data); channel?.postMessage('changed');
      if (data.user) { render('account'); message(data.message, true); }
      else { render('login'); content.querySelector('#mm-email').value = email; message(data.message, true); }
    } catch (error) { message(error.message); }
    finally { setBusy(false); }
  }
  function init() {
    if (document.getElementById('mm-account-dialog')) return;
    try { localStorage.removeItem('mm_customer_session_v2'); } catch {}
    const style = document.createElement('style');
    style.textContent = `
      #mm-account-btn{font:inherit;font-weight:700;font-size:13px;border:1px solid #dfd8d0;background:#fff;color:#191919;border-radius:30px;padding:10px 16px;cursor:pointer}
      #mm-account-dialog{border:0;padding:0;width:min(860px,calc(100% - 32px));max-width:860px;max-height:calc(100dvh - 32px);border-radius:24px;background:#fff;color:#202020;box-shadow:0 32px 100px #0004;overflow:auto;font:16px/1.5 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
      #mm-account-dialog::backdrop{background:rgb(20 16 12 / 65%);backdrop-filter:blur(5px)}
      #mm-account-dialog *{box-sizing:border-box}#mm-account-dialog button,#mm-account-dialog input{font:inherit}
      #mm-account-dialog button{cursor:pointer}#mm-account-dialog button:disabled{cursor:wait;opacity:.6}
      #mm-account-dialog button:focus-visible,#mm-account-dialog input:focus-visible{outline:3px solid #c66718;outline-offset:3px}
      .mm-account-layout{display:grid;grid-template-columns:.85fr 1.15fr;min-height:520px}
      .mm-account-brand{padding:46px 32px;background:#f5efe7;display:flex;flex-direction:column;justify-content:space-between;gap:32px;border-radius:24px 0 0 24px}
      .mm-account-logo{font-size:22px;font-weight:850;letter-spacing:-.9px}.mm-account-logo span{color:#b65411}
      .mm-account-brand h3{font-size:36px;line-height:1.12;letter-spacing:-1.6px;margin:20px 0}.mm-account-brand p{color:#6c6055;font-size:14px;margin:0}
      .mm-account-art{height:110px;position:relative;overflow:hidden;margin-top:12px}.mm-account-art:before,.mm-account-art:after{content:'';position:absolute;width:150px;height:60px;border-radius:70% 35% 25% 25%;border-bottom:9px solid #fff;transform:rotate(-17deg);background:#242323;top:12px;left:10px;box-shadow:0 14px 24px #74573515}.mm-account-art:after{background:#c76922;left:98px;top:37px;transform:rotate(12deg)}
      .mm-account-perks{border-top:1px solid #ded3c6;padding-top:22px;font-size:12px;color:#6c6055;display:grid;gap:8px}
      .mm-account-content{padding:44px 36px 32px;min-width:0}.mm-account-kicker{font-size:10px;letter-spacing:1.9px;font-weight:750;color:#9b5a2b;margin-bottom:8px}
      #mm-account-dialog h2{font-size:27px;letter-spacing:-.8px;line-height:1.2;margin:0 0 10px}.mm-account-intro{font-size:13px;color:#726b65;margin:0 0 24px}
      .mm-account-close{position:absolute;right:12px;top:10px;border:0;background:transparent;color:#746d66;font-size:26px!important;line-height:1;padding:8px;border-radius:50%;z-index:1}
      .mm-account-tabs{display:grid;grid-template-columns:1fr 1fr;background:#f4f2ef;padding:4px;border-radius:12px;margin-bottom:22px;gap:4px}.mm-account-tabs button{border:0;border-radius:9px;font-size:13px!important;font-weight:650!important;background:transparent;padding:10px 6px;color:#77706a}.mm-account-tabs [aria-selected=true]{background:white;color:#202020;box-shadow:0 2px 6px #0000000c}
      .mm-account-field{display:grid;gap:7px;margin:15px 0}.mm-account-field label{font-size:12px;font-weight:650}.mm-account-field input{border:1px solid #d9d3cd;background:#fff;border-radius:10px;padding:12px;width:100%;min-width:0;color:#202020;font-size:16px!important}.mm-account-field small{font-size:11px;color:#726b65}.mm-account-password{position:relative}.mm-account-password input{padding-right:70px}.mm-account-password button{position:absolute;right:7px;top:7px;padding:7px;border:0;background:white;border-radius:6px;font-size:11px!important;color:#665b53}
      .mm-account-primary,.mm-account-secondary{width:100%;border-radius:11px;padding:13px 16px;font-size:14px!important;font-weight:750!important}.mm-account-primary{border:1px solid #d96c16;background:#ed7b22;color:#17120e;margin-top:7px;box-shadow:0 3px 0 #be57100f}.mm-account-primary:hover{background:#df6f1c}.mm-account-secondary{border:1px solid #ddd6ce;background:white;color:#5f554d;margin-top:12px}
      .mm-account-footnote{font-size:11px;color:#77706a;text-align:center;margin:20px 0 0;line-height:1.7}.mm-account-footnote button{font-size:inherit!important;border:0;background:none;text-decoration:underline;color:#645448;padding:0}
      .mm-account-message{font-size:12px;line-height:1.5;margin:12px 0;overflow-wrap:anywhere}.mm-account-message:empty{display:none}.mm-account-message.error{color:#a22720;background:#fff2ef;border-radius:9px;padding:10px 12px}.mm-account-message.success{color:#285b38;background:#eef6ef;border-radius:9px;padding:10px 12px}
      .mm-account-summary{display:flex;align-items:center;gap:14px;background:#f8f5f1;padding:20px 16px;border-radius:14px;margin:30px 0}.mm-account-summary strong,.mm-account-summary div>span{display:block;overflow-wrap:anywhere}.mm-account-summary strong{font-size:16px}.mm-account-summary div>span{font-size:12px;color:#756a60;margin-top:3px}.mm-account-avatar{display:grid;place-items:center;flex-shrink:0;width:46px;height:46px;background:#f0dcc8;border-radius:50%;color:#8d4b1a;font-weight:800}
      @media(max-width:660px){#mm-account-dialog{width:calc(100% - 20px);max-height:calc(100dvh - 20px);border-radius:20px}.mm-account-layout{grid-template-columns:1fr;min-height:0}.mm-account-brand{padding:20px 24px;border-radius:20px 20px 0 0;gap:0}.mm-account-brand h3,.mm-account-brand p,.mm-account-art,.mm-account-perks{display:none}.mm-account-logo{font-size:20px}.mm-account-content{padding:26px 24px}.mm-account-close{top:13px}#mm-account-dialog h2{font-size:25px}.mm-account-intro{margin-bottom:20px}#mm-account-btn{font-size:11px;padding:9px 10px}}
      @media(prefers-reduced-motion:reduce){#mm-account-dialog *{scroll-behavior:auto}}
    `;
    document.head.appendChild(style);
    button = document.createElement('button'); button.id = 'mm-account-btn'; button.type = 'button'; button.textContent = 'Zaloguj / Załóż konto'; button.setAttribute('aria-haspopup', 'dialog');
    (document.querySelector('.navlinks') || document.querySelector('header')).appendChild(button);
    dialog = document.createElement('dialog'); dialog.id = 'mm-account-dialog'; dialog.setAttribute('aria-labelledby', 'mm-account-title');
    dialog.innerHTML = `<button type="button" class="mm-account-close" aria-label="Zamknij okno konta">×</button><div class="mm-account-layout"><aside class="mm-account-brand" aria-label="mMarkoweButy"><div class="mm-account-logo">mMarkowe<span>Buty</span></div><div><div class="mm-account-art" aria-hidden="true"></div><h3>Dobre pary.<br>Lepsze ceny.</h3><p>Twoje miejsce na kolejną<br>ulubioną parę.</p></div><div class="mm-account-perks"><span>✓ Pojedyncze pary, konkretne rozmiary</span><span>✓ Darmowa dostawa od 99 zł</span></div></aside><section class="mm-account-content"></section></div>`;
    document.body.appendChild(dialog); content = dialog.querySelector('.mm-account-content');
    button.addEventListener('click', () => { previousFocus = document.activeElement; render(state.user ? 'account' : 'login'); dialog.showModal(); });
    dialog.querySelector('.mm-account-close').addEventListener('click', close);
    dialog.addEventListener('click', event => { if (event.target === dialog) { const rect = dialog.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) close(); } });
    dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
    dialog.addEventListener('close', () => { content.querySelectorAll('input[name="password"]').forEach(input => { input.value = ''; }); previousFocus?.focus(); });
    if (channel) channel.onmessage = () => checkSession().catch(() => {});
    document.addEventListener('visibilitychange', () => { if (!document.hidden) checkSession().catch(() => {}); });
    window.addEventListener('online', () => checkSession().catch(() => {}));
    const hash = new URLSearchParams(location.hash.slice(1));
    if (hash.has('access_token') || hash.has('error_description')) {
      const confirmed = hash.has('access_token') && hash.get('type') === 'signup';
      history.replaceState(null, '', location.pathname + location.search);
      button.click();
      message(confirmed ? 'Adres e-mail został potwierdzony. Możesz się zalogować.' : 'Link potwierdzający jest nieprawidłowy lub wygasł. Spróbuj ponownie zarejestrować konto.', confirmed);
    }
    checkSession().catch(() => {});
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true }); else init();
})();
