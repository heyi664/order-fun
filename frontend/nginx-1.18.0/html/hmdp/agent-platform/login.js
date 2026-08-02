(() => {
  const api = '/api';
  const $ = selector => document.querySelector(selector);
  const form = $('#loginForm'); const message = $('#formMessage');
  let mode = new URLSearchParams(location.search).get('mode') === 'admin' ? 'admin' : 'user';
  let adminAction = 'login'; let timer = null; let captchaId = '';

  function setMessage(text = '', type = '') { message.textContent = text; message.className = `form-message ${type}`; }
  async function request(path, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    const response = await fetch(api + path, Object.assign({}, options, { headers }));
    let result = null; try { result = await response.json(); } catch (_) { }
    if (!response.ok || (result && result.success === false)) throw new Error((result && result.errorMsg) || `请求失败 (${response.status})`);
    return result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result;
  }
  async function refreshImageCaptcha() {
    const button = $('#refreshCaptcha'); const image = $('#captchaImage');
    if (!button || !image) return;
    button.disabled = true; image.alt = '正在加载图形验证码';
    try {
      const captcha = await request('/user/captcha');
      captchaId = captcha.captchaId; image.src = captcha.image; image.alt = '四位图形验证码，点击图片可刷新';
      $('#imageCaptcha').value = '';
    } catch (error) {
      captchaId = ''; image.removeAttribute('src'); image.alt = '图形验证码加载失败'; setMessage(error.message, 'error');
    } finally { button.disabled = false; }
  }
  function setMode(nextMode) {
    mode = nextMode; adminAction = 'login'; form.reset(); setMessage('');
    document.querySelectorAll('[data-mode]').forEach(button => button.classList.toggle('active', button.dataset.mode === mode));
    $('#userFields').hidden = mode !== 'user'; $('#adminFields').hidden = mode !== 'admin'; $('#adminAction').hidden = mode !== 'admin';
    $('#loginTitle').textContent = mode === 'user' ? '使用邮箱登录' : '管理员账号登录';
    $('#loginSubtitle').textContent = mode === 'user' ? '验证码将发送到你的邮箱；首次验证会自动创建账号。' : '管理员必须使用独立账号和密码登录。';
    $('#loginButton').innerHTML = mode === 'user' ? '登录并进入平台<span>→</span>' : '管理员登录<span>→</span>';
    $('#adminAction').textContent = '没有管理员账号？使用校验码注册';
    $('#registrationCodeRow').hidden = true;
    if (mode === 'user') refreshImageCaptcha();
  }
  function validEmail() { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test($('#email').value.trim()); }
  function userReturnTarget() {
    const next = new URLSearchParams(location.search).get('next') || '';
    return next.startsWith('./') && !next.startsWith('//') ? next : './';
  }
  function startCountdown() {
    let seconds = 60; const button = $('#sendCode'); button.disabled = true;
    const tick = () => { button.textContent = `${seconds} 秒后重发`; seconds -= 1; if (seconds < 0) { clearInterval(timer); timer = null; button.disabled = false; button.textContent = '发送验证码'; } };
    tick(); timer = setInterval(tick, 1000);
  }
  $('#sendCode').addEventListener('click', async () => {
    if (!validEmail()) { setMessage('请输入有效的邮箱地址。', 'error'); return; }
    const captchaCode = $('#imageCaptcha').value.trim();
    if (!captchaId || !/^\d{4}$/.test(captchaCode)) { setMessage('请输入图片中的 4 位数字验证码。', 'error'); return; }
    const button = $('#sendCode'); button.disabled = true; button.textContent = '发送中…';
    try { await request(`/user/code?email=${encodeURIComponent($('#email').value.trim().toLowerCase())}&captchaId=${encodeURIComponent(captchaId)}&captchaCode=${encodeURIComponent(captchaCode)}`, { method: 'POST' }); setMessage('验证码已发送，请查看邮箱。', 'success'); startCountdown(); }
    catch (error) { await refreshImageCaptcha(); setMessage(error.message, 'error'); button.disabled = false; button.textContent = '发送验证码'; }
  });
  $('#refreshCaptcha').addEventListener('click', refreshImageCaptcha);
  $('#adminAction').addEventListener('click', () => {
    adminAction = adminAction === 'login' ? 'register' : 'login'; setMessage('');
    const registering = adminAction === 'register'; $('#registrationCodeRow').hidden = !registering;
    $('#loginTitle').textContent = registering ? '注册管理员账号' : '管理员账号登录';
    $('#loginSubtitle').textContent = registering ? '注册需要当前管理员设置的校验码；首次注册使用环境变量校验码。' : '管理员必须使用独立账号和密码登录。';
    $('#loginButton').innerHTML = registering ? '注册并进入管理台<span>→</span>' : '管理员登录<span>→</span>';
    $('#adminAction').textContent = registering ? '已有管理员账号？返回登录' : '没有管理员账号？使用校验码注册';
  });
  form.addEventListener('submit', async event => {
    event.preventDefault(); const button = $('#loginButton');
    let path; let body; let target;
    if (mode === 'user') {
      if (!validEmail() || !$('#code').value.trim()) return setMessage('请输入邮箱和验证码。', 'error');
      if (!$('#agreement').checked) return setMessage('请先同意服务协议与隐私政策。', 'error');
      path = '/user/login'; body = { email: $('#email').value.trim().toLowerCase(), code: $('#code').value.trim() }; target = userReturnTarget();
    } else {
      const username = $('#adminUsername').value.trim(); const password = $('#adminPassword').value;
      if (!/^[A-Za-z0-9_.-]{3,32}$/.test(username) || password.length < 8) return setMessage('请输入合法账号和至少 8 位密码。', 'error');
      if (adminAction === 'register' && !$('#registrationCode').value.trim()) return setMessage('请输入管理员注册校验码。', 'error');
      path = adminAction === 'register' ? '/admin/auth/register' : '/admin/auth/login';
      body = { username, password, verificationCode: $('#registrationCode').value.trim() }; target = new URLSearchParams(location.search).get('next') || './admin.html';
    }
    button.disabled = true; setMessage('');
    try {
      const token = await request(path, { method: 'POST', body: JSON.stringify(body) });
      if (!token) throw new Error('登录失败，未获取到登录凭证。');
      if (mode === 'admin') sessionStorage.setItem('admin_token', token); else sessionStorage.setItem('token', token);
      setMessage('登录成功，正在跳转…', 'success'); setTimeout(() => { location.href = target; }, 280);
    } catch (error) { setMessage(error.message, 'error'); button.disabled = false; }
  });
  document.querySelectorAll('[data-mode]').forEach(button => button.addEventListener('click', () => setMode(button.dataset.mode)));
  setMode(mode);
})();
