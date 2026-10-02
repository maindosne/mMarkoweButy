import { createClient } from 'npm:@supabase/supabase-js@2.102.0';

const ALLOWED_ORIGINS = new Set(['https://mmarkowebuty.pl','https://www.mmarkowebuty.pl']);
const enc = new TextEncoder();

function headers(origin: string | null) {
  const h = new Headers({
    'content-type':'application/json; charset=utf-8',
    'cache-control':'no-store',
    'x-content-type-options':'nosniff'
  });
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    h.set('access-control-allow-origin', origin);
    h.set('vary','Origin');
    h.set('access-control-allow-methods','GET,POST,OPTIONS');
    h.set('access-control-allow-headers','content-type');
  }
  return h;
}
function json(origin: string | null, data: unknown, status=200) {
  return new Response(JSON.stringify(data), {status, headers:headers(origin)});
}
function secretKey() {
  const modern=Deno.env.get('SUPABASE_SECRET_KEYS');
  if (modern) { try { const k=JSON.parse(modern)?.default; if(k)return String(k); } catch {} }
  return Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') || '';
}
function adminClient() {
  const url=Deno.env.get('SUPABASE_URL')||'', key=secretKey();
  if(!url||!key) throw new Error('SUPABASE_CONFIG_MISSING');
  return createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false}});
}
function clientIp(req: Request) {
  return (req.headers.get('x-forwarded-for')?.split(',')[0].trim() || req.headers.get('cf-connecting-ip') || 'unknown').slice(0,128);
}
async function sha256(value: string) {
  const d=new Uint8Array(await crypto.subtle.digest('SHA-256',enc.encode(value)));
  return [...d].map(b=>b.toString(16).padStart(2,'0')).join('');
}
function validClientId(value: unknown) {
  const s=String(value||'').trim();
  return /^[a-zA-Z0-9_-]{20,100}$/.test(s) ? s : '';
}
function validUuid(value: unknown) {
  const s=String(value||'').trim();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(s) ? s : '';
}
async function rateAllowed(sb:any, req:Request, clientHash:string) {
  const ipKey=await sha256(clientIp(req)+'|'+(req.headers.get('user-agent')||'').slice(0,160));
  const checks=[
    ['ai-support-chat-ip',ipKey,30,600],
    ['ai-support-chat-client',clientHash,40,600]
  ];
  for(const [bucket,key,limit,window] of checks) {
    const {data,error}=await sb.rpc('hit_rate_limit',{p_bucket:bucket,p_key_hash:key,p_limit:limit,p_window_seconds:window});
    if(error)throw error;
    if(data!==true)return false;
  }
  return true;
}
function extractText(data:any) {
  if(typeof data?.output_text==='string'&&data.output_text.trim())return data.output_text.trim();
  const parts:string[]=[];
  for(const item of Array.isArray(data?.output)?data.output:[]) {
    for(const c of Array.isArray(item?.content)?item.content:[]) {
      if(c?.type==='output_text'&&typeof c.text==='string')parts.push(c.text);
    }
  }
  return parts.join('\n').trim();
}
function normalize(s:string){return s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'');}
function fallbackReply(message:string, facts:any, catalog:any[]) {
  const q=normalize(message);
  if(/dostaw|paczkomat|inpost|wysyl/.test(q)) {
    const price=Number(facts.shippingPrice||0).toFixed(2).replace('.',',');
    const free=Number(facts.freeShippingFrom||0).toFixed(2).replace('.',',');
    return `Dostawa jest realizowana przez ${facts.shippingMethod || 'InPost Paczkomat'}. Koszt to ${price} zł, a od ${free} zł dostawa jest darmowa. Jeśli chcesz, napisz też o konkretnym problemie z dostawą.`;
  }
  if(/zwrot|reklamac|wada|uszkodz|oddac/.test(q)) {
    return `Pomogę Ci to uporządkować. W sprawie zwrotu lub reklamacji napisz do sklepu na ${facts.contactEmail}${facts.contactPhone ? ' lub zadzwoń: '+facts.contactPhone : ''}. Jeśli sprawa dotyczy zamówienia, w wiadomości do obsługi podaj jego numer; nie wpisuj tutaj danych karty ani hasła.`;
  }
  if(/kontakt|telefon|mail|email|napisac|zadzwon/.test(q)) {
    return `Kontakt ze sklepem: ${facts.contactEmail}${facts.contactPhone ? ' • '+facts.contactPhone : ''}. Opisz krótko sprawę, a przy zamówieniu dodaj jego numer.`;
  }
  if(/rozmiar|wkład|wklad|stop/.test(q)) {
    return 'Mogę pomóc z rozmiarem. Napisz, jaki zwykle nosisz rozmiar i — jeśli możesz — długość stopy lub wkładki w centymetrach. Pełny asystent AI jest jeszcze konfigurowany, więc nie będę zgadywał dopasowania.';
  }
  const words=q.split(/[^a-z0-9]+/).filter(x=>x.length>=3);
  const hits=catalog.filter((p:any)=>{
    const hay=normalize(`${p.brand} ${p.name} ${p.description||''} ${(p.sizes||[]).join(' ')}`);
    return words.some(w=>hay.includes(w));
  }).slice(0,3);
  if(hits.length) {
    return 'Z aktualnie dostępnych ofert pasują m.in.: '+hits.map((p:any)=>`${p.brand} ${p.name}, rozmiar ${(p.sizes||[]).join('/')}, ${Number(p.price).toFixed(2).replace('.',',')} zł`).join(' • ')+'.';
  }
  return `Asystent AI jest jeszcze konfigurowany. Mogę już pomóc z dostawą, kontaktem, zwrotem/reklamacją i podstawowymi pytaniami o dostępne produkty. W bardziej złożonej sprawie napisz na ${facts.contactEmail}.`;
}
async function getConversation(sb:any, conversationId:string, clientHash:string) {
  if(!conversationId)return null;
  const {data,error}=await sb.from('chat_conversations').select('id,status').eq('id',conversationId).eq('client_key_hash',clientHash).maybeSingle();
  if(error)throw error;
  return data||null;
}
async function ensureConversation(sb:any, conversationId:string, clientHash:string, context:any) {
  const existing=await getConversation(sb,conversationId,clientHash);
  if(existing)return existing.id;
  const metadata={
    page:String(context?.page||'').slice(0,500)||null,
    title:String(context?.title||'').slice(0,300)||null
  };
  const {data,error}=await sb.from('chat_conversations').insert({client_key_hash:clientHash,metadata}).select('id').single();
  if(error)throw error;
  return data.id;
}
async function storeMessage(sb:any, conversationId:string, role:string, content:string) {
  const {data,error}=await sb.from('chat_messages').insert({conversation_id:conversationId,role,content:content.slice(0,5000),source:'web'}).select('id,created_at').single();
  if(error)throw error;
  await sb.from('chat_conversations').update({
    last_message:content.slice(0,1000),
    last_message_role:role,
    last_message_at:new Date().toISOString(),
    updated_at:new Date().toISOString()
  }).eq('id',conversationId);
  return data;
}
async function recentMessages(sb:any, conversationId:string) {
  const {data,error}=await sb.from('chat_messages').select('role,content,created_at').eq('conversation_id',conversationId).order('created_at',{ascending:false}).limit(10);
  if(error)throw error;
  return (data||[]).reverse();
}

