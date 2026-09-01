import { Routes, Route } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import ResidentHomePage from "./pages/ResidentHomePage";
import ResidentHistoryPage from "./pages/ResidentHistoryPage";
import CreateInvitationPage from "./pages/CreateInvitationPage";
import PendingInvitationsPage from "./pages/PendingInvitationsPage";
import VehicleRegistrationPage from "./pages/VehicleRegistrationPage";
import OccupancyDashboardPage from "./pages/OccupancyDashboardPage";
import ResidentsPage from "./pages/ResidentsPage";
import CreateCheckpointPage from "./pages/CreateCheckpointPage";
import CreatePatrolRoutePage from "./pages/CreatePatrolRoutePage";
import PatrolStatusPage from "./pages/PatrolStatusPage";
import CreateGuardPage from "./pages/CreateGuardPage";
import ShiftSchedulePage from "./pages/ShiftSchedulePage";
import ShiftListPage from "./pages/ShiftListPage";
import VehicleHistoryPage from "./pages/VehicleHistoryPage";
import IncidentsPage from "./pages/IncidentsPage";
import VisitorHistoryPage from "./pages/VisitorHistoryPage";
import AdminPropertiesPage from "./pages/admin/AdminPropertiesPage";
import AdminUnitsPage from "./pages/admin/AdminUnitsPage";
import AdminStaffPage from "./pages/admin/AdminStaffPage";
import AdminLinkStaffPage from "./pages/admin/AdminLinkStaffPage";
import AdminAuditPage from "./pages/admin/AdminAuditPage";
import ProtectedRoute from "./components/ProtectedRoute";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        path="/home"
        element={
          <ProtectedRoute role="RESIDENT">
            <ResidentHomePage />
          </ProtectedRoute>
        }
      />
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
        path="/residents"
        element={
          <ProtectedRoute role="PROPERTY_MANAGER">
            <ResidentsPage />
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
          <ProtectedRoute role="SUPERVISOR">
            <CreateGuardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/shift-schedule"
        element={
          <ProtectedRoute role="SUPERVISOR">
            <ShiftSchedulePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/shifts"
        element={
          <ProtectedRoute role={["SUPERVISOR", "PROPERTY_MANAGER", "ADMIN"]}>
            <ShiftListPage />
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
      <Route
        path="/incidents"
        element={
          <ProtectedRoute role={["PROPERTY_MANAGER", "SUPERVISOR"]}>
            <IncidentsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/visitor-history"
        element={
          <ProtectedRoute role={["PROPERTY_MANAGER", "SUPERVISOR"]}>
            <VisitorHistoryPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/properties"
        element={
          <ProtectedRoute role="ADMIN">
            <AdminPropertiesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/units"
        element={
          <ProtectedRoute role="ADMIN">
            <AdminUnitsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/staff"
        element={
          <ProtectedRoute role="ADMIN">
            <AdminStaffPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/link-staff"
        element={
          <ProtectedRoute role="ADMIN">
            <AdminLinkStaffPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/audit"
        element={
          <ProtectedRoute role="ADMIN">
            <AdminAuditPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}
