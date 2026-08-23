export type Role = "RESIDENT" | "GUARD" | "SUPERVISOR" | "PROPERTY_MANAGER" | "CLIENT" | "ADMIN";

export interface AuthResponse {
  token: string;
  userId: number;
  fullName: string;
  roles: Role[];
}

export interface OtpRequestResponse {
  message: string;
  expiresInMinutes: number;
  devOnlyCode: string | null;
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

export interface OccupancyResponse {
  propertyId: number;
  totalOnSite: number;
  byCategory: Record<VisitorCategory, VisitorEntryResponse[]>;
}

export type InvitationStatus = "PENDING" | "USED" | "EXPIRED" | "CANCELLED";

export interface InvitationResponse {
  id: number;
  visitorName: string;
  visitorPhone: string | null;
  expectedVehicleReg: string | null;
  purpose: string | null;
  validFrom: string;
  validUntil: string;
  status: InvitationStatus;
  qrToken: string;
  checkInUrl: string;
  qrCodeDataUri: string | null;
  whatsappShareLink: string | null;
  createdAt: string;
}

export interface PropertyManagerResponse {
  id: number;
  userId: number;
  propertyId: number;
  propertyName: string;
}

export interface UnitResponse {
  id: number;
  propertyId: number;
  unitNumber: string;
  createdAt: string;
}

export interface ResidentResponse {
  id: number;
  userId: number;
  fullName: string;
  phoneNumber: string;
  email: string | null;
  unitId: number;
  unitNumber: string;
  createdAt: string;
}

export interface CheckpointResponse {
  id: number;
  propertyId: number;
  name: string;
  latitude: number;
  longitude: number;
  geoToleranceMeters: number | null;
  qrToken: string;
  qrCodeDataUri: string | null;
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
}

export interface MissedCheckpointResponse {
  routeId: number;
  routeName: string;
  from: string;
  to: string;
  checkpoints: MissedCheckpointStatus[];
}

export interface GuardResponse {
  id: number;
  userId: number;
  fullName: string;
  phoneNumber: string;
  email: string;
  propertyId: number;
  propertyName: string;
  badgeNumber: string | null;
  createdAt: string;
}

export interface VehicleResponse {
  id: number;
  registration: string;
  make: string | null;
  model: string | null;
  colour: string | null;
  createdAt: string;
}

export interface PropertySupervisorResponse {
  id: number;
  userId: number;
  propertyId: number;
  propertyName: string;
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

export interface PropertyResponse {
  id: number;
  name: string;
  address: string | null;
  timezone: string;
  latitude: number | null;
  longitude: number | null;
  geoToleranceMeters: number | null;
  createdAt: string;
}

export interface UserResponse {
  id: number;
  fullName: string;
  phoneNumber: string;
  email: string | null;
  roles: Role[];
  active: boolean;
  createdAt: string;
}

export interface AuditVerificationResponse {
  valid: boolean;
  firstBrokenId: number | null;
}

export interface ApiErrorBody {
  status: number;
  error: string;
  timestamp?: string;
}
