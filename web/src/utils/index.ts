/**
 * 通用工具函数
 *
 * - 日期 / 时间格式化 (dayjs)
 * - 大数字中文单位格式化 (万 / 亿)
 * - 评分颜色映射
 * - JSON 文件下载
 */
import dayjs from 'dayjs';

/**
 * 格式化为日期: YYYY-MM-DD。
 *
 * @param date 日期字符串或 Date 对象
 */
export function formatDate(date: string | Date): string {
  return dayjs(date).format('YYYY-MM-DD');
}

/**
 * 格式化为日期时间: YYYY-MM-DD HH:mm:ss。
 *
 * @param date 日期字符串或 Date 对象
 */
export function formatDateTime(date: string | Date): string {
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss');
}

/**
 * 将大数字格式化为中文单位字符串 (万 / 亿)。
 *
 * - >= 1 亿: 保留两位小数 + "亿"
 * - >= 1 万: 保留两位小数 + "万"
 * - 其他: 原样返回
 *
 * @param num 待格式化的数字
 */
export function formatNumber(num: number): string {
  if (num === null || num === undefined || Number.isNaN(num)) {
    return '-';
  }

  const abs = Math.abs(num);
  const sign = num < 0 ? '-' : '';

  if (abs >= 1e8) {
    return `${sign}${(abs / 1e8).toFixed(2)}亿`;
  }
  if (abs >= 1e4) {
    return `${sign}${(abs / 1e4).toFixed(2)}万`;
  }
  return String(num);
}

/**
 * 根据评分返回对应的主题色 (用于 Tag / Progress 等组件)。
 *
 * - >= 0.8: green
 * - >= 0.6: blue
 * - >= 0.4: orange
 * - 其他:   red
 *
 * @param score 评分 (期望 0 ~ 1)
 */
export function getScoreColor(score: number): string {
  if (score >= 0.8) return 'green';
  if (score >= 0.6) return 'blue';
  if (score >= 0.4) return 'orange';
  return 'red';
}

/**
 * 将数据下载为 JSON 文件。
 *
 * @param data     任意可序列化数据
 * @param filename 文件名 (缺省后缀时自动补 `.json`)
 */
export function downloadJson(data: unknown, filename: string): void {
  const json = JSON.stringify(data, null, 2);
  const blob = new Blob([json], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = url;
  link.download = filename.endsWith('.json') ? filename : `${filename}.json`;
  link.style.display = 'none';

  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  // 释放对象 URL, 避免内存泄漏
  URL.revokeObjectURL(url);
}
