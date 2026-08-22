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

export interface ApiErrorBody {
  status: number;
  error: string;
  timestamp?: string;
}
