import { createClient } from 'npm:@supabase/supabase-js@2.102.0';

const ORIGINS = new Set(['https://www.mmarkowebuty.pl', 'https://mmarkowebuty.pl']);
const COOKIE = '__Host-mm_customer';
const SESSION_SECONDS = 30 * 86400;
const options = { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } };

export function customerSession(req: Request) {
  try {
    const value = (req.headers.get('cookie') || '').split(';').map(s => s.trim()).find(s => s.startsWith(COOKIE + '='))?.slice(COOKIE.length + 1);
    if (!value || value.length > 3800) return null;
    const s = JSON.parse(atob(value));
    return typeof s.access_token === 'string' && typeof s.refresh_token === 'string' ? s : null;
  } catch { return null; }
}

function cookie(session: any) {
  const value = session ? btoa(JSON.stringify({ access_token: session.access_token, refresh_token: session.refresh_token, expires_at: session.expires_at })) : '';
  return `${COOKIE}=${value}; Path=/; Max-Age=${session ? SESSION_SECONDS : 0}; HttpOnly; Secure; SameSite=Lax`;
}

function errorText(error: any) {
  const code = error?.code || '';
  const messages: Record<string, string> = {
    invalid_credentials: 'Nieprawidłowy adres e-mail lub hasło.',
    email_not_confirmed: 'Potwierdź adres e-mail, klikając link w wiadomości od sklepu.',
    user_already_exists: 'Konto z tym adresem już istnieje. Zaloguj się.',
    email_exists: 'Konto z tym adresem już istnieje. Zaloguj się.',
    weak_password: 'Użyj mocniejszego hasła: co najmniej 8 znaków, litery i cyfry.',
    email_address_invalid: 'Podaj poprawny adres e-mail.',
    signup_disabled: 'Rejestracja jest chwilowo niedostępna. Spróbuj później.',
    email_address_not_authorized: 'Nie udało się wysłać potwierdzenia rejestracji. Skontaktuj się ze sklepem.',
    over_email_send_rate_limit: 'Wysłano już wiadomość. Sprawdź pocztę lub spróbuj ponownie za kilka minut.',
    over_request_rate_limit: 'Zbyt wiele prób. Odczekaj kilka minut i spróbuj ponownie.',
    request_timeout: 'Serwer nie odpowiedział. Spróbuj ponownie.',
  };
  return messages[code] || (error?.status === 429 ? messages.over_request_rate_limit : 'Nie udało się wykonać operacji. Spróbuj ponownie za chwilę.');
}

