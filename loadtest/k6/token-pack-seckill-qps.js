import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import exec from 'k6/execution';

// 阶梯式恒定到达速率压测：每个请求使用一个独立测试用户，避免被每人限购干扰。
// RATE_PROFILE 格式："20:30s,40:30s,60:30s"，含义为每档目标 RPS 与持续时间。
const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '');
const TOKEN_INPUT = __ENV.AUTH_TOKENS || (__ENV.AUTH_TOKENS_FILE ? open(__ENV.AUTH_TOKENS_FILE) : '');
const TOKENS = TOKEN_INPUT
  .replace(/^\uFEFF/, '')
  .trim()
  .replace(/^AUTH_TOKENS=/, '')
  .split(',')
  .map((token) => token.trim())
  .filter(Boolean);
const VOUCHER_ID = __ENV.VOUCHER_ID;
const QUANTITY = Number(__ENV.SECKILL_QUANTITY || '1');
const PROFILE_TEXT = __ENV.RATE_PROFILE || '20:30s,40:30s,60:30s,80:30s,100:30s';
const MAX_VUS = Number(__ENV.MAX_VUS || '400');
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || String(Math.min(400, MAX_VUS)));
const SETTLE_SECONDS = Number(__ENV.STAGE_SETTLE_SECONDS || '10');

function parseDurationSeconds(value) {
  const match = /^(\d+)(s|m|h)$/.exec(value.trim());
  if (!match) throw new Error(`无效时长：${value}。请使用如 30s、2m、1h 的格式。`);
  const amount = Number(match[1]);
  const unit = match[2];
  return amount * (unit === 'h' ? 3600 : unit === 'm' ? 60 : 1);
}

function parseProfile(text) {
  return text.split(',').map((item, index) => {
    const [rateText, durationText, ...extra] = item.trim().split(':');
    const rate = Number(rateText);
    if (extra.length || !Number.isInteger(rate) || rate < 1 || !durationText) {
      throw new Error(`无效 RATE_PROFILE 第 ${index + 1} 档：${item}`);
    }
    const durationSeconds = parseDurationSeconds(durationText);
    return {
      name: `rps_${rate}_${index + 1}`,
      rate,
      duration: durationText,
      durationSeconds,
      expectedRequests: rate * durationSeconds,
      // constant-arrival-rate 在阶段边界可能多调度一两个迭代；预留槽位以确保令牌绝不复用。
      tokenSlots: rate * durationSeconds + 2,
    };
  });
}

if (!VOUCHER_ID) throw new Error('必须提供 VOUCHER_ID。');
if (!TOKENS.length) throw new Error('必须提供 AUTH_TOKENS 或 AUTH_TOKENS_FILE。');
if (!Number.isInteger(QUANTITY) || QUANTITY < 1) throw new Error('SECKILL_QUANTITY 必须是正整数。');
if (!Number.isInteger(MAX_VUS) || MAX_VUS < 1 || !Number.isInteger(PRE_ALLOCATED_VUS) || PRE_ALLOCATED_VUS < 1 || PRE_ALLOCATED_VUS > MAX_VUS) {
  throw new Error('PRE_ALLOCATED_VUS 必须为正整数且不大于 MAX_VUS。');
}
if (!Number.isInteger(SETTLE_SECONDS) || SETTLE_SECONDS < 0) throw new Error('STAGE_SETTLE_SECONDS 必须是非负整数。');

const PROFILE = parseProfile(PROFILE_TEXT);
const REQUIRED_TOKENS = PROFILE.reduce((sum, stage) => sum + stage.tokenSlots, 0);
if (TOKENS.length < REQUIRED_TOKENS) {
  throw new Error(`令牌数不足：本配置至少需要 ${REQUIRED_TOKENS} 个，当前只有 ${TOKENS.length} 个。`);
}

const orderAccepted = new Counter('seckill_order_accepted');
const orderRejected = new Counter('seckill_order_rejected');
const businessSuccess = new Rate('seckill_business_success');
const transportSuccess = new Rate('seckill_transport_success');
const networkFailures = new Counter('seckill_network_failures');
const stageMetrics = {};
const scenarios = {};

let tokenOffset = 0;
let startOffsetSeconds = 0;
for (const stage of PROFILE) {
  stageMetrics[stage.name] = {
    accepted: new Counter(`seckill_${stage.name}_accepted`),
    rejected: new Counter(`seckill_${stage.name}_rejected`),
    duration: new Trend(`seckill_${stage.name}_duration_ms`, true),
  };
  scenarios[stage.name] = {
    executor: 'constant-arrival-rate',
    exec: 'runStage',
    rate: stage.rate,
    timeUnit: '1s',
    duration: stage.duration,
    preAllocatedVUs: PRE_ALLOCATED_VUS,
    maxVUs: MAX_VUS,
    startTime: `${startOffsetSeconds}s`,
    gracefulStop: `${SETTLE_SECONDS}s`,
    env: {
      STAGE_NAME: stage.name,
      TOKEN_OFFSET: String(tokenOffset),
    },
    tags: { stage: stage.name, target_rps: String(stage.rate) },
  };
  tokenOffset += stage.tokenSlots;
  startOffsetSeconds += stage.durationSeconds + SETTLE_SECONDS;
}

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    seckill_transport_success: ['rate>0.99'],
    seckill_business_success: ['rate>0.99'],
    dropped_iterations: ['count==0'],
  },
  tags: { service: 'heyee-comments', test_type: 'token-pack-seckill-qps' },
};

export function runStage() {
  const stageName = __ENV.STAGE_NAME;
  const tokenIndex = Number(__ENV.TOKEN_OFFSET) + exec.scenario.iterationInTest;
  const token = TOKENS[tokenIndex];
  if (!token) throw new Error(`第 ${tokenIndex + 1} 个请求缺少测试令牌。`);

  // 后端仅接受 [A-Za-z0-9-]；stageName 中的下划线不能直接进入幂等号。
  const paymentRequestId = `k6-qps-${stageName.replace(/_/g, '-')}-${tokenIndex}-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
  const response = http.post(
    `${BASE_URL}/voucher-order/seckill/${VOUCHER_ID}`,
    JSON.stringify({ quantity: QUANTITY, paymentRequestId }),
    {
      headers: { 'Content-Type': 'application/json', authorization: token },
      tags: { endpoint: '/voucher-order/seckill/:id', stage: stageName },
    },
  );

  let payload;
  try { payload = response.json(); } catch (_) { payload = null; }
  const httpOk = response.status >= 200 && response.status < 300;
  const checked = check(response, {
    '秒杀请求：HTTP 2xx': () => httpOk,
    '秒杀请求：响应为 JSON': () => payload !== null,
  });
  transportSuccess.add(checked, { stage: stageName });
  if (response.status === 0) networkFailures.add(1, { stage: stageName });

  const accepted = payload && payload.success === true;
  if (!accepted && exec.scenario.iterationInTest === 0) {
    console.warn(`${stageName} 首个业务拒绝：${payload && payload.errorMsg ? payload.errorMsg : JSON.stringify(payload)}`);
  }
  businessSuccess.add(accepted, { stage: stageName });
  stageMetrics[stageName].duration.add(response.timings.duration);
  if (accepted) {
    orderAccepted.add(1, { stage: stageName });
    stageMetrics[stageName].accepted.add(1);
  } else {
    orderRejected.add(1, { stage: stageName });
    stageMetrics[stageName].rejected.add(1);
  }
}
