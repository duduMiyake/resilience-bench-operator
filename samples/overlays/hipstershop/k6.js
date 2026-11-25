import { parseHTML } from 'k6/html';
import { check, group } from 'k6';
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import {
  AWSConfig,
  S3Client,
} from 'https://jslib.k6.io/aws/0.12.3/s3.js';

const s3 = new S3Client(new AWSConfig({
  region: __ENV.AWS_REGION || 'us-east-1',
  accessKeyId: __ENV.AWS_ACCESS_KEY_ID,
  secretAccessKey: __ENV.AWS_SECRET_ACCESS_KEY,
}));

const vus = parseInt(__ENV.VIRTUAL_USERS || '10', 10);
const iterations = parseInt(__ENV.K6_ITERATIONS || '10', 10);

console.log(`DEBUG: VUs: ${vus}, Iterations per VU: ${iterations}`);

export const options = {
      vus,
      iterations,
};

const creditCard = {
  email: 'someoneelse@example.com',
  street_address: '1321 Washington Soares Avenue',
  zip_code: '98033',
  city: 'Fortaleza',
  state: 'CE',
  country: 'Brazil',
  credit_card_number: '5379759871429836',
  credit_card_expiration_month: '1',
  credit_card_expiration_year: '2034',
  credit_card_cvv: '665',
};

const outputPath = __ENV.OUTPUT_PATH;
const bucketName = __ENV.BUCKET_NAME || __ENV.AWS_BUCKET_NAME;
const s3Prefix = __ENV.AWS_S3_PREFIX || '';
const HOST = __ENV.HOST || 'frontend';

const httpDurationIndex = new Trend('custom_index_http_req_duration');
const httpErrorDurationIndex = new Trend('custom_index_error_http_req_duration');
const successIndex = new Counter('custom_index_success');
const errorIndex = new Counter('custom_index_error');

const successAd = new Counter('custom_ad_success');
const errorAd = new Counter('custom_ad_error');

const successShippingOnCart = new Counter('custom_shipping_cart_success');
const errorShippingOnCart = new Counter('custom_shipping_cart_error');

const successRecommendations = new Counter('custom_recommendations_success');
const errorRecommendations = new Counter('custom_recommendations_error');

const httpDurationProduct = new Trend('custom_product_http_req_duration');
const httpErrorDurationProduct = new Trend('custom_product_error_http_req_duration');
const successProduct = new Counter('custom_product_success');
const errorProduct = new Counter('custom_product_error');

const httpDurationCart = new Trend('custom_cart_http_req_duration');
const httpErrorDurationCart = new Trend('custom_cart_error_http_req_duration');
const successCart = new Counter('custom_cart_success');
const errorCart = new Counter('custom_cart_error');

const httpDurationViewCart = new Trend('custom_viewcart_http_req_duration');
const httpErrorDurationViewCart = new Trend('custom_viewcart_error_http_req_duration');
const successViewCart = new Counter('custom_viewcart_success');
const errorViewCart = new Counter('custom_viewcart_error');

const httpDurationCurrency = new Trend('custom_currency_http_req_duration');
const httpErrorDurationCurrency = new Trend('custom_currency_error_http_req_duration');
const successCurrency = new Counter('custom_currency_success');
const errorCurrency = new Counter('custom_currency_error');

const httpDurationCheckout = new Trend('custom_checkout_http_req_duration');
const httpErrorDurationCheckout = new Trend('custom_checkout_error_http_req_duration');
const successCheckout = new Counter('custom_checkout_success');
const errorCheckout = new Counter('custom_checkout_error');

const sessionDuration = new Trend('session_duration');
const journeyStarts = new Counter('custom_journey_started');
const journeyCompletions = new Counter('custom_journey_completed');
const checkoutAttempts = new Counter('custom_checkout_attempted');
const checkoutSkipped = new Counter('custom_checkout_skipped');

export default function () {
  const startTime = Date.now();
  journeyStarts.add(1);
  let attempted = false;
  let completed = false;
  try {
    group('main_session', function () {
      index();
      browseProduct();
      const productId = randomProduct();
      if (!addToCart(productId) || !viewCart(productId)) {
        return;
      }
      setCurrency();
      attempted = true;
      completed = checkout();
    });
  } finally {
    checkoutSkipped.add(attempted ? 0 : 1);
    journeyCompletions.add(completed ? 1 : 0);
    sessionDuration.add(Date.now() - startTime);
  }
}

function randomCurrency() {
  const currencies = ['EUR', 'USD', 'JPY', 'CAD', 'GBP', 'TRY'];
  return currencies[Math.floor(Math.random() * currencies.length)];
}

function randomProduct() {
  const products = [
    '0PUK6V6EV0',
    '1YMWWN1N4O',
    '2ZYFJ3GM2N',
    '66VCHSJNUP',
    '6E92ZMYYFZ',
    '9SIQT8TOJO',
    'L9ECAV7KIM',
    'LS4PSXUNUM',
    'OLJCESPC7Z'
  ];
  return products[Math.floor(Math.random() * products.length)];
}

function randomQuantity() {
  return Math.floor(Math.random() * 10) + 1;
}

function index() {
  const res = http.get(`http://${HOST}/`);
  const successfulIndex = res.status === 200 ? 1 : 0;
  if (successfulIndex) {
    httpDurationIndex.add(res.timings.duration);
  } else {
    httpErrorDurationIndex.add(res.timings.duration);
  }
  successIndex.add(successfulIndex ? 1 : 0);
  errorIndex.add(successfulIndex ? 0 : 1);
}

