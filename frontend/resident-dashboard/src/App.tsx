import { Routes, Route } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import ResidentHistoryPage from "./pages/ResidentHistoryPage";
import CreateInvitationPage from "./pages/CreateInvitationPage";
import PendingInvitationsPage from "./pages/PendingInvitationsPage";
import VehicleRegistrationPage from "./pages/VehicleRegistrationPage";
import OccupancyDashboardPage from "./pages/OccupancyDashboardPage";
import CreateResidentPage from "./pages/CreateResidentPage";
import CreateCheckpointPage from "./pages/CreateCheckpointPage";
import CreatePatrolRoutePage from "./pages/CreatePatrolRoutePage";
import PatrolStatusPage from "./pages/PatrolStatusPage";
import CreateGuardPage from "./pages/CreateGuardPage";
import VehicleHistoryPage from "./pages/VehicleHistoryPage";
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
        path="/invitations/pending"
        element={
          <ProtectedRoute role="RESIDENT">
            <PendingInvitationsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/vehicles"
        element={
          <ProtectedRoute role="RESIDENT">
            <VehicleRegistrationPage />
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
      <Route
        path="/checkpoints/new"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <CreateCheckpointPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/patrol-routes/new"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <CreatePatrolRoutePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/patrol-status"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <PatrolStatusPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/guards/new"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <CreateGuardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/vehicle-history"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <VehicleHistoryPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}
