import { API_SOURCES, PARSE_APIS, buildSearchUrl, pickBestApi } from './api-sources.js';
import { parseApiResponse, mergeResults, deduplicateEpisodes } from './parser.js';
import { validateBatch, needsParsing } from './validator.js';

let rankedApis = null;

export function setWarmupResult(apis) {
  rankedApis = apis;
}

/**
 * 执行搜索：并发请求所有启用的 API 源，合并去重，附加解析链接
 * 每集返回 playUrl（最佳源）+ altUrls（备选源列表），解析失败时可切换
 */
export async function search(keyword) {
  if (!keyword || keyword.trim().length === 0) {
    return [];
  }

  const enabledSources = API_SOURCES.filter(s => s.enabled);
  const bestApi = pickBestApi(rankedApis);
  const availableApis = rankedApis && rankedApis.length > 0 ? rankedApis : PARSE_APIS.slice(0, 5);

  const searchResults = await Promise.allSettled(
    enabledSources.map(source => fetchSource(source, keyword))
  );

  const allResults = [];
  for (const result of searchResults) {
    if (result.status === 'fulfilled' && result.value.length > 0) {
      allResults.push(result.value);
    }
  }

  if (allResults.length === 0) {
    return [];
  }

  const merged = mergeResults(allResults);

  for (const item of merged) {
    item.episodes = deduplicateEpisodes(item.episodes);

    item.episodes = item.episodes.map(ep => {
      const encodedUrl = encodeURIComponent(ep.url);
      const altUrls = availableApis.slice(0, 8).map(api => ({
        name: api.name,
        url: api.url + encodedUrl
      }));
      if (needsParsing(ep.url)) {
        return { ...ep, playUrl: bestApi.url + encodedUrl, altUrls };
      }
      return { ...ep, playUrl: ep.url, altUrls };
    });
  }

  return merged;
}

async function fetchSource(source, keyword) {
  const url = buildSearchUrl(source, keyword);

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 8000);

    const response = await fetch(url, {
      signal: controller.signal,
      headers: { 'Accept': 'application/json' }
    });

    clearTimeout(timeoutId);

    if (!response.ok) return [];

    const data = await response.json();
    return parseApiResponse(data, source.id);
  } catch (err) {
    console.warn(`[${source.id}] 搜索失败:`, err.message);
    return [];
  }
}

export async function validateEpisodes(episodes) {
  const toValidate = episodes.map(ep => ({ ...ep, url: ep.playUrl }));
  return validateBatch(toValidate, 3);
}

/**
 * 查询同一集在不同源的播放链接
 * 搜索剧名 → 找到同名剧 → 提取同名集的所有源
 */
export async function getAltSources(showName, episodeName, currentUrl) {
  if (!showName) return [];

  const enabledSources = API_SOURCES.filter(s => s.enabled);

  const searchResults = await Promise.allSettled(
    enabledSources.map(source => fetchSource(source, showName))
  );

  const allResults = [];
  for (const result of searchResults) {
    if (result.status === 'fulfilled' && result.value.length > 0) {
      allResults.push(result.value);
    }
  }

  if (allResults.length === 0) return [];

  const merged = mergeResults(allResults);
  const show = merged.find(item => item.name.trim() === showName.trim());
  if (!show) return [];

  const matched = show.episodes.filter(ep =>
    ep.name.trim() === episodeName.trim() && ep.url !== currentUrl
  );

  const seen = new Set();
  const altSources = [];
  for (const ep of matched) {
    if (seen.has(ep.url)) continue;
    seen.add(ep.url);
    altSources.push({
      name: ep.source || '未知源',
      url: ep.url,
      sourceId: ep.sourceId
    });
  }

  return altSources;
}

/**
 * 获取剧集列表（轻量搜索，只返回匹配剧名的剧集列表）
 */
export async function getEpisodeList(showName) {
  if (!showName) return [];

  const enabledSources = API_SOURCES.filter(s => s.enabled);

  const searchResults = await Promise.allSettled(
    enabledSources.map(source => fetchSource(source, showName))
  );

  const allResults = [];
  for (const result of searchResults) {
    if (result.status === 'fulfilled' && result.value.length > 0) {
      allResults.push(result.value);
    }
  }

  if (allResults.length === 0) return [];

  const merged = mergeResults(allResults);
  const show = merged.find(item => item.name.trim() === showName.trim());
  if (!show) return [];

  const bestApi = pickBestApi(rankedApis);

  return deduplicateEpisodes(show.episodes).map(ep => {
    if (needsParsing(ep.url)) {
      return { name: ep.name, playUrl: bestApi.url + encodeURIComponent(ep.url) };
    }
    return { name: ep.name, playUrl: ep.url };
  });
}
