export interface Category {
  id: string;
  name: string;
  imageUrl: string;
}

export interface Food {
  id: string;
  restaurantId: string;
  categoryId: string;
  name: string;
  description: string;
  price: number;
  imageUrl: string;
  rating?: number;
  isAvailable: boolean;
  isVegetarian: boolean;
  preparationTime?: number;
}

export interface Restaurant {
  id: string;
  name: string;
  description?: string;
  address: string;
  city?: string;
  state?: string;
  pincode?: string;
  phone?: string;
  email?: string;
  imageUrl: string;
  bannerUrl?: string;
  cuisine?: string;
  latitude?: number;
  longitude?: number;
  openingTime?: string;
  closingTime?: string;
  deliveryRadius?: number;
  deliveryFee?: number;
  minimumOrder?: number;
  preparationTime?: number;
  rating?: number;
  isOpen: boolean;
  isApproved?: boolean;
}

export interface Offer {
  id: string;
  title: string;
  description: string;
  imageUrl: string;
  discount?: number;
  priority?: number;
  target?: string;
  startDate?: string;
  endDate?: string;
  isActive?: boolean;
}

export interface Coupon {
  id: string;
  code: string;
  type?: string;
  discountValue?: number;
  maxDiscount?: number;
  minOrderAmount?: number;
  usageLimit?: number;
  currentUses?: number;
  perUserLimit?: number;
  firstOrderOnly?: boolean;
  expiryDate?: string;
  isActive?: boolean;
}

export interface User {
  id: string;
  email?: string;
  name?: string;
  phone?: string;
  role?: string;
  isSuspended?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Review {
  id: string;
  userId: string;
  orderId?: string;
  restaurantId: string;
  foodId?: string;
  rating: number;
  comment: string;
  isReported?: boolean;
  isHidden?: boolean;
  createdAt?: string;
  userName?: string;
}

export interface OrderItem {
  foodId: string;
  quantity: number;
  price: number;
  name?: string;
}

export interface DeliveryPartner {
  id: string;
  name: string;
  phone: string;
  vehicleType: string;
  vehicleNumber: string;
}

export interface Order {
  id: string;
  userId: string;
  restaurantId: string;
  restaurantName?: string;
  items: OrderItem[];
  totalAmount: number;
  orderStatus: string;
  paymentStatus?: string;
  createdAt: string;
  deliveryPartner?: DeliveryPartner;
  deliveryOtp?: string;
  deliveryIssue?: {
    reason: string;
    description: string;
    reportedBy: string;
    reportedAt: number;
    status: string;
  };
}

export interface AuditLog {
  id: string;
  action: string;
  adminId?: string;
  adminEmail?: string;
  resourceType?: string;
  resourceId?: string;
  changes?: any;
  timestamp: any;
}

export interface Driver {
  uid: string;
  name: string;
  email: string;
  phone: string;
  vehicleType: string;
  vehicleNumber: string;
  licenseNumber: string;
  emergencyContact: string;
  status: string; // PENDING, APPROVED, SUSPENDED, REJECTED
  availability: string; // ONLINE, OFFLINE, BUSY
  createdAt: any;
  updatedAt: any;
  fcmToken?: string;
  lastLocation?: {
    latitude: number;
    longitude: number;
    updatedAt: number;
  };
  activeOrderId?: string;
  activeDeliveriesCount?: number;
  reliability?: number;
}

