(() => {
  'use strict';

  const API = '/api';
  const state = {
    route: 'community', feed: 'hot', page: 1, posts: [], topics: [],
    activeTopic: null, packages: [], conversationId: sessionStorage.getItem('agent_hub_conversation_id') || '',
    messages: readSession('agent_hub_messages', []), uploading: [], user: null
  };
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  function readSession(key, fallback) {
    try { return JSON.parse(sessionStorage.getItem(key)) || fallback; } catch (_) { return fallback; }
  }
  function escapeHtml(value = '') {
    return String(value).replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));
  }
  function getToken() { return sessionStorage.getItem('token') || ''; }
  function initials(name = 'U') { return escapeHtml(String(name).trim().slice(0, 1).toUpperCase() || 'U'); }
  function toast(message) {
    const el = $('#toast'); el.textContent = message; el.classList.add('show');
    window.clearTimeout(toast.timer); toast.timer = window.setTimeout(() => el.classList.remove('show'), 2600);
  }
  async function api(path, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    if (getToken()) headers.authorization = getToken();
    if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    const response = await fetch(API + path, Object.assign({}, options, { headers }));
    let body = null;
    try { body = await response.json(); } catch (_) { }
    if (!response.ok) throw new Error((body && (body.errorMsg || body.message)) || `请求失败 (${response.status})`);
    if (body && body.success === false) throw new Error(body.errorMsg || '请求未完成');
    return body && Object.prototype.hasOwnProperty.call(body, 'data') ? body.data : body;
  }
  function modal(id, open) { const el = $('#' + id); el.classList.toggle('open', open); el.setAttribute('aria-hidden', String(!open)); }
  function dateText(value) {
    if (!value) return '刚刚'; const date = new Date(value); if (Number.isNaN(date.getTime())) return '刚刚';
    const diff = Date.now() - date.getTime(); if (diff < 3600000) return `${Math.max(1, Math.floor(diff / 60000))} 分钟前`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
    return `${date.getMonth() + 1}月${date.getDate()}日`;
  }
  function topicNames(post) {
    const text = `${post.title || ''} ${post.content || ''}`.replace(/＃/g, '#'); const found = []; const re = /#([\p{L}\p{N}_-]{2,30})/gu; let match;
    while ((match = re.exec(text))) if (!found.includes(match[1])) found.push(match[1]);
    return found;
  }
  function postImages(post) { return String(post.images || '').split(',').filter(Boolean).slice(0, 3); }
  function avatar(post) {
    return post.icon ? `<span class="user-avatar"><img src="${escapeHtml(post.icon)}" alt=""></span>` : `<span class="user-avatar">${initials(post.name || post.nickName)}</span>`;
  }
  function postCard(post) {
    const topics = topicNames(post).map(name => `<button class="topic-tag" data-topic-name="${escapeHtml(name)}">#${escapeHtml(name)}</button>`).join('');
    const images = postImages(post).map(src => `<img src="${escapeHtml(src)}" alt="帖子图片">`).join('');
    return `<article class="post-card" data-post-id="${post.id}">
      <div class="post-author">${avatar(post)}<div class="author-meta"><b>${escapeHtml(post.name || post.nickName || 'Agent 用户')}</b><span>${dateText(post.createTime)}</span></div></div>
      <h3 class="post-title" data-open-post="${post.id}">${escapeHtml(post.title || '未命名帖子')}</h3>
      <p class="post-content">${escapeHtml(post.content || '')}</p>${images ? `<div class="post-images">${images}</div>` : ''}
      ${topics ? `<div class="tag-row">${topics}</div>` : ''}
      <div class="post-actions"><button class="post-action ${post.isLike ? 'liked' : ''}" data-like="${post.id}">♡ ${Number(post.liked || 0)}</button><button class="post-action" data-comment="${post.id}">□ ${Number(post.comments || 0)}</button><button class="post-action ${post.isFavorite ? 'liked' : ''}" data-favorite="${post.id}">♧ ${post.isFavorite ? '已收藏' : '收藏'}</button></div>
    </article>`;
  }
  function renderPosts(target, posts) {
    $(target).innerHTML = posts.length ? posts.map(postCard).join('') : '<div class="empty-feed">暂时没有可展示的帖子。</div>';
  }
  async function loadPosts(reset = false) {
    if (reset) { state.page = 1; state.posts = []; }
    $('#feedStatus').textContent = '加载中…';
    try {
      let data;
      if (state.feed === 'follow') {
        data = await api(`/blog/of/follow?lastId=${Date.now()}&offset=0`);
        data = (data && data.list) || [];
      } else data = await api(`/blog/hot?current=${state.page}`);
      const list = Array.isArray(data) ? data : [];
      state.posts = state.posts.concat(list);
      renderPosts('#postFeed', state.posts); state.page += 1;
      $('#feedStatus').textContent = state.posts.length ? `${state.posts.length} 条内容` : '';
      $('#loadMorePosts').hidden = list.length === 0;
    } catch (error) { $('#feedStatus').textContent = ''; toast(error.message); renderPosts('#postFeed', state.posts); }
  }
  async function loadTopics() {
    try {
      const data = await api('/topics/hot?limit=20'); state.topics = Array.isArray(data) ? data : [];
      const preview = state.topics.slice(0, 6);
      $('#hotTopicPreview').innerHTML = preview.length ? preview.map(topic => `<div class="hot-topic" data-topic-id="${topic.id}"><span class="rank-no">${topic.rank}</span><div class="topic-copy"><b>#${escapeHtml(topic.name)}</b><span>${Number(topic.heat || 0).toLocaleString()} 热度 · ${topic.blogCount || 0} 帖子</span></div><span>›</span></div>`).join('') : '<div class="empty-feed">暂无热榜数据</div>';
      $('#topicRank').innerHTML = state.topics.length ? state.topics.map(topic => `<div class="topic-rank-row" data-topic-id="${topic.id}"><strong class="big-rank">${topic.rank}</strong><div><b>#${escapeHtml(topic.name)}</b><span>${topic.blogCount || 0} 篇帖子</span></div><strong class="topic-heat">${Number(topic.heat || 0).toLocaleString()} 热度</strong></div>`).join('') : '<div class="empty-feed">暂无热榜数据</div>';
    } catch (error) { toast(`热榜加载失败：${error.message}`); }
  }
  async function openTopic(id) {
    const topic = state.topics.find(item => String(item.id) === String(id));
    if (!topic) return;
    state.activeTopic = topic; $('#activeTopicName').textContent = `#${topic.name}`; $('#topicPosts').classList.remove('hidden'); $('#topicPostFeed').innerHTML = '<div class="empty-feed">加载中…</div>';
    location.hash = 'topics';
    try { const data = await api(`/topics/${id}/blogs?current=1`); renderPosts('#topicPostFeed', Array.isArray(data) ? data : []); } catch (error) { $('#topicPostFeed').innerHTML = `<div class="empty-feed">${escapeHtml(error.message)}</div>`; }
  }
  function samplePackages() {
    return [
      { title: 'Starter Token Pack', subTitle: '适合轻量体验', tokenAmount: 20000, payValue: 990, stock: 80, mock: true },
      { title: 'Creator Token Pack', subTitle: '适合持续创作和测试', tokenAmount: 80000, payValue: 2990, stock: 35, mock: true, featured: true },
      { title: 'Team Token Pack', subTitle: '适合团队协作场景', tokenAmount: 250000, payValue: 7990, stock: 12, mock: true }
    ];
  }
  function money(value) { return `¥${(Number(value || 0) / 100).toFixed(2)}`; }
  function renderPackages() {
    $('#tokenPackages').innerHTML = state.packages.length ? state.packages.map((pack, index) => { const perOrder = Number(pack.perOrderLimit || 1); return `<article class="package-card ${pack.featured || index === 1 ? 'featured' : ''}"><span class="pack-tag">限量 Token 包</span><h2>${escapeHtml(pack.title || 'Token Pack')}</h2><p>${escapeHtml(pack.subTitle || '抢购后可兑换为 AI 对话 Token。')}</p><div class="package-price">${money(pack.payValue)} <small>· ${Number(pack.tokenAmount || 0).toLocaleString()} Token</small></div><div class="package-meta"><span>剩余 ${pack.stock ?? '--'} 份</span><span>单次限购 ${perOrder} 份</span></div><label class="package-quantity">购买数量 <input data-quantity type="number" min="1" max="${perOrder}" value="1"></label><button class="primary-btn" data-seckill="${pack.id || ''}" ${pack.stock === 0 ? 'disabled' : ''}>${pack.stock === 0 ? '已抢完' : '立即抢购'}</button></article>`; }).join('') : '<div class="empty-feed">暂无可抢购的 Token 包。</div>';
  }
  async function loadPackages() {
    try {
      const data = await api('/voucher/token-packs');
      const list = Array.isArray(data) ? data : [];
      state.packages = list.map((item, index) => Object.assign({}, item, { featured: index === 1 }));
    } catch (error) { state.packages = []; toast(`Token 包加载失败：${error.message}`); }
    renderPackages();
  }
  let packageClock = null;
  let packageRefreshTick = 0;
  function countdownText(milliseconds) {
    const seconds = Math.max(0, Math.floor(milliseconds / 1000));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const remainingSeconds = seconds % 60;
    return `${days} 天 : ${String(hours).padStart(2, '0')} 时 : ${String(minutes).padStart(2, '0')} 分 : ${String(remainingSeconds).padStart(2, '0')} 秒`;
  }
  function packageSaleState(pack, now = Date.now()) {
    const beginAt = Date.parse(pack.beginTime || '');
    const endAt = Date.parse(pack.endTime || '');
    if (Number.isFinite(endAt) && now >= endAt) return { type: 'ended', text: '已结束', endAt };
    if (Number.isFinite(beginAt) && now < beginAt) return { type: 'upcoming', text: `距开抢 ${countdownText(beginAt - now)}`, endAt };
    if (Number(pack.stock || 0) <= 0) return { type: 'sold-out', text: '已售罄', endAt };
    return { type: 'active', text: Number.isFinite(endAt) ? `距结束 ${countdownText(endAt - now)}` : '抢购进行中', endAt };
  }
  function renderPackages() {
    const now = Date.now();
    const visiblePackages = state.packages.filter(pack => {
      const endAt = Date.parse(pack.endTime || '');
      return !Number.isFinite(endAt) || now - endAt < 15 * 60 * 1000;
    });
    $('#tokenPackages').innerHTML = visiblePackages.length ? visiblePackages.map((pack, index) => {
      const perOrder = Number(pack.perOrderLimit || 1);
      const sale = packageSaleState(pack, now);
      const disabled = sale.type !== 'active';
      const buttonText = sale.type === 'upcoming' ? '即将开抢' : sale.type === 'ended' ? '已结束' : sale.type === 'sold-out' ? '已售罄' : '立即抢购';
      return `<article class="package-card ${pack.featured || index === 1 ? 'featured' : ''} ${disabled ? 'sale-closed' : ''}"><span class="pack-tag">限量 Token 包</span><h2>${escapeHtml(pack.title || 'Token Pack')}</h2><p>${escapeHtml(pack.subTitle || '抢购后可兑换为 AI 对话 Token。')}</p><div class="package-price">${money(pack.payValue)} <small>· ${Number(pack.tokenAmount || 0).toLocaleString()} Token</small></div><div class="package-meta"><span>剩余 ${pack.stock ?? '--'} 份</span><span>单次限购 ${perOrder} 份</span></div><div class="sale-countdown ${sale.type}">${sale.text}</div><label class="package-quantity">购买数量 <input data-quantity type="number" min="1" max="${perOrder}" value="1" ${disabled ? 'disabled' : ''}></label><button class="primary-btn ${disabled ? 'sale-disabled' : ''}" data-seckill="${pack.id || ''}" ${disabled ? 'disabled' : ''}>${buttonText}</button></article>`;
    }).join('') : '<div class="empty-feed">暂无可展示的 Token 包。</div>';
  }
  function startPackageClock() {
    if (packageClock) return;
    packageClock = window.setInterval(() => {
      renderPackages();
      packageRefreshTick += 1;
      if (packageRefreshTick % 30 === 0) loadPackages();
    }, 1000);
  }
  async function loadPackages() {
    try {
      const data = await api('/voucher/token-packs');
      const list = Array.isArray(data) ? data : [];
      state.packages = list.map((item, index) => Object.assign({}, item, { featured: index === 1 }));
    } catch (error) { state.packages = []; toast(`Token 包加载失败：${error.message}`); }
    renderPackages();
    startPackageClock();
  }
  let pendingPayment = null;
  function createPaymentRequestId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
    return `payment-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  }
  function openPayment(pack, quantity) {
    pendingPayment = { id: String(pack.id), quantity, title: pack.title || 'Token 包', amount: Number(pack.payValue || 0) * quantity, paymentRequestId: createPaymentRequestId() };
    $('#paymentPackageTitle').textContent = pendingPayment.title;
    $('#paymentQuantity').textContent = `${quantity} 份`;
    $('#paymentAmount').textContent = money(pendingPayment.amount);
    modal('paymentModal', true);
  }
  async function seckill(id, quantity = 1) {
    if (!getToken()) { toast('请先登录后再抢购'); setTimeout(() => { location.href = './login.html'; }, 800); return; }
    const pack = state.packages.find(item => String(item.id) === String(id));
    if (!pack) { toast('Token 包不存在或已下架'); return; }
    try {
      await api(`/voucher-order/seckill/check/${id}`, { method: 'POST', body: JSON.stringify({ quantity }) });
      openPayment(pack, quantity);
    } catch (error) { toast(error.message); }
  }
  async function confirmPayment() {
    if (!pendingPayment) return;
    const button = $('#confirmPayment'); button.disabled = true; button.textContent = '支付处理中…';
    try {
      const orderId = await api(`/voucher-order/seckill/${pendingPayment.id}`, { method: 'POST', body: JSON.stringify({ quantity: pendingPayment.quantity, paymentRequestId: pendingPayment.paymentRequestId }) });
      modal('paymentModal', false); toast(`支付成功，订单号：${orderId}，请在下方兑换。`);
      pendingPayment = null; loadPackages(); loadTokenOrders();
    } catch (error) { toast(error.message); loadPackages(); }
    finally { button.disabled = false; button.textContent = '确认支付'; }
  }
  async function loadTokenAccount() {
    if (!getToken()) {
      ['#headerBalance', '#tokenBalance', '#profileToken'].forEach(selector => $(selector).textContent = '--');
      return;
    }
    try {
      const account = await api('/token-account');
      const balance = Number(account && account.balance || 0).toLocaleString();
      ['#headerBalance', '#tokenBalance', '#profileToken'].forEach(selector => $(selector).textContent = balance);
    } catch (error) { toast(`Token 余额加载失败：${error.message}`); }
  }
  async function loadTokenOrders() {
    const target = $('#tokenOrders');
    if (!getToken()) { target.innerHTML = '<div class="empty-feed">登录后查看并兑换已抢到的 Token 包。</div>'; return; }
    target.innerHTML = '<div class="empty-feed">加载订单中…</div>';
    try {
      const orders = await api('/token-account/orders');
      target.innerHTML = orders.length ? orders.map(order => { const quantity = Number(order.quantity || 1); const amount = Number(order.tokenAmount || 0) * quantity; return `<article class="token-order"><div class="token-order-copy"><b>${escapeHtml(order.title || 'Token 包')}</b><span>订单 ${order.id} · ${quantity} 份 · ${dateText(order.createTime)}</span></div><strong class="token-order-amount">${amount.toLocaleString()} Token</strong>${order.redeemed ? '<span class="topic-tag">已兑换</span>' : `<button class="secondary-btn" data-redeem="${order.id}">兑换</button>`}</article>`; }).join('') : '<div class="empty-feed">还没有 Token 包订单。</div>';
    } catch (error) { target.innerHTML = `<div class="empty-feed">${escapeHtml(error.message)}</div>`; }
  }
  async function redeemOrder(orderId) {
    try {
      const result = await api(`/token-account/redeem/${orderId}`, { method: 'POST' });
      toast(result.alreadyRedeemed ? '该订单已兑换过。' : `兑换成功，到账 ${Number(result.redeemedAmount || 0).toLocaleString()} Token。`);
      loadTokenAccount(); loadTokenOrders();
    } catch (error) { toast(error.message); }
  }
  async function toggleLike(id) {
    if (!getToken()) { toast('请先登录后点赞'); return; }
    try { await api(`/blog/like/${id}`, { method: 'PUT' }); await refreshPost(id); } catch (error) { toast(error.message); }
  }
  async function refreshPost(id) {
    const post = await api(`/blog/${id}`); const replace = item => String(item.id) === String(id) ? post : item;
    state.posts = state.posts.map(replace); renderPosts('#postFeed', state.posts);
    if ($('#postModal').classList.contains('open')) openPost(id);
  }
  async function toggleFavorite(id, button) {
    if (!getToken()) { toast('请先登录后收藏'); return; }
    try {
      const result = await api(`/blog/favorites/${id}`, { method: 'PUT' });
      const favorited = Boolean(result && result.favorited);
      button.classList.toggle('liked', favorited); button.textContent = `♧ ${favorited ? '已收藏' : '收藏'}`;
    } catch (error) { toast(error.message); }
  }
  async function openPost(id, focusComment = false) {
    modal('postModal', true); $('#postDetail').innerHTML = '<div class="empty-feed">加载帖子详情…</div>';
    try {
      const detailRequests = [api(`/blog/${id}`), api(`/blog-comments?blogId=${id}`)];
      if (getToken()) detailRequests.push(api(`/blog/favorites/${id}/status`));
      const responses = await Promise.all(detailRequests); const post = responses[0]; const comments = responses[1];
      if (responses[2]) post.isFavorite = Boolean(responses[2].favorited);
      const commentList = Array.isArray(comments) ? comments : [];
      const html = `${postCard(post)}<section class="detail-comments"><h3>评论 · ${commentList.length}</h3><div>${commentList.length ? commentList.map(comment => `<article class="comment"><span class="user-avatar">${comment.userIcon ? `<img src="${escapeHtml(comment.userIcon)}" alt="">` : initials(comment.userName)}</span><div><b>${escapeHtml(comment.userName || '用户')}</b><p>${escapeHtml(comment.content || '')}</p><time>${dateText(comment.createTime)}</time></div></article>`).join('') : '<p class="empty-feed">还没有评论，来聊聊吧。</p>'}</div><form class="comment-form" data-comment-form="${post.id}"><input name="content" required maxlength="255" placeholder="写下你的想法"><button class="primary-btn">发送</button></form></section>`;
      $('#postDetail').innerHTML = html;
      if (focusComment) $('.comment-form input', $('#postDetail')).focus();
    } catch (error) { $('#postDetail').innerHTML = `<div class="empty-feed">${escapeHtml(error.message)}</div>`; }
  }
  async function addComment(form) {
    if (!getToken()) { toast('请先登录后评论'); return; }
    const content = new FormData(form).get('content').trim(); if (!content) return;
    try { await api('/blog-comments', { method: 'POST', body: JSON.stringify({ blogId: Number(form.dataset.commentForm), content }) }); toast('评论已发布'); openPost(form.dataset.commentForm, false); } catch (error) { toast(error.message); }
  }
  async function uploadImages(files) {
    const uploads = Array.from(files).slice(0, 6).map(async file => {
      const form = new FormData(); form.append('file', file); const data = await api('/upload/blog', { method: 'POST', body: form }); return String(data || '').startsWith('/imgs') ? data : `/imgs${data}`;
    });
    try { state.uploading = await Promise.all(uploads); } catch (error) { toast(`图片上传失败：${error.message}`); state.uploading = []; }
    $('#imagePreview').innerHTML = state.uploading.map(src => `<img src="${escapeHtml(src)}" alt="预览">`).join('');
  }
  async function createPost(form) {
    if (!getToken()) { toast('请先登录后发布'); return; }
    const data = new FormData(form); const title = String(data.get('title') || '').trim(); const content = String(data.get('content') || '').trim(); if (!title || !content) return;
    try { await api('/blog', { method: 'POST', body: JSON.stringify({ title, content, images: state.uploading.join(',') }) }); toast('帖子已发布'); form.reset(); state.uploading = []; $('#imagePreview').innerHTML = ''; modal('composerModal', false); state.feed = 'hot'; setActiveFeed(); loadPosts(true); loadTopics(); } catch (error) { toast(error.message); }
  }
  function setActiveFeed() { $$('.feed-tabs button').forEach(btn => btn.classList.toggle('active', btn.dataset.feed === state.feed)); }
  function setRoute() {
    const route = location.hash.replace('#', '') || 'community'; state.route = ['community', 'topics', 'tokens', 'chat', 'profile'].includes(route) ? route : 'community';
    $$('.page').forEach(page => page.classList.toggle('active', page.id === `${state.route}Page`));
    $$('.nav-list a').forEach(link => link.classList.toggle('active', link.dataset.route === state.route));
    $('.sidebar').classList.remove('open');
    if (state.route === 'profile') loadProfile();
    if (state.route === 'tokens') { loadTokenAccount(); loadTokenOrders(); }
    if (state.route === 'chat') renderChat();
  }
  function saveMessages() { sessionStorage.setItem('agent_hub_messages', JSON.stringify(state.messages)); }
  function renderChat() {
    const list = $('#messageList');
    if (!state.messages.length) list.innerHTML = `<div class="welcome-chat"><p class="eyebrow">KNOWLEDGE-POWERED AGENT</p><h1>今天想让 Agent 帮你解决什么？</h1><p>可以咨询产品使用、梳理工作流、总结知识库内容，或讨论社区里的问题。</p></div>`;
    else list.innerHTML = state.messages.map(message => `<article class="message ${message.role === 'user' ? 'user' : ''}"><span class="message-avatar">${message.role === 'user' ? '我' : 'AI'}</span><div class="bubble">${message.thinking ? `<div class="thinking">思考中：${escapeHtml(message.thinking)}</div>` : ''}<span class="message-label">${message.role === 'user' ? '你' : 'Agent Assistant'}</span>${escapeHtml(message.content || (message.streaming ? '正在生成…' : ''))}</div></article>`).join('');
    list.scrollTop = list.scrollHeight; $('#chatTitle').textContent = state.messages.find(item => item.role === 'user')?.content.slice(0, 22) || '新的 AI 对话';
  }
  function parseEvent(eventText, message) {
    let type = 'message'; const lines = [];
    eventText.split(/\r?\n/).forEach(line => { if (line.startsWith('event:')) type = line.slice(6).trim(); if (line.startsWith('data:')) lines.push(line.slice(5).trim()); });
    if (!lines.length) return; let payload; try { payload = JSON.parse(lines.join('\n')); } catch (_) { return; }
    if (type === 'meta') { state.conversationId = payload.conversationId || state.conversationId; sessionStorage.setItem('agent_hub_conversation_id', state.conversationId); }
    if (type === 'message' && payload.type === 'think') message.thinking = (message.thinking || '') + (payload.delta || '');
    if (type === 'message' && payload.type === 'response') message.content += payload.delta || '';
    if (type === 'error' || type === 'reject') message.content = message.content || payload.message || '本次对话未能完成。';
  }
  async function sendChat() {
    const input = $('#chatInput'); const content = input.value.trim(); if (!content || state.streaming) return;
    state.messages.push({ role: 'user', content }); input.value = ''; const answer = { role: 'assistant', content: '', thinking: '', streaming: true }; state.messages.push(answer); state.streaming = true; $('#sendChat').disabled = true; $('#chatStatus').textContent = '● 生成中'; renderChat();
    try {
      const headers = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }; if (getToken()) headers.authorization = getToken();
      const history = state.messages.slice(0, -1).slice(-10).map(item => ({ role: item.role, content: item.content }));
      const response = await fetch(`${API}/ai/chat/stream`, { method: 'POST', headers, body: JSON.stringify({ conversationId: state.conversationId, message: content, history }) });
      if (!response.ok || !response.body) throw new Error(`AI 服务暂不可用 (${response.status})`);
      const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
      while (true) { const chunk = await reader.read(); if (chunk.done) break; buffer += decoder.decode(chunk.value, { stream: true }); const events = buffer.split(/\r?\n\r?\n/); buffer = events.pop(); events.forEach(event => parseEvent(event, answer)); renderChat(); }
    } catch (error) { answer.content = answer.content || error.message; }
    finally { answer.streaming = false; state.streaming = false; $('#sendChat').disabled = false; $('#chatStatus').textContent = '● 在线'; saveMessages(); renderChat(); }
  }
  async function loadProfile() {
    if (!getToken()) {
      $('#profileLogin').textContent = '登录'; $('#profileLogin').disabled = false; $('#logoutButton').hidden = true;
      $('#myPosts').innerHTML = '<div class="empty-feed">登录后查看你的帖子。</div>';
      $('#myFavorites').innerHTML = '<div class="empty-feed">登录后查看你的收藏。</div>';
      loadTokenAccount(); return;
    }
    try { const user = await api('/user/me'); state.user = user; $('#profileName').textContent = user.nickName || '我的空间'; $('#profileAvatar').textContent = initials(user.nickName); $('#profileLogin').textContent = '已登录'; $('#profileLogin').disabled = true; $('#logoutButton').hidden = false; } catch (_) { $('#profileLogin').textContent = '登录'; }
    loadTokenAccount();
    try { const posts = await api('/blog/of/me?current=1'); const list = Array.isArray(posts) ? posts : []; $('#myPostCount').textContent = list.length; $('#myPosts').innerHTML = list.length ? list.map(post => `<article class="mini-post" data-open-post="${post.id}"><b>${escapeHtml(post.title || '未命名帖子')}</b><span>${dateText(post.createTime)} · ${Number(post.liked || 0)} 赞 · ${Number(post.comments || 0)} 评论</span></article>`).join('') : '<div class="empty-state"><span>◇</span><b>还没有发布帖子</b><p>分享你的第一个 Agent 使用心得。</p></div>'; } catch (error) { $('#myPosts').innerHTML = `<div class="empty-feed">${escapeHtml(error.message)}</div>`; }
    try { const favorites = await api('/blog/favorites/me?current=1'); $('#myFavorites').innerHTML = favorites.length ? favorites.map(post => `<article class="mini-post" data-open-post="${post.id}"><b>${escapeHtml(post.title || '未命名帖子')}</b><span>${dateText(post.createTime)} · 已收藏</span></article>`).join('') : '<div class="empty-state"><span>♡</span><b>还没有收藏帖子</b><p>收藏有价值的经验，方便随时回看。</p></div>'; } catch (error) { $('#myFavorites').innerHTML = `<div class="empty-feed">${escapeHtml(error.message)}</div>`; }
  }
  async function logout() {
    try { await api('/user/logout', { method: 'POST' }); } catch (error) { toast(error.message); return; }
    sessionStorage.removeItem('token'); state.user = null; toast('已退出登录'); loadProfile(); loadTokenAccount(); loadTokenOrders();
  }
  function bindEvents() {
    window.addEventListener('hashchange', setRoute); $('#mobileMenu').addEventListener('click', () => $('.sidebar').classList.toggle('open'));
    $('#openComposer').addEventListener('click', () => modal('composerModal', true)); $('#newPostButton').addEventListener('click', () => modal('composerModal', true));
    $$('.modal').forEach(el => el.addEventListener('click', event => { if (event.target === el) modal(el.id, false); }));
    $$('[data-close]').forEach(button => button.addEventListener('click', () => modal(button.dataset.close, false)));
    $('.feed-tabs').addEventListener('click', event => { const button = event.target.closest('[data-feed]'); if (!button || button.dataset.feed === state.feed) return; state.feed = button.dataset.feed; setActiveFeed(); loadPosts(true); });
    $('#loadMorePosts').addEventListener('click', () => loadPosts());
    $('#postImages').addEventListener('change', event => uploadImages(event.target.files)); $('#postForm').addEventListener('submit', event => { event.preventDefault(); createPost(event.currentTarget); });
    $('#tokenPackages').addEventListener('click', event => { const button = event.target.closest('[data-seckill]'); if (button) { const input = $('[data-quantity]', button.closest('.package-card')); seckill(button.dataset.seckill, Number(input && input.value || 1)); } });
    $('#tokenOrders').addEventListener('click', event => { const button = event.target.closest('[data-redeem]'); if (button) redeemOrder(button.dataset.redeem); });
    $('#refreshTokenOrders').addEventListener('click', () => { loadTokenAccount(); loadTokenOrders(); });
    $('#confirmPayment').addEventListener('click', confirmPayment);
    $('#newChat').addEventListener('click', () => { state.messages = []; state.conversationId = ''; sessionStorage.removeItem('agent_hub_messages'); sessionStorage.removeItem('agent_hub_conversation_id'); renderChat(); });
    $('#sendChat').addEventListener('click', sendChat); $('#chatInput').addEventListener('keydown', event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); sendChat(); } });
    $('#quickPrompts').innerHTML = ['如何为知识库问答设计提问模板？', '帮我梳理一个 Agent 工作流。', 'RAG 检索效果不稳定，该如何排查？'].map(text => `<button data-prompt="${escapeHtml(text)}">${escapeHtml(text)} →</button>`).join('');
    $('#quickPrompts').addEventListener('click', event => { const button = event.target.closest('[data-prompt]'); if (!button) return; $('#chatInput').value = button.dataset.prompt; sendChat(); });
    $('#profileButton').addEventListener('click', () => { location.hash = 'profile'; }); $('#profileLogin').addEventListener('click', () => { if (!getToken()) location.href = './login.html'; }); $('#logoutButton').addEventListener('click', logout);
    $('#globalSearch').addEventListener('keydown', event => { if (event.key === 'Enter') { const value = event.currentTarget.value.trim().replace(/^#/, ''); const topic = state.topics.find(item => item.name === value); if (topic) openTopic(topic.id); else toast('暂未找到该话题，可在社区发布带 # 的帖子创建它。'); } });
    document.addEventListener('click', event => {
      const topic = event.target.closest('[data-topic-id]'); if (topic) { openTopic(topic.dataset.topicId); return; }
      const topicByName = event.target.closest('[data-topic-name]'); if (topicByName) { const item = state.topics.find(row => row.name === topicByName.dataset.topicName); if (item) openTopic(item.id); else toast('该话题正在收录中'); return; }
      const like = event.target.closest('[data-like]'); if (like) { toggleLike(like.dataset.like); return; }
      const comment = event.target.closest('[data-comment]'); if (comment) { openPost(comment.dataset.comment, true); return; }
      const open = event.target.closest('[data-open-post]'); if (open) { openPost(open.dataset.openPost); return; }
      const favorite = event.target.closest('[data-favorite]'); if (favorite) toggleFavorite(favorite.dataset.favorite, favorite);
    });
    $('#postDetail').addEventListener('submit', event => { if (event.target.matches('[data-comment-form]')) { event.preventDefault(); addComment(event.target); } });
    $('#closeTopicPosts').addEventListener('click', () => $('#topicPosts').classList.add('hidden'));
  }
  async function init() { bindEvents(); setRoute(); renderChat(); await Promise.all([loadPosts(true), loadTopics(), loadPackages(), loadTokenAccount()]); }
  init();
})();
