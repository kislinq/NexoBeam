import http from "node:http";
import net from "node:net";
import { fileURLToPath, URL } from "node:url";
import { resolve } from "node:path";
import httpProxy from "http-proxy";

const allowedPrefixes = [
  "/auth/v1/",
  "/functions/v1/",
  "/realtime/v1/",
  "/rest/v1/",
  "/storage/v1/",
];

export function isAllowedPath(requestUrl) {
  const pathname = new URL(requestUrl, "http://localhost").pathname;
  return allowedPrefixes.some((prefix) => pathname.startsWith(prefix));
}

export function createProxyServer(upstreamUrl) {
  const upstream = new URL(upstreamUrl);
  if (upstream.protocol !== "https:" || upstream.username || upstream.password) {
    throw new Error("SUPABASE_URL must be an HTTPS URL without credentials");
  }
  if (upstream.pathname !== "/" || upstream.search || upstream.hash) {
    throw new Error("SUPABASE_URL must not include a path, query, or fragment");
  }

  const proxy = httpProxy.createProxyServer({
    target: upstream.origin,
    changeOrigin: true,
    ws: true,
    secure: true,
    proxyTimeout: 30_000,
    timeout: 30_000,
  });

  proxy.on("error", (error, request, response) => {
    const pathname = request.url?.split("?")[0] ?? "/";
    console.error(`Supabase upstream error for ${pathname}: ${error.message}`);
    if (response instanceof net.Socket) {
      response.destroy();
    } else if (response && !response.headersSent) {
      response.writeHead(502, { "content-type": "text/plain; charset=utf-8" });
      response.end("Upstream unavailable");
    }
  });

  const server = http.createServer((request, response) => {
    if (request.method === "GET" && request.url === "/_health") {
      response.writeHead(200, { "content-type": "text/plain; charset=utf-8" });
      response.end("ok");
      return;
    }

    if (!isAllowedPath(request.url ?? "/")) {
      response.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
      response.end("Not found");
      return;
    }

    proxy.web(request, response);
  });

  server.on("upgrade", (request, socket, head) => {
    if (!isAllowedPath(request.url ?? "/")) {
      socket.end("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n");
      return;
    }
    proxy.ws(request, socket, head);
  });

  return server;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  if (!process.env.SUPABASE_URL) {
    throw new Error("SUPABASE_URL environment variable is required");
  }
  const port = Number.parseInt(process.env.PORT ?? "10000", 10);
  createProxyServer(process.env.SUPABASE_URL).listen(port, "0.0.0.0", () => {
    console.log(`NexoBeam proxy listening on port ${port}`);
  });
}
