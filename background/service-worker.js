import { search, validateEpisodes, setWarmupResult } from '../lib/searcher.js';
import { warmupParseApis } from '../lib/api-sources.js';

let warmupPromise = null;
let warmupDone = false;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'warmup') {
    handleWarmup().then(sendResponse);
    return true;
  }

  if (message.type === 'search') {
    handleSearch(message.keyword).then(sendResponse);
    return true;
  }

  if (message.type === 'validate') {
    validateEpisodes(message.episodes).then(sendResponse);
    return true;
  }

  if (message.type === 'play') {
    chrome.tabs.create({ url: message.url, active: true });
    sendResponse({ success: true });
    return false;
  }
});

async function handleWarmup() {
  if (warmupDone && warmupPromise) {
    return { success: true, message: '已预热' };
  }
  try {
    warmupPromise = warmupParseApis();
    const apis = await warmupPromise;
    setWarmupResult(apis);
    warmupDone = true;
    return {
      success: true,
      available: apis.length,
      fastest: apis[0] ? `${apis[0].name} (${apis[0].latency}ms)` : 'none'
    };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

async function handleSearch(keyword) {
  if (warmupPromise && !warmupDone) {
    await warmupPromise;
  }
  try {
    const results = await search(keyword);
    return { success: true, data: results };
  } catch (err) {
    return { success: false, error: err.message };
  }
}
