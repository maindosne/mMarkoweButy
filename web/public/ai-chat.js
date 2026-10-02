(() => {
  'use strict';

  if (window.__mmAiSupportChatLoaded) return;
  window.__mmAiSupportChatLoaded = true;

  const API_URL = '/api/ai-chat';
  const CLIENT_KEY = 'mm_ai_support_client_v1';
  const CONVERSATION_KEY = 'mm_ai_support_conversation_v1';
  const HISTORY_KEY = 'mm_ai_support_history_v1';
  const MAX_HISTORY = 10;

  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[char]));

  const makeClientId = () => {
    if (crypto.randomUUID) return crypto.randomUUID();
    return Array.from(crypto.getRandomValues(new Uint8Array(24)), (b) => b.toString(16).padStart(2, '0')).join('');
  };

  let clientId = localStorage.getItem(CLIENT_KEY) || '';
  if (!/^[a-zA-Z0-9_-]{20,100}$/.test(clientId)) {
    clientId = makeClientId();
    localStorage.setItem(CLIENT_KEY, clientId);
  }

  let conversationId = localStorage.getItem(CONVERSATION_KEY) || '';
  let remoteHistoryLoaded = false;
  let busy = false;
  let history = [];

  try {
    const stored = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
    history = Array.isArray(stored) ? stored.slice(-MAX_HISTORY) : [];
  } catch {
    history = [];
  }

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
        <span><i aria-hidden="true"></i> Pomoc online</span>
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
      <div class="mm-ai-chat-safety">Rozmawiasz z asystentem. Nie podawaj haseł ani danych karty.</div>
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

  function saveHistory() {
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(-MAX_HISTORY)));
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

  async function loadRemoteHistory() {
    if (!conversationId || remoteHistoryLoaded) return;
    remoteHistoryLoaded = true;

    try {
      const params = new URLSearchParams({
        action: 'history',
        clientId,
        conversationId
      });
      const response = await fetch(`${API_URL}?${params.toString()}`, { cache: 'no-store' });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !Array.isArray(data.messages) || !data.messages.length) return;

      history = data.messages
        .filter((message) => ['customer', 'assistant', 'human'].includes(message.role))
        .map((message) => ({
          role: message.role === 'customer' ? 'user' : 'assistant',
          content: String(message.content || '')
        }))
        .filter((message) => message.content)
        .slice(-MAX_HISTORY);

      saveHistory();
      renderHistory();
    } catch {}
  }

  async function setOpen(open) {
    panel.classList.toggle('open', open);
    launcher.setAttribute('aria-expanded', String(open));
    statusDot.hidden = open;
    if (open) {
      await loadRemoteHistory();
      setTimeout(() => input.focus(), 60);
    }
  }

  function resizeInput() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 110) + 'px';
  }

  function pageContext() {
    const detail = document.querySelector('#detailModal.open #detailBody');
    return {
      page: location.pathname + location.search,
      title: document.title,
      visibleProduct: detail ? String(detail.innerText || '').slice(0, 1200) : null
    };
  }

  async function ask(text) {
    text = String(text || '').trim();
    if (!text || busy) return;

    busy = true;
    sendButton.disabled = true;

    addMessage('user', text);
    history.push({ role: 'user', content: text });
    history = history.slice(-MAX_HISTORY);
    saveHistory();

    input.value = '';
    resizeInput();
    const typing = addTyping();

    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          clientId,
          conversationId: conversationId || null,
          message: text,
          context: pageContext()
        })
      });

      const data = await response.json().catch(() => ({}));
      typing.remove();

      if (!response.ok || !data.reply) {
        addMessage('assistant', data.error || 'Czat ma chwilowy problem. Spróbuj ponownie za moment.', true);
        return;
      }

      if (data.conversationId && data.conversationId !== conversationId) {
        conversationId = String(data.conversationId);
        localStorage.setItem(CONVERSATION_KEY, conversationId);
        remoteHistoryLoaded = true;
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