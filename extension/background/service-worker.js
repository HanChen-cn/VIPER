import { search, validateEpisodes, setWarmupResult, getAltSources, getEpisodeList } from '../lib/searcher.js';
import { warmupParseApis } from '../lib/api-sources.js';
import {
  getSearchHistory, addSearchHistory, clearSearchHistory,
  getPlayHistory, addPlayHistory,
  getFavorites, addFavorite, removeFavorite
} from '../lib/storage.js';

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
    const playerData = {
      url: message.url,
      name: message.name || '',
      episode: message.episode || ''
    };
    chrome.storage.session.set({ playerData }, () => {
      const playerUrl = chrome.runtime.getURL('player/player.html');
      chrome.tabs.create({ url: playerUrl, active: true });
    });
    if (message.name && message.episode) {
      addPlayHistory({ name: message.name, episode: message.episode, url: message.url });
    }
    sendResponse({ success: true });
    return false;
  }

  if (message.type === 'getSearchHistory') {
    getSearchHistory().then(sendResponse);
    return true;
  }
  if (message.type === 'clearSearchHistory') {
    clearSearchHistory().then(() => sendResponse({ success: true }));
    return true;
  }
  if (message.type === 'getPlayHistory') {
    getPlayHistory().then(sendResponse);
    return true;
  }
  if (message.type === 'updatePlayHistory') {
    if (message.name && message.episode) {
      addPlayHistory({ name: message.name, episode: message.episode, url: message.url });
    }
    sendResponse({ success: true });
    return false;
  }
  if (message.type === 'getFavorites') {
    getFavorites().then(sendResponse);
    return true;
  }
  if (message.type === 'addFavorite') {
    addFavorite(message.item).then(ok => sendResponse({ success: ok }));
    return true;
  }
  if (message.type === 'removeFavorite') {
    removeFavorite(message.name).then(() => sendResponse({ success: true }));
    return true;
  }
  if (message.type === 'getAltSources') {
    getAltSources(message.showName, message.episode, message.currentUrl)
      .then(altSources => sendResponse({ success: true, data: altSources }))
      .catch(err => sendResponse({ success: false, error: err.message }));
    return true;
  }
  if (message.type === 'getEpisodeList') {
    getEpisodeList(message.showName)
      .then(episodes => sendResponse({ success: true, data: episodes }))
      .catch(err => sendResponse({ success: false, error: err.message }));
    return true;
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
    if (results.length > 0) {
      addSearchHistory(keyword);
    }
    return { success: true, data: results };
  } catch (err) {
    return { success: false, error: err.message };
  }
}
