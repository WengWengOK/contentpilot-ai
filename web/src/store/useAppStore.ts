/**
 * 全局应用状态 (Zustand)
 *
 * 维护租户 ID 与侧边栏折叠状态, 租户 ID 变更时同步持久化到 localStorage。
 */
import { create } from 'zustand';
import { DEFAULT_TENANT_ID, TENANT_STORAGE_KEY } from '@/constants';

interface AppState {
  /** 当前租户 ID (初始化时从 localStorage 读取, 缺省 'default') */
  tenantId: string;
  /** 设置租户 ID, 同时持久化到 localStorage */
  setTenantId: (id: string) => void;
  /** 侧边栏是否折叠 */
  collapsed: boolean;
  /** 切换侧边栏折叠状态 */
  toggleCollapsed: () => void;
}

/**
 * 读取已持久化的租户 ID, 不存在时回退默认值。
 */
function loadTenantId(): string {
  return localStorage.getItem(TENANT_STORAGE_KEY) || DEFAULT_TENANT_ID;
}

export const useAppStore = create<AppState>((set) => ({
  tenantId: loadTenantId(),
  setTenantId: (id: string) => {
    localStorage.setItem(TENANT_STORAGE_KEY, id);
    set({ tenantId: id });
  },
  collapsed: false,
  toggleCollapsed: () => set((state) => ({ collapsed: !state.collapsed })),
}));

export default useAppStore;
