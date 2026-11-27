/*
 * Service worker so para a casca do app: HTML, CSS, JS e icones.
 * Os dados nunca entram no cache: parametro e rotina desatualizados sao pior que tela vazia, e o
 * PWA serve justamente para ver como esta agora. Sem rede ele abre e avisa que nao carregou.
 */
const CACHE = 'luluco-casca-v13';
const CASCA = ['./', './index.html', './style.css', './ui.js', './app.js', './graficos.js', './agenda.js', './financas.js', './relatorios.js', './aquario.js', './avisos.js', './config.js', './manifest.webmanifest', './icon-192.png', './icon-512.png', './icon-180.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(CASCA)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // so a casca, e so do proprio dominio: chamada ao Supabase passa direto
  if (url.origin !== self.location.origin) return;
  e.respondWith(
    fetch(req)
      .then((r) => {
        if (r && r.ok) caches.open(CACHE).then((c) => c.put(req, r.clone()));
        return r;
      })
      .catch(() => caches.match(req).then((c) => c || caches.match('./index.html'))),
  );
});

// Avisos no iPhone (Web Push). O iOS exige mostrar uma notificacao a cada push recebido; push
// silencioso faz a Apple cortar a inscricao. O servidor so manda o que e para aparecer.
self.addEventListener('push', (e) => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch (_) { d = { body: e.data ? e.data.text() : '' }; }
  e.waitUntil(self.registration.showNotification(d.title || 'Luluco Reef', {
    body: d.body || '',
    tag: d.tag || undefined,
    icon: './icon-192.png',
    badge: './icon-192.png',
    data: { url: './' },
  }));
});

self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  e.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((cs) => {
      const aberto = cs.find((c) => 'focus' in c);
      return aberto ? aberto.focus() : self.clients.openWindow('./');
    }),
  );
});
