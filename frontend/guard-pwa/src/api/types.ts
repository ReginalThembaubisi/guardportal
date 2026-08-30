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

/** Today-only slice of the incident-investigation history endpoint — the backend enforces "today, own property" regardless of what's requested. */
export interface VisitorHistoryEntryResponse {
  id: number;
  visitorName: string;
  visitorPhone: string | null;
  unitId: number | null;
  unitNumber: string | null;
  category: VisitorCategory;
  approvalStatus: ApprovalStatus;
  vehicleRegistration: string | null;
  enteredAt: string;
  exitedAt: string | null;
  notes: string | null;
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

export interface VisitorCheckOutResponse {
  id: number;
  visitorName: string;
  visitingResidentNames: string | null;
  vehicleRegistration: string | null;
  vehicleRecognized: boolean;
  recognizedVehicleOwnerName: string | null;
  enteredAt: string;
  exitedAt: string;
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

export type ShiftType = "DAY" | "NIGHT";

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
  shiftType: ShiftType | null;
  createdAt: string;
}

/** A planned/rostered shift, uploaded by a Supervisor — replaces checking a WhatsApp group. */
export interface ShiftScheduleResponse {
  id: number;
  guardId: number;
  guardName: string;
  propertyId: number;
  propertyName: string;
  shiftDate: string;
  shiftType: ShiftType;
  startTime: string | null;
  endTime: string | null;
  createdAt: string;
}

/** For the checkpoint picker — a guard checks in by picking one of these by name, no QR scan involved. */
export interface CheckpointResponse {
  id: number;
  propertyId: number;
  name: string;
  latitude: number;
  longitude: number;
  geoToleranceMeters: number | null;
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

export interface PatrolRouteCheckpointStop {
  checkpointId: number;
  name: string;
  sequenceOrder: number;
}

export interface PatrolRouteResponse {
  id: number;
  propertyId: number;
  name: string;
  checkpoints: PatrolRouteCheckpointStop[];
  createdAt: string;
}

export interface MissedCheckpointStatus {
  checkpointId: number;
  name: string;
  sequenceOrder: number;
  scanned: boolean;
  scanCount: number;
  firstScanAt: string | null;
  lastScanAt: string | null;
  distanceMeters: number | null;
  withinTolerance: boolean | null;
}

export interface MissedCheckpointResponse {
  routeId: number;
  routeName: string;
  from: string;
  to: string;
  checkpoints: MissedCheckpointStatus[];
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
