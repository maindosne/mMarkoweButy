(() => {
  'use strict';

  if (window.__mmAiSupportChatLoaded) return;
  window.__mmAiSupportChatLoaded = true;

  const API_URL = '/api/ai-chat';
  const HISTORY_KEY = 'mm_ai_support_history_v1';
  const MAX_HISTORY = 10;

  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]));

  const launcher = document.createElement('button');
  launcher.type = 'button';
  launcher.className = 'mm-ai-launcher';
  launcher.setAttribute('aria-label', 'Otwórz czat pomocy');
  launcher.setAttribute('aria-expanded', 'false');
  launcher.innerHTML = `
    <span class="mm-ai-launcher-dot" aria-hidden="true"></span>
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M7 18.5 3.5 21v-4.8A8.5 8.5 0 1 1 7 18.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
      <path d="M8 10h8M8 13.5h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
    </svg>
  `;

  const panel = document.createElement('section');
  panel.className = 'mm-ai-chat';
  panel.setAttribute('role', 'dialog');
  panel.setAttribute('aria-label', 'Pomoc mMarkoweButy');
  panel.innerHTML = `
    <header class="mm-ai-chat-head">
      <div class="mm-ai-chat-avatar" aria-hidden="true">m</div>
      <div class="mm-ai-chat-title">
        <strong>Pomoc mMarkoweButy</strong>
        <span><i aria-hidden="true"></i> Asystent online</span>
      </div>
      <button class="mm-ai-chat-close" type="button" aria-label="Zamknij czat">×</button>
    </header>

    <div class="mm-ai-chat-body">
      <p class="mm-ai-chat-intro">Zapytaj o produkty, rozmiary, dostawę albo problem ze sklepem.</p>

      <div class="mm-ai-chat-quick" aria-label="Szybkie pytania">
        <button type="button" data-chat-question="Pomóż mi dobrać rozmiar butów.">Dobór rozmiaru</button>
        <button type="button" data-chat-question="Jak wygląda dostawa i kiedy jest darmowa?">Dostawa</button>
        <button type="button" data-chat-question="Chcę zapytać o zwrot albo reklamację.">Zwrot / reklamacja</button>
        <button type="button" data-chat-question="Mam problem z zamówieniem lub płatnością.">Zamówienie</button>
      </div>

      <div class="mm-ai-chat-messages" aria-live="polite"></div>
    </div>

    <footer class="mm-ai-chat-foot">
      <form class="mm-ai-chat-form">
        <textarea rows="1" maxlength="1200" placeholder="Napisz, w czym pomóc…" aria-label="Wiadomość do asystenta"></textarea>
        <button class="mm-ai-chat-send" type="submit" aria-label="Wyślij wiadomość">➜</button>
      </form>
      <div class="mm-ai-chat-safety">AI może się pomylić. Nie podawaj haseł ani danych karty.</div>
    </footer>
  `;

  document.body.append(panel, launcher);

  const body = panel.querySelector('.mm-ai-chat-body');
  const list = panel.querySelector('.mm-ai-chat-messages');
  const form = panel.querySelector('.mm-ai-chat-form');
  const input = panel.querySelector('textarea');
  const sendButton = panel.querySelector('.mm-ai-chat-send');
  const closeButton = panel.querySelector('.mm-ai-chat-close');
  const statusDot = launcher.querySelector('.mm-ai-launcher-dot');

  let busy = false;
  let history = [];

  try {
    const stored = JSON.parse(sessionStorage.getItem(HISTORY_KEY) || '[]');
    history = Array.isArray(stored) ? stored.slice(-MAX_HISTORY) : [];
  } catch {
    history = [];
  }

  function saveHistory() {
    try {
      sessionStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(-MAX_HISTORY)));
    } catch {}
  }

  function addMessage(role, text, isError = false) {
    const row = document.createElement('div');
    row.className = `mm-ai-message-row ${role === 'user' ? 'user' : 'assistant'}${isError ? ' error' : ''}`;
    row.innerHTML = `<div class="mm-ai-message">${escapeHtml(text)}</div>`;
    list.appendChild(row);
    body.scrollTop = body.scrollHeight;
    return row;
  }

  function addTyping() {
    const row = document.createElement('div');
    row.className = 'mm-ai-message-row assistant';
    row.innerHTML = '<div class="mm-ai-message"><span class="mm-ai-typing" aria-label="Asystent pisze"><i></i><i></i><i></i></span></div>';
    list.appendChild(row);
    body.scrollTop = body.scrollHeight;
    return row;
  }

  function renderHistory() {
    list.innerHTML = '';
    if (!history.length) {
      addMessage('assistant', 'Cześć! Jestem asystentem mMarkoweButy. Napisz, czego potrzebujesz — spróbuję pomóc szybko i konkretnie 🙂');
      return;
    }
    history.forEach((message) => addMessage(message.role, message.content));
  }

  function setOpen(open) {
    panel.classList.toggle('open', open);
    launcher.setAttribute('aria-expanded', String(open));
    statusDot.hidden = open;
    if (open) setTimeout(() => input.focus(), 60);
  }

  function resizeInput() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 110) + 'px';
  }

  async function ask(text) {
    text = String(text || '').trim();
    if (!text || busy) return;

    busy = true;
    sendButton.disabled = true;

    const priorHistory = history.slice(-8);
    addMessage('user', text);
    history.push({ role: 'user', content: text });
    saveHistory();

    input.value = '';
    resizeInput();

    const typing = addTyping();

    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          message: text,
          history: priorHistory
        })
      });

      const data = await response.json().catch(() => ({}));
      typing.remove();

      if (!response.ok || !data.reply) {
        addMessage('assistant', data.error || 'Czat ma chwilowy problem. Spróbuj ponownie za moment.', true);
        return;
      }

      const reply = String(data.reply).trim();
      addMessage('assistant', reply);
      history.push({ role: 'assistant', content: reply });
      history = history.slice(-MAX_HISTORY);
      saveHistory();
    } catch {
      typing.remove();
      addMessage('assistant', 'Nie udało się połączyć z czatem. Sprawdź połączenie i spróbuj ponownie.', true);
    } finally {
      busy = false;
      sendButton.disabled = false;
      input.focus();
    }
  }

  launcher.addEventListener('click', () => setOpen(!panel.classList.contains('open')));
  closeButton.addEventListener('click', () => setOpen(false));

  panel.querySelectorAll('[data-chat-question]').forEach((button) => {
    button.addEventListener('click', () => ask(button.dataset.chatQuestion));
  });

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    ask(input.value);
  });

  input.addEventListener('input', resizeInput);
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && panel.classList.contains('open')) setOpen(false);
  });

  renderHistory();
})();