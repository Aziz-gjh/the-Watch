import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const port = Number(process.env.PORT || 4173);
const types = {'.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css; charset=utf-8', '.svg':'image/svg+xml', '.md':'text/plain; charset=utf-8', '.json':'application/json', '.jsonl':'application/x-ndjson', '.csv':'text/csv; charset=utf-8'};
http.createServer(async (req, res) => {
  try {
    const path = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const file = resolve(root, `.${path === '/' ? '/index.html' : path}`);
    const relative = file.slice(root.length).replaceAll('\\', '/');
    if (!file.startsWith(root) || relative.split('/').some(part => part.startsWith('.')) || relative.includes('node_modules') || !types[extname(file)]) {
      res.writeHead(404).end('Not found'); return;
    }
    const body = await readFile(file);
    res.writeHead(200, {'Content-Type':types[extname(file)], 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff'}).end(body);
  } catch { res.writeHead(404).end('Not found'); }
}).listen(port, '127.0.0.1', () => console.log(`The Watch: http://127.0.0.1:${port}`));
