import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/ProtectedRoute";
import CheckInPage from "./pages/CheckInPage";
import CheckpointScanPage from "./pages/CheckpointScanPage";
import ClockPage from "./pages/ClockPage";
import LoginPage from "./pages/LoginPage";
import OccupancyPage from "./pages/OccupancyPage";
import WalkInPage from "./pages/WalkInPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        path="/clock"
        element={
          <ProtectedRoute>
            <ClockPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/checkin"
        element={
          <ProtectedRoute>
            <CheckInPage />
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
        path="/scan"
        element={
          <ProtectedRoute>
            <CheckpointScanPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/occupancy"
        element={
          <ProtectedRoute>
            <OccupancyPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
