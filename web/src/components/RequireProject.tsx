import { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { FolderPlus } from 'lucide-react';
import { useApp } from '../lib/store';
import { Card, Empty } from './ui';
import type { Project } from '../lib/types';

export default function RequireProject({ children }: { children: (project: Project) => ReactNode }) {
  const { activeProject } = useApp();
  const navigate = useNavigate();
  const project = activeProject();
  if (!project) {
    return (
      <Card>
        <Empty
          icon={<FolderPlus size={26} />}
          title="请先选择或创建一个项目"
          hint="在右上角切换项目，或前往「项目管理」新建一个项目。"
          action={
            <button className="btn-primary" onClick={() => navigate('/projects')}>
              前往项目管理
            </button>
          }
        />
      </Card>
    );
  }
  return <>{children(project)}</>;
}