function browseProduct() {
  const res = http.get(`http://${HOST}/product/${randomProduct()}`);
  const body = parseHTML(res.body || '');

  const ad = body.find('body > main > div.ad > div > div > strong');
  successAd.add(ad.text() === 'Ad' ? 1 : 0);
  errorAd.add(ad.text() === 'Ad' ? 0 : 1);

  const recommendation = body.find('body > main > div:nth-child(2) > section.recommendations');
  const successfulRecommentations = recommendation.text() !== '';
  successRecommendations.add(successfulRecommentations ? 1 : 0);
  errorRecommendations.add(successfulRecommentations ? 0 : 1);

  const successfulProduct = res.status === 200;
  if (successfulProduct) {
    httpDurationProduct.add(res.timings.duration);
  } else {
    httpErrorDurationProduct.add(res.timings.duration);
  }
  successProduct.add(successfulProduct ? 1 : 0);
  errorProduct.add(successfulProduct ? 0 : 1);
}

function recordStage(response, ok, successDuration, errorDuration, successes, errors) {
  (ok ? successDuration : errorDuration).add(response.timings.duration);
  successes.add(ok ? 1 : 0);
  errors.add(ok ? 0 : 1);
}

function addToCart(productId) {
  const body = { product_id: productId, quantity: randomQuantity() };
  // Keep the original redirect request and the separate GET /cart below.
  const res = http.post(`http://${HOST}/cart`, body);
  const accepted = res.status === 200;
  recordStage(res, accepted, httpDurationCart, httpErrorDurationCart, successCart, errorCart);
  return accepted;
}

function viewCart(productId) {
  const res = http.get(`http://${HOST}/cart`);
  const doc = parseHTML(res.body || '');
  const item = doc.find(`.cart-summary-item-row a[href$="/product/${productId}"]`);
  const quote = doc.find('.cart-summary-shipping-row .text-right').text().trim();
  const validQuote = res.status === 200 && /[0-9]/.test(quote);
  successShippingOnCart.add(validQuote ? 1 : 0);
  errorShippingOnCart.add(validQuote ? 0 : 1);

  const confirmed = validQuote && item.size() > 0;
  recordStage(res, confirmed, httpDurationViewCart, httpErrorDurationViewCart, successViewCart, errorViewCart);
  return confirmed;
}

function setCurrency() {
  const res = http.post(`http://${HOST}/setCurrency`, { currency_code: randomCurrency() });
  if (res.status === 200) {
    httpDurationCurrency.add(res.timings.duration);
  } else {
    httpErrorDurationCurrency.add(res.timings.duration);
  }
  successCurrency.add(res.status === 200 ? 1 : 0);
  errorCurrency.add(res.status !== 200 ? 1 : 0);
}

function checkout() {
  checkoutAttempts.add(1);
  const res = http.post(`http://${HOST}/cart/checkout`, creditCard);
  const httpOk = res.status === 200;
  check(res, { 'checkout is 200': () => httpOk });
  recordStage(res, httpOk, httpDurationCheckout, httpErrorDurationCheckout, successCheckout, errorCheckout);

  if (!httpOk) {
    return false;
  }
  const doc = parseHTML(res.body || '');
  const details = doc.find('.order-complete-section .border-bottom-solid .text-right');
  return Boolean(details.eq(0).text().trim() && details.eq(1).text().trim());
}

export async function handleSummary(data) {
  const metrics = {};

  for (const [metricName, metricData] of Object.entries(data.metrics)) {
    if (metricName.startsWith('custom_')) {
      const cleanName = metricName.replace('custom_', '');
      metrics[cleanName] = metricData.type === 'trend'
        ? metricData.values.med
        : metricData.values.count;
    }
  }

  metrics.session_duration = data.metrics.session_duration?.values.med;

  for (const metricName of Object.keys(metrics)) {
    if (metricName.endsWith('success')) {
      const page = metricName.replace('_success', '');
      const attempts = metrics[metricName] + (metrics[`${page}_error`] || 0);
      if (attempts > 0) metrics[`${page}_success_rate`] = metrics[metricName] / attempts;
    }
  }

  metrics.journey_started ??= 0;
  metrics.journey_completed ??= 0;
  metrics.checkout_attempted ??= 0;
  metrics.checkout_skipped ??= 0;
  if (metrics.journey_started === 0 || !data.metrics.iteration_duration) {
    throw new Error('No complete journey samples were recorded');
  }
  metrics.journey_success_rate = metrics.journey_completed / metrics.journey_started;
  if (metrics.checkout_attempted === 0) {
    // A valid context with all checkouts skipped must still be scoreable.
    // The count distinguishes this score input from failed checkout attempts.
    metrics.checkout_success_rate = 0;
  }

  for (const metricName of Object.keys(data.metrics.iteration_duration.values)) {
    Object.assign(metrics, {
      [`iteration_duration_${metricName}`]: data.metrics.iteration_duration.values[metricName],
    });
  }

  metrics.http_reqs = data.metrics.http_reqs.values.count;
  metrics.iterations = data.metrics.iterations.values.count;

  console.log(`checkout_success_rate=${metrics.checkout_success_rate}`);
  await s3.putObject(bucketName, objectKey(outputPath), JSON.stringify(metrics, null, 2));
}

function objectKey(path) {
  let key = path;
  while (key.startsWith('/')) {
    key = key.substring(1);
  }
  let prefix = s3Prefix.trim();
  while (prefix.startsWith('/')) {
    prefix = prefix.substring(1);
  }
  while (prefix.endsWith('/')) {
    prefix = prefix.substring(0, prefix.length - 1);
  }
  return prefix ? `${prefix}/${key}` : key;
}