export async function handleCustomerAuth(req: Request) {
  const origin = req.headers.get('origin');
  const headers = new Headers({ 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store', 'Vary': 'Origin, Cookie', 'X-Content-Type-Options': 'nosniff' });
  if (origin && ORIGINS.has(origin)) {
    headers.set('Access-Control-Allow-Origin', origin);
    headers.set('Access-Control-Allow-Credentials', 'true');
    headers.set('Access-Control-Allow-Headers', 'content-type');
    headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  }
  const out = (body: any, status = 200, session: any = undefined) => {
    if (session !== undefined) headers.set('Set-Cookie', cookie(session));
    return new Response(JSON.stringify(body), { status, headers });
  };
  if (origin && !ORIGINS.has(origin)) return out({ error: 'Niedozwolone źródło żądania.' }, 403);
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers });
  if (!['GET', 'POST'].includes(req.method)) return out({ error: 'Niedozwolona metoda.' }, 405);
  try {
    const url = Deno.env.get('SUPABASE_URL') || '';
    const key = Deno.env.get('SUPABASE_ANON_KEY') || JSON.parse(Deno.env.get('SUPABASE_PUBLISHABLE_KEYS') || '{}').default;
    const adminKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') || JSON.parse(Deno.env.get('SUPABASE_SECRET_KEYS') || '{}').default;
    if (!url || !key || !adminKey) return out({ error: 'Logowanie jest chwilowo niedostępne.' }, 503);
    const client = createClient(url, key, options);
    const admin = createClient(url, adminKey, options);
    let body: any = {};
    if (req.method === 'POST') {
      if (!req.headers.get('content-type')?.includes('application/json')) return out({ error: 'Nieprawidłowy format żądania.' }, 415);
      const raw = await req.text();
      if (raw.length > 4096) return out({ error: 'Zbyt długie dane formularza.' }, 413);
      try { body = JSON.parse(raw); } catch { return out({ error: 'Nieprawidłowe dane formularza.' }, 400); }
      if (!body || typeof body !== 'object' || Array.isArray(body)) return out({ error: 'Nieprawidłowe dane formularza.' }, 400);
    }
    const action = req.method === 'GET' ? 'session' : body.action;
    const stored = customerSession(req);
    const profileFor = async (user: any) => {
      const { data, error } = await admin.from('customer_profiles').select('user_id,email,display_name,phone').eq('user_id', user.id).single();
      if (error) throw new Error('PROFILE_UNAVAILABLE');
      return { user: { id: user.id, email: user.email }, profile: data };
    };
    if (action === 'session') {
      if (!stored) return out({ user: null, profile: null });
      if (Number(stored.expires_at) > Date.now() / 1000 + 120) {
        const { data, error } = await client.auth.getUser(stored.access_token);
        if (!error && data.user) return out({ ...await profileFor(data.user), expiresAt: stored.expires_at });
        if (error && (error.status || 500) >= 500) return out({ error: 'Nie można teraz sprawdzić sesji. Spróbuj ponownie.' }, 503);
      }
      const { data, error } = await client.auth.refreshSession({ refresh_token: stored.refresh_token });
      if (error || !data.session || !data.user) {
        if (error && (error.status || 500) >= 500) return out({ error: 'Nie można teraz odnowić sesji. Spróbuj ponownie.' }, 503);
        return out({ user: null, profile: null }, 200, null);
      }
      return out({ ...await profileFor(data.user), expiresAt: data.session.expires_at }, 200, data.session);
    }
    if (action === 'logout') {
      if (stored) {
        let token = stored.access_token;
        if (Number(stored.expires_at) <= Date.now() / 1000 + 10) {
          const { data, error } = await client.auth.refreshSession({ refresh_token: stored.refresh_token });
          if (error && (error.status || 500) >= 500) return out({ error: 'Nie udało się wylogować. Spróbuj ponownie.' }, 503);
          token = data.session?.access_token || token;
        }
        const { error } = await admin.auth.admin.signOut(token, 'local');
        if (error && ![401, 403, 404].includes(error.status || 0)) return out({ error: 'Nie udało się wylogować. Spróbuj ponownie.' }, 503);
      }
      return out({ user: null, profile: null, message: 'Wylogowano bezpiecznie.' }, 200, null);
    }
    if (!['signup', 'login'].includes(action)) return out({ error: 'Nieznana operacja.' }, 400);
    const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';
    const password = typeof body.password === 'string' ? body.password : '';
    const displayName = typeof body.display_name === 'string' ? body.display_name.trim() : '';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) return out({ error: 'Podaj poprawny adres e-mail.' }, 400);
    if (!password || password.length > 128 || (action === 'signup' && password.length < 8)) return out({ error: 'Hasło musi mieć od 8 do 128 znaków.' }, 400);
    if (action === 'signup' && (!displayName || displayName.length > 100)) return out({ error: 'Podaj imię (maksymalnie 100 znaków).' }, 400);
    const ip = req.headers.get('x-forwarded-for')?.split(',')[0].trim() || req.headers.get('cf-connecting-ip') || 'unknown';
    const hash = async (value: string) => [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)))].map(b => b.toString(16).padStart(2, '0')).join('');
    for (const [suffix, value, limit] of [['ip', ip, action === 'signup' ? 10 : 40], ['email', email, action === 'signup' ? 5 : 15]] as const) {
      const { data, error } = await admin.rpc('hit_rate_limit', { p_bucket: `customer_${action}_${suffix}`, p_key_hash: await hash(value), p_limit: limit, p_window_seconds: 900 });
      if (error) return out({ error: 'Logowanie jest chwilowo niedostępne.' }, 503);
      if (!data) return out({ error: 'Zbyt wiele prób. Odczekaj 15 minut i spróbuj ponownie.' }, 429);
    }
    const result = action === 'signup'
      ? await client.auth.signUp({ email, password, options: { emailRedirectTo: 'https://www.mmarkowebuty.pl/', data: { display_name: displayName } } })
      : await client.auth.signInWithPassword({ email, password });
    if (result.error) return out({ error: errorText(result.error) }, result.error.status === 429 ? 429 : 400);
    const { session, user } = result.data;
    if (!session) return out({ user: null, profile: null, needsConfirmation: true, message: 'Sprawdź swoją skrzynkę e-mail (również spam) i potwierdź adres. Jeśli masz już konto, zaloguj się.' });
    if (!user) return out({ error: 'Nie udało się utworzyć sesji.' }, 502);
    return out({ ...await profileFor(user), expiresAt: session.expires_at, message: action === 'signup' ? 'Konto zostało utworzone.' : 'Zalogowano pomyślnie.' }, 200, session);
  } catch (error) {
    console.error('Customer auth failure', error instanceof Error && error.message === 'PROFILE_UNAVAILABLE' ? 'profile_read' : 'auth_service');
    return out({ error: 'Usługa kont jest chwilowo niedostępna. Spróbuj ponownie za chwilę.' }, 503);
  }
}
