export type Role = "RESIDENT" | "GUARD" | "SUPERVISOR" | "PROPERTY_MANAGER" | "CLIENT" | "ADMIN";

export interface AuthResponse {
  token: string;
  userId: number;
  fullName: string;
  roles: Role[];
}

export type VisitorCategory = "VISITOR" | "CONTRACTOR" | "DELIVERY" | "STAFF";
export type ApprovalStatus = "AUTO_APPROVED" | "RESIDENT_APPROVED" | "DENIED" | "PENDING";

export interface VisitorEntryResponse {
  id: number;
  propertyId: number;
  unitId: number | null;
  invitationId: number | null;
  visitorName: string;
  visitorPhone: string | null;
  category: VisitorCategory;
  approvalStatus: ApprovalStatus;
  processedByGuardId: number;
  vehicleRegistration: string | null;
  vehicleRecognized: boolean;
  enteredAt: string;
  exitedAt: string | null;
  exitProcessedByGuardId: number | null;
  notes: string | null;
  createdAt: string;
}

export interface VisitorCheckInResponse {
  id: number;
  propertyId: number;
  visitorName: string;
  category: VisitorCategory;
  visitingResidentName: string | null;
  vehicleRegistration: string | null;
  vehicleRecognized: boolean;
  recognizedVehicleOwnerName: string | null;
  enteredAt: string;
}

export interface VisitorWalkInResponse {
  id: number;
  propertyId: number;
  visitorName: string;
  category: VisitorCategory;
  unitId: number | null;
  visitingResidentNames: string | null;
  vehicleRegistration: string | null;
  vehicleRecognized: boolean;
  recognizedVehicleOwnerName: string | null;
  enteredAt: string;
  notes: string | null;
}

export interface UnitResponse {
  id: number;
  propertyId: number;
  unitNumber: string;
  createdAt: string;
}

export interface OccupancyResponse {
  propertyId: number;
  totalOnSite: number;
  byCategory: Record<VisitorCategory, VisitorEntryResponse[]>;
}

export interface ShiftResponse {
  id: number;
  guardId: number;
  propertyId: number;
  propertyName: string;
  clockInAt: string;
  clockInLatitude: number;
  clockInLongitude: number;
  clockInDistanceMeters: number | null;
  clockInWithinTolerance: boolean | null;
  clockOutAt: string | null;
  clockOutLatitude: number | null;
  clockOutLongitude: number | null;
  clockOutDistanceMeters: number | null;
  clockOutWithinTolerance: boolean | null;
  createdAt: string;
}

export interface CheckpointScanResponse {
  id: number;
  checkpointId: number;
  checkpointName: string;
  shiftId: number;
  guardId: number;
  scannedAt: string;
  latitude: number;
  longitude: number;
  distanceMeters: number | null;
  withinTolerance: boolean | null;
  createdAt: string;
}

export type IncidentSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type IncidentStatus = "OPEN" | "INVESTIGATING" | "RESOLVED";

export interface IncidentMediaResponse {
  id: number;
  originalFilename: string;
  contentType: string;
  fileSizeBytes: number;
  createdAt: string;
}

export interface IncidentResponse {
  id: number;
  propertyId: number;
  reportedByGuardId: number;
  reportedByGuardName: string;
  shiftId: number;
  description: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  latitude: number;
  longitude: number;
  reportedAt: string;
  media: IncidentMediaResponse[];
  createdAt: string;
}

export interface ApiErrorBody {
  status: number;
  error: string;
  timestamp?: string;
}
