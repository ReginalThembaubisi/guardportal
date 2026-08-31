import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/ProtectedRoute";
import GatePage from "./pages/GatePage";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import MePage from "./pages/MePage";
import PatrolPage from "./pages/PatrolPage";
import QueuePage from "./pages/QueuePage";
import ReportPage from "./pages/ReportPage";
import WalkInPage from "./pages/WalkInPage";
import VehicleHistoryPage from "./pages/VehicleHistoryPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        path="/home"
        element={
          <ProtectedRoute>
            <HomePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/gate"
        element={
          <ProtectedRoute>
            <GatePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/patrol"
        element={
          <ProtectedRoute>
            <PatrolPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/report"
        element={
          <ProtectedRoute>
            <ReportPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/me"
        element={
          <ProtectedRoute>
            <MePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/queue"
        element={
          <ProtectedRoute>
            <QueuePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/walk-in"
        element={
          <ProtectedRoute>
            <WalkInPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/vehicle-history"
        element={
          <ProtectedRoute>
            <VehicleHistoryPage />
          </ProtectedRoute>
        }
      />

      {/* Old routes, kept resolving for any bookmarked or in-flight link. */}
      <Route path="/clock" element={<Navigate to="/home" replace />} />
      <Route path="/checkin" element={<Navigate to="/gate?segment=checkin" replace />} />
      <Route path="/occupancy" element={<Navigate to="/gate?segment=onsite" replace />} />
      <Route path="/scan" element={<Navigate to="/patrol" replace />} />
      <Route path="/report-incident" element={<Navigate to="/report" replace />} />
      <Route path="/shifts" element={<Navigate to="/me" replace />} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
