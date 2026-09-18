// Retired: customer authentication is served at /api/customer-auth.
Deno.serve(() => new Response(JSON.stringify({error:'Odśwież stronę sklepu, aby skorzystać z nowego logowania.'}), {status:410,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}}));
