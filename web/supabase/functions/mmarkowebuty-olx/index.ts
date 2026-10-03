// mMarkoweButy OLX Partner API integration
// SAFE SCAFFOLD: no OLX write operation is enabled yet.

const OLX_API_BASE = "https://www.olx.pl/api/partner";
const OLX_API_VERSION = "2.0";

type Mode = "dry-run" | "production";

function getMode(): Mode {
  // Production must be explicitly enabled after acceptance and verification.
  return Deno.env.get("OLX_PRODUCTION_ENABLED") === "true" ? "production" : "dry-run";
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

Deno.serve(async (req) => {
  const url = new URL(req.url);

  if (req.method === "GET" && url.pathname.endsWith("/health")) {
    return json(200, {
      ok: true,
      service: "mmarkowebuty-olx",
      mode: getMode(),
      apiVersion: OLX_API_VERSION,
      apiBaseConfigured: OLX_API_BASE,
      credentialsPresent: Boolean(
        Deno.env.get("OLX_CLIENT_ID") &&
        Deno.env.get("OLX_CLIENT_SECRET") &&
        Deno.env.get("OLX_REDIRECT_URI")
      ),
      writesEnabled: false,
    });
  }

  return json(503, {
    ok: false,
    code: "OLX_INTEGRATION_NOT_ENABLED",
    message: "OLX integration is prepared in safe mode. Production operations are not enabled yet.",
  });
});
