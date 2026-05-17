/**
 * CMS 采集站 API 源定义
 * 标准格式: {baseUrl}/api.php/provide/vod/?ac=detail&wd={keyword}
 */

export const API_SOURCES = [
  {
    id: 'heimuer',
    name: '黑木耳资源',
    baseUrl: 'https://json.heimuer.xyz',
    searchPath: '/api.php/provide/vod/',
    params: { ac: 'detail' },
    searchParam: 'wd',
    enabled: true
  },
  {
    id: 'ffzy',
    name: '非凡资源',
    baseUrl: 'https://cj.ffzyapi.com',
    searchPath: '/api.php/provide/vod/',
    params: { ac: 'detail' },
    searchParam: 'wd',
    enabled: true
  },
  {
    id: 'hongniu',
    name: '红牛资源',
    baseUrl: 'https://www.hongniuzy2.com',
    searchPath: '/api.php/provide/vod/',
    params: { ac: 'detail' },
    searchParam: 'wd',
    enabled: true
  },
  {
    id: 'wolong',
    name: '卧龙资源',
    baseUrl: 'https://collect.wolongzyw.com',
    searchPath: '/api.php/provide/vod/',
    params: { ac: 'detail' },
    searchParam: 'wd',
    enabled: true
  },
  {
    id: 'jinying',
    name: '金鹰资源',
    baseUrl: 'https://jyzyapi.com',
    searchPath: '/api.php/provide/vod/',
    params: { ac: 'detail' },
    searchParam: 'wd',
    enabled: true
  }
];

/**
 * 内置解析接口（35 个）
 * 策略：popup 打开时全量并发检测可用性，自动选最快的
 */
export const PARSE_APIS = [
  // 移动端+PC 通用
  { id: 'im1907', name: '纯净1', url: 'https://im1907.top/?jx=', mobile: true },
  { id: 'jsonplayer', name: 'B站1', url: 'https://jx.jsonplayer.com/player/?url=', mobile: true },
  { id: 'aidouer', name: '爱豆', url: 'https://jx.aidouer.net/?url=', mobile: true },
  { id: 'chok', name: 'CHok', url: 'https://www.gai4.com/?url=', mobile: true },
  { id: 'okjx', name: 'OK', url: 'https://okjx.cc/?url=', mobile: true },
  { id: 'rdhk', name: 'RDHK', url: 'https://jx.rdhk.net/?v=', mobile: true },
  { id: 'rrm', name: '人人迷', url: 'https://jx.blbo.cc:4433/?url=', mobile: true },
  { id: 'sigu3', name: '思古3', url: 'https://jsap.attakids.com/?url=', mobile: true },
  { id: 'tingle', name: '听乐', url: 'https://jx.dj6u.com/?url=', mobile: true },
  { id: 'yt', name: 'YT', url: 'https://jx.yangtu.top/?url=', mobile: true },
  { id: '4kdv', name: '4K', url: 'https://jx.4kdv.com/?url=', mobile: true },
  // PC 专用
  { id: 'bljiex', name: 'BL', url: 'https://vip.bljiex.com/?v=', mobile: false },
  { id: 'bingdou', name: '冰豆', url: 'https://api.qianqi.net/vip/?url=', mobile: false },
  { id: 'baiyu', name: '百域', url: 'https://jx.618g.com/?url=', mobile: false },
  { id: 'ckplayer', name: 'CK', url: 'https://www.ckplayer.vip/jiexi/?url=', mobile: false },
  { id: 'ckmov', name: 'ckmov', url: 'https://www.ckmov.vip/api.php?url=', mobile: false },
  { id: 'h8jx', name: 'H8', url: 'https://www.h8jx.com/jiexi.php?url=', mobile: false },
  { id: 'playerjy', name: 'JY', url: 'https://jx.playerjy.com/?url=', mobile: false },
  { id: 'jiexila', name: '解析la', url: 'https://api.jiexi.la/?url=', mobile: false },
  { id: 'laobandq', name: '老板', url: 'https://vip.laobandq.com/jiexi.php?url=', mobile: false },
  { id: 'mtosz', name: 'MAO', url: 'https://www.mtosz.com/m3u8.php?url=', mobile: false },
  { id: 'm3u8tv', name: 'M3U8', url: 'https://jx.m3u8.tv/jiexi/?url=', mobile: false },
  { id: 'nxflv', name: '诺讯', url: 'https://www.nxflv.com/?url=', mobile: false },
  { id: 'playm3u8', name: 'PM', url: 'https://www.playm3u8.cn/jiexi.php?url=', mobile: false },
  { id: 'pangujiexi', name: '盘古', url: 'https://www.pangujiexi.cc/jiexi.php?url=', mobile: false },
  { id: 'qige', name: '七哥', url: 'https://jx.nnxv.cn/tv.php?url=', mobile: false },
  { id: 'siyun', name: '思云', url: 'https://jx.ap2p.cn/?url=', mobile: false },
  { id: 'weido', name: '维多', url: 'https://jx.ivito.cn/?url=', mobile: false },
  { id: 'xmflv', name: '虾米', url: 'https://jx.xmflv.com/?url=', mobile: false },
  { id: 'yunduan', name: '云端', url: 'https://sb.5gseo.net/?url=', mobile: false },
  { id: 'yparse', name: '云析', url: 'https://jx.yparse.com/index.php?url=', mobile: false },
  { id: 'yh0523', name: '0523', url: 'https://go.yh0523.cn/y.cy?url=', mobile: false },
  { id: '1717yun', name: '17云', url: 'https://www.1717yun.com/jx/ty.php?url=', mobile: false },
  { id: '000180', name: '180', url: 'https://jx.000180.top/jx/?url=', mobile: false },
  { id: '8090g', name: '8090', url: 'https://www.8090g.cn/?url=', mobile: false }
];

export function buildSearchUrl(source, keyword) {
  const params = new URLSearchParams({ ...source.params, [source.searchParam]: keyword });
  return `${source.baseUrl}${source.searchPath}?${params.toString()}`;
}

/**
 * 预热：全量并发检测所有解析接口可用性
 * popup 打开时立即调用，在用户输入时完成检测
 */
export async function warmupParseApis() {
  const testUrl = 'https://v.qq.com/x/page/test.html';

  const results = await Promise.allSettled(
    PARSE_APIS.map(async (api) => {
      const start = Date.now();
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 4000);
        await fetch(api.url + testUrl, { method: 'HEAD', mode: 'no-cors', signal: controller.signal });
        clearTimeout(timeoutId);
        return { ...api, latency: Date.now() - start, available: true };
      } catch {
        return { ...api, latency: Infinity, available: false };
      }
    })
  );

  return results
    .map(r => r.status === 'fulfilled' ? r.value : null)
    .filter(r => r && r.available)
    .sort((a, b) => a.latency - b.latency);
}

/**
 * 从已排序的可用接口列表中获取最佳接口
 */
export function pickBestApi(rankedApis) {
  if (rankedApis && rankedApis.length > 0) {
    return rankedApis[0];
  }
  return PARSE_APIS[0];
}
