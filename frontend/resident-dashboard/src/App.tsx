import { Routes, Route } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import ResidentHistoryPage from "./pages/ResidentHistoryPage";
import CreateInvitationPage from "./pages/CreateInvitationPage";
import OccupancyDashboardPage from "./pages/OccupancyDashboardPage";
import CreateResidentPage from "./pages/CreateResidentPage";
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
      <Route
        path="/residents/new"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <CreateResidentPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}
