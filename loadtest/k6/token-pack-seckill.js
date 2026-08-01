import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import exec from 'k6/execution';

// 多用户同一时刻抢购 Token 包。
// 每个 VU 只提交一次下单请求，且强制一对一使用 AUTH_TOKENS 中的测试账号。

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '');
// Windows 单个环境变量有长度限制。AUTH_TOKENS_FILE 可指向一行
// "AUTH_TOKENS=token1,token2,..." 或纯逗号分隔 token 的本地文件。
const TOKEN_INPUT = __ENV.AUTH_TOKENS || (__ENV.AUTH_TOKENS_FILE ? open(__ENV.AUTH_TOKENS_FILE) : '');
const TOKENS = TOKEN_INPUT
  .replace(/^\uFEFF/, '')
  .trim()
  .replace(/^AUTH_TOKENS=/, '')
  .split(',')
  .map((token) => token.trim())
  .filter(Boolean);
const VOUCHER_ID = __ENV.VOUCHER_ID;
const VUS = Number(__ENV.VUS || TOKENS.length);
const QUANTITY = Number(__ENV.SECKILL_QUANTITY || '1');
const START_AT = __ENV.START_AT;

const orderAccepted = new Counter('seckill_order_accepted');
const orderRejected = new Counter('seckill_order_rejected');
const transportSuccess = new Rate('seckill_transport_success');
const networkFailures = new Counter('seckill_network_failures');
const http2xx = new Counter('seckill_http_2xx');
const http4xx = new Counter('seckill_http_4xx');
const http5xx = new Counter('seckill_http_5xx');
const httpOther = new Counter('seckill_http_other_status');

if (!VOUCHER_ID) throw new Error('必须提供 VOUCHER_ID。');
if (!TOKENS.length) throw new Error('必须提供 AUTH_TOKENS（一个测试用户令牌对应一个 VU）。');
if (!Number.isInteger(VUS) || VUS < 1) throw new Error('VUS 必须是正整数。');
if (VUS > TOKENS.length) throw new Error(`VUS=${VUS} 大于令牌数 ${TOKENS.length}，会导致多个 VU 共用同一用户。`);
if (!Number.isInteger(QUANTITY) || QUANTITY < 1) throw new Error('SECKILL_QUANTITY 必须是正整数。');
if (!START_AT || Number.isNaN(Date.parse(START_AT))) {
  throw new Error('必须提供未来的 START_AT，ISO 8601 格式，例如 2026-07-31T22:00:00+08:00。');
}

// 在 setup 阶段校验，而不是初始化阶段；k6 生成汇总时会再次加载脚本，
// 那时 START_AT 已经过期，不应导致压测结果文件写入失败。
export function setup() {
  if (Date.parse(START_AT) <= Date.now()) {
    throw new Error('START_AT 必须晚于脚本启动时间。');
  }
}

export const options = {
  scenarios: {
    simultaneous_seckill: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || '10m',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    seckill_transport_success: ['rate>0.99'],
  },
  tags: { service: 'hy-agent-hub', test_type: 'simultaneous-seckill' },
};

function waitForStart() {
  const remainingSeconds = (Date.parse(START_AT) - Date.now()) / 1000;
  if (remainingSeconds > 0) sleep(remainingSeconds);
}

export default function () {
  // idInTest 从 1 开始，因此 VU 1 对应第 1 个令牌；不会轮换或共享账号。
  const userSlot = exec.vu.idInTest;
  const token = TOKENS[userSlot - 1];
  waitForStart();

  const paymentRequestId = `k6-seckill-${userSlot}-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
  const response = http.post(
    `${BASE_URL}/voucher-order/seckill/${VOUCHER_ID}`,
    JSON.stringify({ quantity: QUANTITY, paymentRequestId }),
    {
      headers: { 'Content-Type': 'application/json', authorization: token },
      tags: { endpoint: '/voucher-order/seckill/:id', user_slot: String(userSlot) },
    },
  );

  let payload;
  try { payload = response.json(); } catch (_) { payload = null; }
  if (response.status === 0) networkFailures.add(1);
  else if (response.status >= 200 && response.status < 300) http2xx.add(1);
  else if (response.status >= 400 && response.status < 500) http4xx.add(1);
  else if (response.status >= 500 && response.status < 600) http5xx.add(1);
  else httpOther.add(1);
  const ok = check(response, {
    '秒杀请求：HTTP 2xx': (r) => r.status >= 200 && r.status < 300,
    '秒杀请求：响应为 JSON': () => payload !== null,
  });
  transportSuccess.add(ok);

  // Result.success=false 可能是售罄、限购、活动未开始或限流；保留为业务结果而非 HTTP 故障。
  if (payload && payload.success === true) orderAccepted.add(1);
  else orderRejected.add(1);
}
