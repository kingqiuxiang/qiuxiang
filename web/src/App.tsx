import { useEffect } from 'react';
import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Toasts from './components/Toasts';
import { useApp } from './lib/store';
import Dashboard from './pages/Dashboard';
import Interfaces from './pages/Interfaces';
import Workbench from './pages/Workbench';
import Runner from './pages/Runner';
import PageTest from './pages/PageTest';
import History from './pages/History';
import Projects from './pages/Projects';

export default function App() {
  const { loadProjects, loadDefaults } = useApp();
  useEffect(() => {
    loadDefaults();
    loadProjects();
  }, []);

  return (
    <>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/interfaces" element={<Interfaces />} />
          <Route path="/workbench" element={<Workbench />} />
          <Route path="/runner" element={<Runner />} />
          <Route path="/pagetest" element={<PageTest />} />
          <Route path="/history" element={<History />} />
          <Route path="/projects" element={<Projects />} />
        </Routes>
      </Layout>
      <Toasts />
    </>
  );
}
