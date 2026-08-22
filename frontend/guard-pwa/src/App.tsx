import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/ProtectedRoute";
import CheckInPage from "./pages/CheckInPage";
import LoginPage from "./pages/LoginPage";
import OccupancyPage from "./pages/OccupancyPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        path="/checkin"
        element={
          <ProtectedRoute>
            <CheckInPage />
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
