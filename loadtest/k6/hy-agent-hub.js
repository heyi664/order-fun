import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// HYEEE Agent Hub k6 压测脚本。
// 默认只覆盖读链路；传入 AUTH_TOKENS 后会附带账户读取链路。
// 秒杀下单为有副作用的操作，仅在 ENABLE_SECKILL=true 时执行。

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '');
const PROFILE = __ENV.PROFILE || 'load';
const TOKENS = (__ENV.AUTH_TOKENS || '')
  .split(',')
  .map((token) => token.trim())
  .filter(Boolean);
const ENABLE_SECKILL = (__ENV.ENABLE_SECKILL || 'false').toLowerCase() === 'true';
const VOUCHER_ID = __ENV.VOUCHER_ID;
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || '0.2');
const MAX_PAGE = Number(__ENV.MAX_PAGE || '5');

const businessFailures = new Counter('business_failures');
const businessSuccess = new Rate('business_success');
const seckillAccepted = new Counter('seckill_accepted');
const seckillRejected = new Counter('seckill_rejected');

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomItem(items) {
  return items[randomInt(0, items.length - 1)];
}

const profiles = {
  smoke: {
    executor: 'per-vu-iterations',
    vus: Number(__ENV.VUS || '1'),
    iterations: Number(__ENV.ITERATIONS || '10'),
    maxDuration: __ENV.MAX_DURATION || '2m',
  },
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: __ENV.RAMP_UP || '1m', target: Number(__ENV.VUS || '20') },
      { duration: __ENV.HOLD || '3m', target: Number(__ENV.VUS || '20') },
      { duration: __ENV.RAMP_DOWN || '30s', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: __ENV.RAMP_UP || '2m', target: Number(__ENV.VUS || '50') },
      { duration: __ENV.HOLD || '5m', target: Number(__ENV.VUS || '50') },
      { duration: __ENV.PEAK || '2m', target: Number(__ENV.PEAK_VUS || '100') },
      { duration: __ENV.RAMP_DOWN || '1m', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  soak: {
    executor: 'constant-vus',
    vus: Number(__ENV.VUS || '10'),
    duration: __ENV.DURATION || '30m',
    gracefulStop: '30s',
  },
};

if (!profiles[PROFILE]) {
  throw new Error(`未知 PROFILE=${PROFILE}，可选值：${Object.keys(profiles).join(', ')}`);
}
if (ENABLE_SECKILL && !VOUCHER_ID) {
  throw new Error('开启 ENABLE_SECKILL=true 时必须提供 VOUCHER_ID。');
}
if (ENABLE_SECKILL && TOKENS.length === 0) {
  throw new Error('秒杀压测必须提供 AUTH_TOKENS（逗号分隔的测试用户令牌）。');
}

export const options = {
  scenarios: { agent_hub: profiles[PROFILE] },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    business_success: ['rate>0.99'],
  },
  tags: { service: 'hy-agent-hub', profile: PROFILE },
};

function headers() {
  const token = TOKENS[(__VU - 1) % TOKENS.length];
  const value = { 'Content-Type': 'application/json' };
  // 前端代码直接把 token 放在 authorization 头中，不添加 Bearer 前缀。
  if (token) value.authorization = token;
  return value;
}

function request(method, path, body = null, tags = {}, allowBusinessReject = false) {
  const response = http.request(method, `${BASE_URL}${path}`, body, {
    headers: headers(),
    tags: { endpoint: path.replace(/\/\d+(?=\/|$)/g, '/:id'), ...tags },
  });

  let payload;
  try {
    payload = response.json();
  } catch (_) {
    payload = null;
  }
  const succeeded = Boolean(payload && payload.success === true);
  const ok = check(response, {
    [`${method} ${path}: HTTP 2xx`]: (r) => r.status >= 200 && r.status < 300,
    [`${method} ${path}: Result.success`]: () => succeeded || allowBusinessReject,
  });
  businessSuccess.add(ok);
  if (!ok) {
    businessFailures.add(1);
  }
  return { response, payload, ok, succeeded };
}

function chooseBlog() {
  const current = randomInt(1, MAX_PAGE);
  const result = request('GET', `/blog/hot?current=${current}`);
  const blogs = result.payload && Array.isArray(result.payload.data) ? result.payload.data : [];
  return blogs.length ? randomItem(blogs) : null;
}

function readPublicJourney() {
  // 首页并行加载的三项核心内容：热门帖子、热门话题、Token 包。
  const responses = http.batch([
    ['GET', `${BASE_URL}/topics/hot?limit=20`, null, { headers: headers(), tags: { endpoint: '/topics/hot' } }],
    ['GET', `${BASE_URL}/voucher/token-packs`, null, { headers: headers(), tags: { endpoint: '/voucher/token-packs' } }],
    ['GET', `${BASE_URL}/shop-type/list`, null, { headers: headers(), tags: { endpoint: '/shop-type/list' } }],
  ]);
  responses.forEach((response) => {
    let payload;
    try { payload = response.json(); } catch (_) { payload = null; }
    const ok = check(response, {
      '首页读取：HTTP 2xx': (r) => r.status >= 200 && r.status < 300,
      '首页读取：Result.success': () => payload && payload.success === true,
    });
    businessSuccess.add(ok);
    if (!ok) businessFailures.add(1);
  });

  const blog = chooseBlog();
  if (blog && blog.id) {
    // 帖子详情页会同时读取正文和评论。
    const detail = http.batch([
      ['GET', `${BASE_URL}/blog/${blog.id}`, null, { headers: headers(), tags: { endpoint: '/blog/:id' } }],
      ['GET', `${BASE_URL}/blog-comments?blogId=${blog.id}`, null, { headers: headers(), tags: { endpoint: '/blog-comments' } }],
    ]);
    detail.forEach((response) => {
      let payload;
      try { payload = response.json(); } catch (_) { payload = null; }
      const ok = check(response, {
        '帖子详情读取：HTTP 2xx': (r) => r.status >= 200 && r.status < 300,
        '帖子详情读取：Result.success': () => payload && payload.success === true,
      });
      businessSuccess.add(ok);
      if (!ok) businessFailures.add(1);
    });
  }
}

function readAuthenticatedJourney() {
  if (TOKENS.length === 0) return;
  // 不做点赞、评论、兑换等写操作，避免默认压测污染生产数据。
  request('GET', '/user/me');
  request('GET', '/token-account');
  request('GET', '/token-account/orders');
}

function seckillOnce() {
  const quantity = Number(__ENV.SECKILL_QUANTITY || '1');
  const eligibility = request('POST', `/voucher-order/seckill/check/${VOUCHER_ID}`,
    JSON.stringify({ quantity }), {}, true);
  if (!eligibility.succeeded) {
    seckillRejected.add(1);
    return;
  }

  // 每个 VU 每轮生成唯一支付幂等键，方便服务端排查和订单去重验证。
  const paymentRequestId = `k6-${__VU}-${__ITER}-${Date.now()}-${randomInt(1000, 9999)}`;
  const order = request('POST', `/voucher-order/seckill/${VOUCHER_ID}`,
    JSON.stringify({ quantity, paymentRequestId }), {}, true);

  // 秒杀售罄/限购通常会以 HTTP 200 + Result.success=false 返回，是业务预期结果，单独计数。
  if (order.succeeded) seckillAccepted.add(1);
  else seckillRejected.add(1);
}

export default function () {
  readPublicJourney();
  if (TOKENS.length > 0 && __ITER % 3 === 0) readAuthenticatedJourney();
  if (ENABLE_SECKILL) seckillOnce();
  sleep(THINK_TIME_SECONDS);
}
