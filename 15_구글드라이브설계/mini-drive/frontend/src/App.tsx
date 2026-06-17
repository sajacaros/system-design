import { Routes, Route, Navigate } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { RequireAuth } from "@/components/layout/RequireAuth";
import LoginPage from "@/pages/LoginPage";
import SignupPage from "@/pages/SignupPage";
import ExplorerPage from "@/pages/ExplorerPage";
import TrashPage from "@/pages/TrashPage";
import SharesPage from "@/pages/SharesPage";
import PublicSharePage from "@/pages/PublicSharePage";

export default function App() {
  return (
    <Routes>
      {/* 공개 라우트 */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/share/:token" element={<PublicSharePage />} />

      {/* 인증 가드 라우트 */}
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<ExplorerPage />} />
        <Route path="/shares" element={<SharesPage />} />
        <Route path="/trash" element={<TrashPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
