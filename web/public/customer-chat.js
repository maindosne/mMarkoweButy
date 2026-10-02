(() => {
'use strict';
const API='/api/chat';
const CLIENT_KEY='mm_chat_client_v1';
const CONV_KEY='mm_chat_conversation_v1';
const NUDGE_KEY='mm_chat_nudge_seen_v1';
const $=s=>document.querySelector(s);
const state={conversationId:localStorage.getItem(CONV_KEY)||'',clientId:localStorage.getItem(CLIENT_KEY)||'',open:false,busy:false,seen:new Set(),poll:null,welcomed:false,historyLoaded:false};

function uuid(){return crypto.randomUUID?crypto.randomUUID():'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=Math.random()*16|0,v=c==='x'?r:(r&3|8);return v.toString(16)})}
if(!state.clientId){state.clientId=uuid();localStorage.setItem(CLIENT_KEY,state.clientId)}

function el(tag,cls,text){const n=document.createElement(tag);if(cls)n.className=cls;if(text!=null)n.textContent=text;return n}
function timeLabel(v){try{return new Date(v||Date.now()).toLocaleTimeString('pl-PL',{hour:'2-digit',minute:'2-digit'})}catch{return''}}
function scrollEnd(){const b=$('#mmChatBody');if(b)b.scrollTop=b.scrollHeight}
function context(){
 const detail=$('#detailModal.open #detailBody');
 return {page:location.pathname+location.search,title:document.title,visibleProduct:detail?String(detail.innerText||'').slice(0,1400):null};
}
function addMessage(role,content,opts={}){
 if(!content)return;
 if(opts.id&&state.seen.has(String(opts.id)))return;
 if(opts.id)state.seen.add(String(opts.id));
 const body=$('#mmChatBody'),row=el('div','mm-chat-row '+(role==='user'?'user':'bot'));
 if(role!=='user')row.append(el('div','mm-chat-mini-avatar','mM'));
 const wrap=el('div');
 wrap.append(el('div','mm-chat-bubble',content));
 wrap.append(el('div','mm-chat-meta',timeLabel(opts.createdAt)));
 row.append(wrap);body.append(row);scrollEnd();
}
function setTyping(on){
 const old=$('#mmChatTyping');if(old)old.remove();
 if(!on)return;
 const row=el('div','mm-chat-row bot');row.id='mmChatTyping';row.append(el('div','mm-chat-mini-avatar','mM'));
 const bubble=el('div','mm-chat-bubble');const dots=el('div','mm-chat-typing');dots.innerHTML='<i></i><i></i><i></i>';bubble.append(dots);row.append(bubble);$('#mmChatBody').append(row);scrollEnd();
}
function showError(text){
 const old=$('#mmChatError');if(old)old.remove();
 const n=el('div','mm-chat-error',text);n.id='mmChatError';$('#mmChatFooter').prepend(n);setTimeout(()=>n.remove(),6000);
}
function quickActions(){
 const q=el('div','mm-chat-quick');q.id='mmChatQuick';
 ['Gdzie jest moje zamówienie?','Zwrot lub reklamacja','Pomoc z rozmiarem','Pytanie o produkt'].forEach(t=>{
   const b=el('button','',t);b.type='button';b.addEventListener('click',()=>{const i=$('#mmChatInput');i.value=t;resize(i);send()});q.append(b)
 });
 $('#mmChatBody').append(q);
}
function welcome(){
 if(state.welcomed||state.conversationId)return;
 state.welcomed=true;
 addMessage('assistant','Cześć! 👋 Jestem asystentem mMarkoweButy. Mogę pomóc z zamówieniem, produktem, rozmiarem, dostawą albo reklamacją. Co mogę dla Ciebie sprawdzić?');
 quickActions();
}
function resize(t){t.style.height='auto';t.style.height=Math.min(t.scrollHeight,110)+'px'}
async function request(path,init={}){
 const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),25000);
 try{
   const headers={...(init.headers||{})};
   if(init.body)headers['content-type']='application/json';
   const r=await fetch(API+path,{credentials:'same-origin',...init,signal:ctrl.signal,headers});
   const data=await r.json().catch(()=>({}));
   if(!r.ok)throw new Error(data.error||'Nie udało się połączyć z obsługą.');
   return data;
 }finally{clearTimeout(timer)}
}
async function loadHistory(){
 if(!state.conversationId)return;
 try{
   const q=new URLSearchParams({conversationId:state.conversationId,clientId:state.clientId});
   const data=await request('/history?'+q.toString(),{method:'GET'});
   (data.messages||[]).forEach(m=>addMessage(m.role==='customer'?'user':'assistant',m.content,{id:m.id,createdAt:m.createdAt}));
   state.historyLoaded=true;
 }catch{}
}
async function send(){
 if(state.busy)return;
 const input=$('#mmChatInput'),message=String(input.value||'').trim();
 if(!message)return;
 if(message.length>1200){showError('Wiadomość może mieć maksymalnie 1200 znaków.');return}
 state.busy=true;$('#mmChatSend').disabled=true;input.value='';resize(input);
 const q=$('#mmChatQuick');if(q)q.remove();
 addMessage('user',message);setTyping(true);
 try{
   const data=await request('/message',{method:'POST',body:JSON.stringify({clientId:state.clientId,conversationId:state.conversationId||null,message,context:context()})});
   if(data.conversationId&&!state.conversationId){state.conversationId=data.conversationId;localStorage.setItem(CONV_KEY,state.conversationId)}
   setTyping(false);
   if(data.userMessageId)state.seen.add(String(data.userMessageId));
   addMessage('assistant',data.reply||'Jestem tutaj. Napisz proszę jeszcze raz, z czym mogę pomóc.',{id:data.assistantMessageId,createdAt:data.createdAt});
 }catch(e){
   setTyping(false);showError(e?.name==='AbortError'?'Odpowiedź trwa zbyt długo. Spróbuj ponownie za chwilę.':(e?.message||'Chwilowy problem z czatem. Spróbuj ponownie.'));
 }finally{state.busy=false;$('#mmChatSend').disabled=false;input.focus()}
}
async function openChat(){
 state.open=true;$('#mmChatPanel').classList.add('open');$('#mmChatPanel').setAttribute('aria-hidden','false');$('#mmChatLauncher').setAttribute('aria-expanded','true');$('#mmChatNudge').classList.remove('show');
 if(!state.conversationId)welcome();else if(!state.historyLoaded)await loadHistory();
 setTimeout(()=>$('#mmChatInput')?.focus(),160);
 if(!state.poll)state.poll=setInterval(()=>{if(state.open&&state.conversationId)loadHistory()},12000);
}
function closeChat(){state.open=false;$('#mmChatPanel').classList.remove('open');$('#mmChatPanel').setAttribute('aria-hidden','true');$('#mmChatLauncher').setAttribute('aria-expanded','false')}
function mount(){
 const launcher=el('button','mm-chat-launcher');launcher.id='mmChatLauncher';launcher.type='button';launcher.setAttribute('aria-label','Otwórz czat z obsługą');launcher.setAttribute('aria-controls','mmChatPanel');launcher.setAttribute('aria-expanded','false');launcher.innerHTML='<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 11.4A7.4 7.4 0 0 1 12.4 19H7l-3 2v-5.1A7.4 7.4 0 1 1 20 11.4Z" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/><path d="M8.5 11.5h7M8.5 8.5h4.5" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/></svg>';
 const nudge=el('div','mm-chat-nudge','Potrzebujesz pomocy?');nudge.id='mmChatNudge';
 const panel=el('section','mm-chat-panel');panel.id='mmChatPanel';panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','false');panel.setAttribute('aria-label','Czat z obsługą mMarkoweButy');panel.setAttribute('aria-hidden','true');
 panel.innerHTML='<header class="mm-chat-header"><div class="mm-chat-header-row"><div class="mm-chat-avatar">mM</div><div class="mm-chat-title"><strong>Pomoc mMarkoweButy</strong><div class="mm-chat-status"><i></i><span>Asystent AI • dostępny teraz</span></div></div><button id="mmChatClose" class="mm-chat-close" type="button" aria-label="Zamknij czat">×</button></div></header><div id="mmChatBody" class="mm-chat-body" aria-live="polite"><div class="mm-chat-day">Pomoc online</div></div><footer id="mmChatFooter" class="mm-chat-footer"><div class="mm-chat-composer"><textarea id="mmChatInput" class="mm-chat-input" rows="1" maxlength="1200" placeholder="Napisz wiadomość…" aria-label="Wiadomość do obsługi"></textarea><button id="mmChatSend" class="mm-chat-send" type="button" aria-label="Wyślij wiadomość"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m4 4 16 8-16 8 3-8-3-8Z" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/><path d="M7 12h13" stroke="currentColor" stroke-width="1.9"/></svg></button></div><div class="mm-chat-disclosure">Rozmawiasz z asystentem AI. Jeśli będzie trzeba, sprawa może zostać przekazana człowiekowi.</div></footer>';
 document.body.append(launcher,nudge,panel);
 launcher.addEventListener('click',()=>state.open?closeChat():openChat());$('#mmChatClose').addEventListener('click',closeChat);$('#mmChatSend').addEventListener('click',send);
 const input=$('#mmChatInput');input.addEventListener('input',()=>resize(input));input.addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send()}});
 document.addEventListener('keydown',e=>{if(e.key==='Escape'&&state.open)closeChat()});
 if(!localStorage.getItem(NUDGE_KEY)){setTimeout(()=>{if(!state.open){nudge.classList.add('show');localStorage.setItem(NUDGE_KEY,'1');setTimeout(()=>nudge.classList.remove('show'),5500)}},7000)}
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount);else mount();
})();