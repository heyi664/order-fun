(() => {
  const API = '/api';
  const $ = selector => document.querySelector(selector);
  const getAdminToken = () => sessionStorage.getItem('admin_token') || '';
  if (!getAdminToken()) { location.replace('./login.html?mode=admin&next=./admin.html'); return; }
  const form = $('#publishForm'); const message = $('#message');
  function setMessage(text, type = '') { message.textContent = text || ''; message.className = type; }
  function toLocalInput(date) { const offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 16); }
  function formatMoney(cents) { return `¥${(Number(cents || 0) / 100).toFixed(2)}`; }
  async function api(path, options = {}) {
    const headers = Object.assign({}, options.headers || {}); headers.authorization = getAdminToken();
    if (options.body) headers['Content-Type'] = 'application/json';
    const response = await fetch(API + path, Object.assign({}, options, { headers })); let result; try { result = await response.json(); } catch (_) { }
    if (response.status === 401) { sessionStorage.removeItem('admin_token'); location.replace('./login.html?mode=admin&next=./admin.html'); throw new Error('管理员登录已失效'); }
    if (!response.ok || (result && result.success === false)) throw new Error((result && result.errorMsg) || `请求失败 (${response.status})`);
    return result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result;
  }
  function updatePreview() {
    const data = new FormData(form); const price = Number(data.get('price') || 0); const amount = Number(data.get('tokenAmount') || 0);
    $('#previewTitle').textContent = data.get('title') || '未命名 Token 包'; $('#previewPrice').textContent = `¥${price.toFixed(2)}`;
    $('#previewTokens').textContent = `${amount.toLocaleString()} Token / 份`; $('#previewStock').textContent = `库存 ${data.get('stock') || 0} 份`;
    $('#previewLimit').textContent = `单次 ${data.get('perOrderLimit') || 1} · 累计 ${data.get('perUserLimit') || 1}`;
  }
  function escape(value = '') { const div = document.createElement('div'); div.textContent = value; return div.innerHTML; }
  async function loadPackages() {
    const list = $('#packageList'); list.innerHTML = '<div class="empty">加载中…</div>';
    try { const packs = await api('/voucher/token-packs'); list.innerHTML = packs.length ? packs.map(pack => `<article class="package-row"><div><b>${escape(pack.title)}</b><span>库存 ${pack.stock ?? '--'} · 单次 ${pack.perOrderLimit || 1} · 累计 ${pack.perUserLimit || 1}</span></div><strong>${Number(pack.tokenAmount || 0).toLocaleString()} Token</strong><span>${formatMoney(pack.payValue)}</span></article>`).join('') : '<div class="empty">暂无已发布 Token 包。</div>'; } catch (error) { list.innerHTML = `<div class="empty">${escape(error.message)}</div>`; }
  }
  form.addEventListener('input', updatePreview);
  form.addEventListener('submit', async event => {
    event.preventDefault(); const data = Object.fromEntries(new FormData(form));
    if (Number(data.perUserLimit) < Number(data.perOrderLimit)) { setMessage('累计限购不能小于单次限购。', 'error'); return; }
    const button = $('#publishButton'); button.disabled = true; button.textContent = '发布中…'; setMessage('');
    try { const id = await api('/admin/token-packs', { method: 'POST', body: JSON.stringify(data) }); setMessage(`发布成功，Token 包编号：${id}`, 'success'); form.reset(); setDefaultTimes(); updatePreview(); loadPackages(); }
    catch (error) { setMessage(error.message, 'error'); }
    finally { button.disabled = false; button.textContent = '发布 Token 包 →'; }
  });
  $('#registrationCodeForm').addEventListener('submit', async event => {
    event.preventDefault(); const input = $('#newRegistrationCode'); const output = $('#securityMessage');
    if (input.value.trim().length < 6) { output.textContent = '校验码至少需要 6 位。'; output.className = 'error'; return; }
    try { await api('/admin/auth/registration-code', { method: 'POST', body: JSON.stringify({ verificationCode: input.value.trim() }) }); input.value = ''; output.textContent = '管理员注册校验码已更新。'; output.className = 'success'; }
    catch (error) { output.textContent = error.message; output.className = 'error'; }
  });
  $('#logout').addEventListener('click', async () => { try { await api('/admin/auth/logout', { method: 'POST' }); } finally { sessionStorage.removeItem('admin_token'); location.replace('./login.html?mode=admin'); } });
  function setDefaultTimes() { const now = new Date(); $('#beginTime').value = toLocalInput(now); $('#endTime').value = toLocalInput(new Date(now.getTime() + 7 * 24 * 3600 * 1000)); }
  $('#refresh').addEventListener('click', loadPackages); setDefaultTimes(); updatePreview(); loadPackages();
})();
