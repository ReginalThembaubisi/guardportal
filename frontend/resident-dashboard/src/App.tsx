import { Routes, Route } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import ResidentHistoryPage from "./pages/ResidentHistoryPage";
import CreateInvitationPage from "./pages/CreateInvitationPage";
import OccupancyDashboardPage from "./pages/OccupancyDashboardPage";
import ProtectedRoute from "./components/ProtectedRoute";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        path="/history"
        element={
          <ProtectedRoute role="RESIDENT">
            <ResidentHistoryPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/invite"
        element={
          <ProtectedRoute role="RESIDENT">
            <CreateInvitationPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <OccupancyDashboardPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}
