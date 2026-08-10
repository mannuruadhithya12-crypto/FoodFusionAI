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
  approvalStatus?: string;
  suspensionReason?: string;
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
  uid: string;
  email: string;
  displayName?: string;
  phoneNumber?: string;
  role: string;
  active: boolean;
  restaurantIds: string[];
  isSuspended?: boolean;
  createdAt?: number;
  updatedAt?: number;
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
  customization?: string;
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
  createdAt: any;
  deliveryAddress?: any;
  deliveryInstructions?: string;
  deliveryPartner?: DeliveryPartner;
  deliveryOtp?: string;
  deliveryIssue?: {
    reason: string;
    description: string;
    reportedBy: string;
    reportedAt: number;
    status: string;
  };
  statusHistory?: any[];
}

export interface AuditLog {
  id: string;
  action: string;
  adminId?: string;
  adminEmail?: string;
  resourceType?: string;
  resourceId?: string;
  targetId?: string;
  actorUid?: string;
  changes?: any;
  timestamp: any;
}
