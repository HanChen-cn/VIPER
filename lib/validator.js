/**
 * URL 可用性验证
 */

const TIMEOUT_MS = 5000;

export async function validateUrl(url) {
  if (!url || (!url.startsWith('http://') && !url.startsWith('https://'))) {
    return { valid: false, latency: Infinity };
  }

  const start = Date.now();

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), TIMEOUT_MS);

    const response = await fetch(url, {
      method: 'HEAD',
      signal: controller.signal,
      mode: 'no-cors'
    });

    clearTimeout(timeoutId);
    const latency = Date.now() - start;

    if (response.type === 'opaque' || (response.status >= 200 && response.status < 400)) {
      return { valid: true, latency };
    }

    return { valid: false, latency };
  } catch {
    return { valid: false, latency: Date.now() - start };
  }
}

export async function validateBatch(episodes, concurrency = 3) {
  const results = [];
  const queue = [...episodes];

  async function worker() {
    while (queue.length > 0) {
      const ep = queue.shift();
      const validation = await validateUrl(ep.url);
      results.push({ ...ep, ...validation });
    }
  }

  const workers = Array.from({ length: Math.min(concurrency, queue.length) }, () => worker());
  await Promise.all(workers);

  return results.sort((a, b) => {
    if (a.valid !== b.valid) return a.valid ? -1 : 1;
    return a.latency - b.latency;
  });
}

export function needsParsing(url) {
  const platformPatterns = [
    /iqiyi\.com/,
    /youku\.com/,
    /v\.qq\.com/,
    /mgtv\.com/,
    /bilibili\.com/,
    /le\.com/,
    /sohu\.com/,
    /pptv\.com/
  ];
  return platformPatterns.some(p => p.test(url));
}
