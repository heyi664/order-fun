(() => {
  const api = '/api';
  const form = document.querySelector('#loginForm');
  const email = document.querySelector('#email');
  const code = document.querySelector('#code');
  const agreement = document.querySelector('#agreement');
  const sendCodeButton = document.querySelector('#sendCode');
  const loginButton = document.querySelector('#loginButton');
  const message = document.querySelector('#formMessage');
  let timer = null;

  function setMessage(text = '', type = '') { message.textContent = text; message.className = `form-message ${type}`; }
  async function request(path, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    const response = await fetch(api + path, Object.assign({}, options, { headers }));
    let result = null; try { result = await response.json(); } catch (_) { }
    if (!response.ok || (result && result.success === false)) throw new Error((result && result.errorMsg) || '服务暂时不可用，请稍后重试。');
    return result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result;
  }
  function validEmail() { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim()); }
  function startCountdown() {
    let seconds = 60; sendCodeButton.disabled = true;
    const tick = () => { sendCodeButton.textContent = `${seconds} 秒后重发`; seconds -= 1; if (seconds < 0) { window.clearInterval(timer); timer = null; sendCodeButton.disabled = false; sendCodeButton.textContent = '发送验证码'; } };
    tick(); timer = window.setInterval(tick, 1000);
  }
  sendCodeButton.addEventListener('click', async () => {
    if (!validEmail()) { setMessage('请输入有效的邮箱地址。', 'error'); email.focus(); return; }
    const value = email.value.trim().toLowerCase(); sendCodeButton.disabled = true; sendCodeButton.textContent = '发送中…'; setMessage('');
    try { await request(`/user/code?email=${encodeURIComponent(value)}`, { method: 'POST' }); setMessage('验证码已发送，请查看邮箱。', 'success'); startCountdown(); }
    catch (error) { setMessage(error.message, 'error'); sendCodeButton.disabled = false; sendCodeButton.textContent = '发送验证码'; }
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!validEmail()) { setMessage('请输入有效的邮箱地址。', 'error'); email.focus(); return; }
    if (!code.value.trim()) { setMessage('请输入邮箱验证码。', 'error'); code.focus(); return; }
    if (!agreement.checked) { setMessage('请先阅读并同意用户服务协议与隐私政策。', 'error'); return; }
    loginButton.disabled = true; loginButton.textContent = '正在登录…'; setMessage('');
    try {
      const token = await request('/user/login', { method: 'POST', body: JSON.stringify({ email: email.value.trim().toLowerCase(), code: code.value.trim() }) });
      if (!token) throw new Error('登录失败，未获取到登录凭证。');
      sessionStorage.setItem('token', token); setMessage('登录成功，正在进入平台…', 'success'); window.setTimeout(() => { location.href = './'; }, 350);
    } catch (error) { setMessage(error.message, 'error'); loginButton.disabled = false; loginButton.innerHTML = '登录并进入平台 <span>→</span>'; }
  });
})();