Deno.serve(async(req:Request)=>{
  const origin=req.headers.get('origin');
  if(req.method==='OPTIONS') {
    if(!origin||!ALLOWED_ORIGINS.has(origin))return new Response(null,{status:403});
    return new Response(null,{status:204,headers:headers(origin)});
  }
  if(origin&&!ALLOWED_ORIGINS.has(origin))return json(origin,{error:'Niedozwolone źródło żądania.'},403);

  try {
    const sb=adminClient();
    const apiKey=Deno.env.get('OPENAI_API_KEY')||'';
    const model=Deno.env.get('OPENAI_MODEL')||'gpt-6-luna';

    if(req.method==='GET') {
      const u=new URL(req.url);
      const action=u.searchParams.get('action');
      if(action==='history') {
        const clientId=validClientId(u.searchParams.get('clientId'));
        const conversationId=validUuid(u.searchParams.get('conversationId'));
        if(!clientId||!conversationId)return json(origin,{messages:[]});
        const clientHash=await sha256(clientId);
        const conv=await getConversation(sb,conversationId,clientHash);
        if(!conv)return json(origin,{messages:[]});
        const {data,error}=await sb.from('chat_messages').select('id,role,content,created_at').eq('conversation_id',conversationId).order('created_at',{ascending:true}).limit(100);
        if(error)throw error;
        return json(origin,{conversationId,messages:(data||[]).map((m:any)=>({id:m.id,role:m.role,content:m.content,createdAt:m.created_at}))});
      }
      return json(origin,{ok:true,service:'mmarkowebuty-ai-chat',aiConfigured:Boolean(apiKey),mode:apiKey?'ai':'fallback',model});
    }

    if(req.method!=='POST')return json(origin,{error:'Niedozwolona metoda.'},405);
    if(!req.headers.get('content-type')?.includes('application/json'))return json(origin,{error:'Nieprawidłowy format żądania.'},415);

    const raw=await req.text();
    if(raw.length>20000)return json(origin,{error:'Wiadomość jest zbyt długa.'},413);
    let body:any={};
    try{body=JSON.parse(raw)}catch{return json(origin,{error:'Nieprawidłowe dane.'},400)}

    const message=String(body?.message||'').trim();
    const clientId=validClientId(body?.clientId);
    const requestedConversationId=validUuid(body?.conversationId);
    if(!message)return json(origin,{error:'Napisz wiadomość.'},400);
    if(message.length>1200)return json(origin,{error:'Wiadomość może mieć maksymalnie 1200 znaków.'},400);
    if(!clientId)return json(origin,{error:'Nie udało się utworzyć bezpiecznej sesji czatu. Odśwież stronę.'},400);

    const clientHash=await sha256(clientId);
    if(!(await rateAllowed(sb,req,clientHash)))return json(origin,{error:'Dużo wiadomości naraz — spróbuj ponownie za kilka minut.'},429);

    const conversationId=await ensureConversation(sb,requestedConversationId,clientHash,body?.context);
    const userRecord=await storeMessage(sb,conversationId,'customer',message);

    const [{data:settings},{data:products,error:productError}]=await Promise.all([
      sb.from('store_settings').select('store_name,customer_email,customer_phone,shipping_method_name,shipping_price_cents,free_shipping_threshold_cents,returns_address').eq('id',1).maybeSingle(),
      sb.from('products').select('id,brand,name,description,sizes,price_cents,old_price_cents').eq('published',true).eq('sold',false).or(`reserved_until.is.null,reserved_until.lt.${new Date().toISOString()}`).order('created_at',{ascending:false}).limit(60)
    ]);
    if(productError)throw productError;

    const facts={
      storeName:settings?.store_name||'mMarkoweButy',
      contactEmail:settings?.customer_email||'kontakt@mmarkowebuty.pl',
      contactPhone:settings?.customer_phone||null,
      shippingMethod:settings?.shipping_method_name||'InPost Paczkomat',
      shippingPrice:Number(settings?.shipping_price_cents||0)/100,
      freeShippingFrom:Number(settings?.free_shipping_threshold_cents||9900)/100,
      returnsAddress:settings?.returns_address||null
    };
    const catalog=(products||[]).map((p:any)=>({
      id:p.id,brand:p.brand,name:p.name,sizes:p.sizes||[],
      price:Number(p.price_cents||0)/100,
      oldPrice:p.old_price_cents?Number(p.old_price_cents)/100:null,
      description:String(p.description||'').slice(0,240)
    }));

    let reply='';
    let mode='fallback';

    if(apiKey) {
      const history=await recentMessages(sb,conversationId);
      const instructions=`Jesteś asystentem obsługi klienta sklepu mMarkoweButy.
Pomagaj w pytaniach o sklep, dostępne buty, rozmiary, ceny, dostawę, płatność, zwroty i reklamacje.
Odpowiadaj po polsku, chyba że klient pisze w innym języku. Ton: miły, grzeczny, konkretny i pomocny; lekki humor jest OK, ale nie przy reklamacji, płatności lub zdenerwowanym kliencie.
Nie wymyślaj produktów, cen, rozmiarów, polityk, statusów zamówień ani terminów. Dostępność opieraj wyłącznie na katalogu poniżej.
Nie twierdź, że widzisz konkretne zamówienie, płatność, konto ani dane klienta. Przy problemie z konkretnym zamówieniem/płatnością/reklamacją skieruj do kontaktu ze sklepem i poproś o numer zamówienia w wiadomości do obsługi.
Nigdy nie proś o hasło ani dane karty. Nie obiecuj zwrotu pieniędzy, uznania reklamacji ani konkretnego terminu decyzji. Gdy czegoś nie wiesz, powiedz to wprost. Odpowiedzi zwykle 2-6 zdań.

DANE SKLEPU:
${JSON.stringify(facts)}

AKTUALNIE DOSTĘPNE PRODUKTY:
${JSON.stringify(catalog)}`;

      const input=history.map((m:any)=>({
        role:m.role==='customer'?'user':'assistant',
        content:String(m.content).slice(0,1600)
      }));

      const ai=await fetch('https://api.openai.com/v1/responses',{
        method:'POST',
        headers:{authorization:`Bearer ${apiKey}`,'content-type':'application/json'},
        body:JSON.stringify({model,instructions,input,max_output_tokens:550,store:false})
      });
      const data=await ai.json().catch(()=>({}));
      if(ai.ok) {
        reply=extractText(data);
        mode='ai';
      } else {
        console.error('AI provider error',ai.status,data?.error?.type||data?.error?.code||'unknown');
      }
    }

    if(!reply)reply=fallbackReply(message,facts,catalog);

    const assistantRecord=await storeMessage(sb,conversationId,'assistant',reply);
    return json(origin,{
      reply,
      mode,
      conversationId,
      userMessageId:userRecord?.id||null,
      assistantMessageId:assistantRecord?.id||null,
      createdAt:assistantRecord?.created_at||new Date().toISOString()
    });
  } catch(error) {
    console.error('mMarkoweButy AI chat error',error instanceof Error?error.message:'UNKNOWN');
    return json(origin,{error:'Czat jest chwilowo niedostępny. Spróbuj ponownie za moment.'},500);
  }
});