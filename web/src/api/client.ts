/**
 * Axios 实例 & 统一请求封装
 *
 * - baseURL 取自 Vite 环境变量 `VITE_API_BASE_URL`, 默认 `/api/v1`。
 * - 请求拦截器: 自动注入 `X-Tenant-Id` 请求头 (取自 localStorage, 默认 'default')。
 * - 响应拦截器: 解包统一响应 `ApiResponse<T>`, code !== 200 抛出错误, 否则返回 `data`。
 * - 错误拦截器: 通过 antd `message.error` 提示失败信息。
 */
import axios from 'axios';
import type {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios';
import { message } from 'antd';
import type { ApiResponse } from '@/types';

/** 多租户请求头名称 (对齐后端 AiConstants.TENANT_HEADER) */
const TENANT_HEADER = 'X-Tenant-Id';

/** 从 localStorage 读取租户 ID, 缺省回退 'default' */
function getTenantId(): string {
  return localStorage.getItem('tenantId') || 'default';
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 60000,
});

/* ------------------------------ 请求拦截器 ------------------------------ */
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    config.headers.set(TENANT_HEADER, getTenantId());
    return config;
  },
  (error) => Promise.reject(error),
);

/* ------------------------------ 响应拦截器 ------------------------------ */
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const res = response.data;
    // 兼容后端直接返回非统一结构 (理论上不会发生, 防御性处理)
    if (res === undefined || res === null || typeof res.code !== 'number') {
      return res as unknown as AxiosResponse;
    }
    if (res.code !== 200) {
      const errorMessage = res.message || '请求失败';
      message.error(errorMessage);
      return Promise.reject(new Error(errorMessage));
    }
    // 解包: 拦截器实际返回业务数据 data, 此处类型断言仅为满足 axios 拦截器签名,
    // 由下方 get/post 辅助函数桥接为具体的 Promise<T>。
    return res.data as unknown as AxiosResponse;
  },
  (error) => {
    const errorMessage = resolveErrorMessage(error);
    message.error(errorMessage);
    return Promise.reject(error);
  },
);

/**
 * 从 axios 错误对象中解析出可读的提示信息。
 */
function resolveErrorMessage(error: unknown): string {
  // 已携带 message 的 Error (例如响应拦截器中 code !== 200 抛出的错误)
  if (error instanceof Error && error.message) {
    return error.message;
  }

  if (axios.isAxiosError(error)) {
    const { response, request } = error;

    if (response) {
      const { status, data } = response;
      // 后端可能以统一格式返回错误: { code, message }
      if (data && typeof data.message === 'string' && data.message) {
        return data.message;
      }
      switch (status) {
        case 400:
          return '请求参数错误';
        case 401:
          return '未授权, 请重新登录';
        case 403:
          return '拒绝访问';
        case 404:
          return '请求资源不存在';
        case 408:
          return '请求超时';
        case 500:
          return '服务器内部错误';
        case 502:
          return '网关错误';
        case 503:
          return '服务暂不可用';
        case 504:
          return '网关超时';
        default:
          return `请求失败 (${status})`;
      }
    }

    if (request) {
      return '服务器未响应, 请检查网络连接';
    }
  }

  return '网络异常, 请稍后重试';
}

/* ------------------------------ 请求辅助函数 ------------------------------ */

/**
 * 发起 GET 请求, 返回已解包的业务数据。
 *
 * @param url    请求路径 (相对 baseURL)
 * @param config axios 配置 (可含 params)
 * @returns 已解包的 `data`
 */
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, config) as unknown as Promise<T>;
}

/**
 * 发起 POST 请求, 返回已解包的业务数据。
 *
 * @param url    请求路径 (相对 baseURL)
 * @param data   请求体
 * @param config axios 配置
 * @returns 已解包的 `data`
 */
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>;
}

/** 默认导出实例, 便于需要时直接使用原始 axios 能力 */
export default instance;
