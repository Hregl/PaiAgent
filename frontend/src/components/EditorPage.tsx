import { useEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import { useWorkflowStore } from '../store/workflowStore';
import { useDebugStore } from '../store/debugStore';
import TopBar from './TopBar/TopBar';
import Sidebar from './Sidebar/Sidebar';
import WorkflowCanvas from './Canvas/WorkflowCanvas';
import ConfigPanel from './ConfigPanel/ConfigPanel';
import DebugDrawer from './DebugDrawer/DebugDrawer';

export default function EditorPage() {
  const checkAuth = useAuthStore((s) => s.checkAuth);
  const selectedNodeId = useWorkflowStore((s) => s.selectedNodeId);
  const isDebugOpen = useDebugStore((s) => s.isOpen);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  return (
    <div className="app-container">
      <TopBar />
      <div className="main-layout">
        <div className="sidebar-container">
          <Sidebar />
        </div>
        <div className="canvas-container">
          <WorkflowCanvas />
        </div>
        {selectedNodeId && (
          <div className="config-panel-container">
            <ConfigPanel />
          </div>
        )}
      </div>
      {isDebugOpen && <DebugDrawer />}
    </div>
  );
}
