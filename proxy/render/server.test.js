import assert from "node:assert/strict";
import test from "node:test";
import { createProxyServer, isAllowedPath } from "./server.js";

test("allows only the Supabase API prefixes", () => {
  for (const path of [
    "/auth/v1/health",
    "/functions/v1/send-push",
    "/realtime/v1/websocket?apikey=test",
    "/rest/v1/messages",
    "/storage/v1/object/public/avatars/avatar.jpg",
  ]) {
    assert.equal(isAllowedPath(path), true, `${path} should be forwarded`);
  }

  for (const path of ["/", "/_health/other", "/admin", "/auth/v2/token"]) {
    assert.equal(isAllowedPath(path), false, `${path} should be rejected`);
  }
});

test("requires a clean HTTPS Supabase origin", () => {
  assert.throws(() => createProxyServer("http://project.supabase.co"));
  assert.throws(() => createProxyServer("https://user:pass@project.supabase.co"));
  assert.throws(() => createProxyServer("https://project.supabase.co/custom-path"));
});

test("serves a local health check and rejects non-Supabase paths", async (context) => {
  const server = createProxyServer("https://project.supabase.co");
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  }));

  const { port } = server.address();
  const health = await fetch(`http://127.0.0.1:${port}/_health`);
  assert.equal(health.status, 200);
  assert.equal(await health.text(), "ok");

  const forbidden = await fetch(`http://127.0.0.1:${port}/admin`);
  assert.equal(forbidden.status, 404);
});
