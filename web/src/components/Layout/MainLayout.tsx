/**
 * 全局布局: 侧边栏导航 + 顶部租户切换 + 内容出口.
 *
 * - Sider: 可折叠侧边栏, 折叠状态由 useAppStore 管理; Logo 展示 "ContentPilot AI" / "CP".
 * - Header: 折叠按钮 + 租户输入框 + GitHub 链接.
 * - Content: 使用 <Outlet /> 渲染子路由, 内部留白.
 *
 * 菜单项由 MENU_ITEMS 常量驱动, useNavigate 负责路由跳转, useLocation 确定选中态.
 */
import { useMemo, type ReactNode } from 'react';
import { Layout, Menu, Input, Button, Space, Typography, Tooltip, Badge, theme } from 'antd';
import type { MenuProps } from 'antd';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  GithubOutlined,
  DashboardOutlined,
  BulbOutlined,
  EditOutlined,
  PictureOutlined,
  CloudUploadOutlined,
  BarChartOutlined,
  RocketOutlined,
  DatabaseOutlined,
  SafetyCertificateOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { MENU_ITEMS } from '@/constants';
import { useAppStore } from '@/store/useAppStore';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

/** 菜单 icon 字符串 -> 图标组件. */
const ICON_MAP: Record<string, ReactNode> = {
  dashboard: <DashboardOutlined />,
  bulb: <BulbOutlined />,
  edit: <EditOutlined />,
  picture: <PictureOutlined />,
  'cloud-upload': <CloudUploadOutlined />,
  'bar-chart': <BarChartOutlined />,
  rocket: <RocketOutlined />,
  database: <DatabaseOutlined />,
  'safety-certificate': <SafetyCertificateOutlined />,
  wallet: <WalletOutlined />,
};

/** GitHub 仓库地址. */
const GITHUB_URL = 'https://github.com/contentops/contentpilot-ai';

export function MainLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { tenantId, setTenantId, collapsed, toggleCollapsed } = useAppStore();
  const {
    token: { colorBgContainer },
  } = theme.useToken();

  /** 构建菜单 items. */
  const items: MenuProps['items'] = useMemo(
    () =>
      MENU_ITEMS.map((item) => ({
        key: item.key,
        icon: ICON_MAP[item.icon] ?? null,
        label: item.label,
      })),
    [],
  );

  /** 根据当前路由确定选中的菜单 key. */
  const selectedKey = useMemo(() => {
    const current = location.pathname === '/' ? '/dashboard' : location.pathname;
    const matched = MENU_ITEMS.find(
      (item) => current === item.path || current.startsWith(`${item.path}/`),
    );
    return matched?.key ?? 'dashboard';
  }, [location.pathname]);

  /** 菜单点击跳转. */
  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    const item = MENU_ITEMS.find((i) => i.key === key);
    if (item) navigate(item.path);
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={toggleCollapsed}
        trigger={null}
        width={220}
      >
        {/* Logo 区域 */}
        <div
          style={{
            height: 56,
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            cursor: 'pointer',
          }}
          onClick={() => navigate('/dashboard')}
        >
          <img
            src="/logo.jpg"
            alt="ContentPilot AI"
            style={{
              width: collapsed ? 32 : 36,
              height: collapsed ? 32 : 36,
              borderRadius: 8,
              objectFit: 'cover',
              flexShrink: 0,
              transition: 'width 0.2s, height 0.2s',
            }}
          />
          {!collapsed && (
            <span style={{ fontWeight: 700, fontSize: 15, letterSpacing: 0.5 }}>
              ContentPilot AI
            </span>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={items}
          onClick={onMenuClick}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: colorBgContainer,
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0, 21, 41, 0.08)',
          }}
        >
          <Space size="middle" align="center">
            {/* 折叠 / 展开按钮 */}
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={toggleCollapsed}
              style={{ fontSize: 16, width: 40, height: 40 }}
            />
            <Title level={5} style={{ margin: 0 }}>
              企业级 AI 内容运营平台
            </Title>
          </Space>

          <Space size="middle" align="center">
            {/* 租户选择器 */}
            <Tooltip title="切换租户 ID (回车生效)">
              <Input
                defaultValue={tenantId}
                onPressEnter={(e) => setTenantId((e.target as HTMLInputElement).value)}
                placeholder="租户 ID"
                style={{ width: 160 }}
                prefix={<Badge status="success" />}
              />
            </Tooltip>

            {/* GitHub 链接 */}
            <Tooltip title="GitHub 仓库">
              <Button
                type="text"
                icon={<GithubOutlined style={{ fontSize: 18 }} />}
                href={GITHUB_URL}
                target="_blank"
                rel="noopener noreferrer"
                style={{ width: 40, height: 40 }}
              />
            </Tooltip>
          </Space>
        </Header>

        <Content style={{ padding: 24, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

export default MainLayout;
