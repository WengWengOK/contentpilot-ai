import { lazy, Suspense, type ReactElement } from 'react';
import { useRoutes, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { MainLayout } from '@/components/Layout/MainLayout';

const Dashboard = lazy(() => import('@/pages/Dashboard'));
const TopicPlanning = lazy(() => import('@/pages/TopicPlanning'));
const ContentCreation = lazy(() => import('@/pages/ContentCreation'));
const ImageDesign = lazy(() => import('@/pages/ImageDesign'));
const Publishing = lazy(() => import('@/pages/Publishing'));
const Analysis = lazy(() => import('@/pages/Analysis'));
const Optimization = lazy(() => import('@/pages/Optimization'));
const KnowledgeBase = lazy(() => import('@/pages/KnowledgeBase'));
const Evaluation = lazy(() => import('@/pages/Evaluation'));
const Quota = lazy(() => import('@/pages/Quota'));

const PageLoading = () => (
  <div
    style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      width: '100%',
      minHeight: '60vh',
    }}
  >
    <Spin size="large" />
  </div>
);

const withSuspense = (element: ReactElement): ReactElement => (
  <Suspense fallback={<PageLoading />}>{element}</Suspense>
);

const App = () => {
  const element = useRoutes([
    {
      path: '/',
      element: <MainLayout />,
      children: [
        { index: true, element: <Navigate to="/dashboard" replace /> },
        { path: 'dashboard', element: withSuspense(<Dashboard />) },
        { path: 'topic-planning', element: withSuspense(<TopicPlanning />) },
        { path: 'content-creation', element: withSuspense(<ContentCreation />) },
        { path: 'image-design', element: withSuspense(<ImageDesign />) },
        { path: 'publishing', element: withSuspense(<Publishing />) },
        { path: 'analysis', element: withSuspense(<Analysis />) },
        { path: 'optimization', element: withSuspense(<Optimization />) },
        { path: 'knowledge-base', element: withSuspense(<KnowledgeBase />) },
        { path: 'evaluation', element: withSuspense(<Evaluation />) },
        { path: 'quota', element: withSuspense(<Quota />) },
      ],
    },
    { path: '*', element: <Navigate to="/" replace /> },
  ]);

  return element;
};

export default App;
