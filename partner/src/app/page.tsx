"use client";

import { useEffect, useState } from "react";
import { collection, query, where, orderBy, onSnapshot } from "firebase/firestore";
import { signInWithEmailAndPassword } from "firebase/auth";
import { httpsCallable } from "firebase/functions";
import { db, auth, functions } from "../lib/firebase";
import { useAuth } from "../context/AuthContext";
import { Order, OrderItem } from "../types";
import { 
  Store, 
  LogOut, 
  Clock, 
  CheckCircle, 
  Truck, 
  Package, 
  XCircle, 
  AlertTriangle, 
  ShieldAlert,
  ClipboardList,
  ChevronRight,
  User,
  Phone
} from "lucide-react";

export default function PartnerDashboard() {
  const { 
    user, 
    profile, 
    loading, 
    isPartner, 
    selectedRestaurantId, 
    setSelectedRestaurantId,
    signOut 
  } = useAuth();

  const [orders, setOrders] = useState<Order[]>([]);
  const [loadingOrders, setLoadingOrders] = useState(true);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [processingId, setProcessingId] = useState<string | null>(null);
  
  // Selection states for prep times
  const [selectedPrepTimes, setSelectedPrepTimes] = useState<Record<string, number>>({});

  useEffect(() => {
    if (!selectedRestaurantId) {
      setOrders([]);
      setLoadingOrders(false);
      return;
    }

    setLoadingOrders(true);
    const q = query(
      collection(db, "orders"),
      where("restaurantId", "==", selectedRestaurantId),
      orderBy("createdAt", "desc")
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      setOrders(snapshot.docs.map(d => ({ id: d.id, ...d.data() } as Order)));
      setLoadingOrders(false);
    }, (error) => {
      console.error("Error listening to orders:", error);
      setLoadingOrders(false);
    });

    return () => unsubscribe();
  }, [selectedRestaurantId]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError("");
    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (err: any) {
      setAuthError(err.message || "Failed to sign in. Check credentials.");
    }
  };

  const acceptOrder = async (orderId: string) => {
    const prepMinutes = selectedPrepTimes[orderId] || 30; // default to 30 mins
    setProcessingId(orderId);
    try {
      const partnerAccept = httpsCallable(functions, "partnerAcceptOrder");
      await partnerAccept({ orderId, prepMinutes });
      
      // Auto trigger driver assignment matching once restaurant accepts order
      const assignDriver = httpsCallable(functions, "assignDriverToOrder");
      await assignDriver({ orderId });
      
      Toast("Order accepted. Matching drivers...");
    } catch (err: any) {
      alert("Failed to accept order: " + err.message);
    } finally {
      setProcessingId(null);
    }
  };

  const markReady = async (orderId: string) => {
    setProcessingId(orderId);
    try {
      const updateStatus = httpsCallable(functions, "partnerUpdateOrderStatus");
      await updateStatus({ orderId, newStatus: "READY_FOR_PICKUP" });
      
      // Also request driver matching in case no driver was assigned yet
      const assignDriver = httpsCallable(functions, "assignDriverToOrder");
      await assignDriver({ orderId });
      
      Toast("Order marked ready for pickup!");
    } catch (err: any) {
      alert("Failed to update status: " + err.message);
    } finally {
      setProcessingId(null);
    }
  };

  const triggerManualAssignment = async (orderId: string) => {
    setProcessingId(orderId);
    try {
      const assignDriver = httpsCallable(functions, "assignDriverToOrder");
      const res = await assignDriver({ orderId }) as any;
      if (res.data?.success) {
        Toast("Offered order to available drivers.");
      } else {
        alert(res.data?.message || "No online drivers available.");
      }
    } catch (err: any) {
      alert("Error: " + err.message);
    } finally {
      setProcessingId(null);
    }
  };

  const Toast = (msg: string) => {
    alert(msg); // Simplified for clean operation
  };

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-zinc-50 dark:bg-black text-black dark:text-white font-sans">
        <div className="text-center space-y-4">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500 mx-auto"></div>
          <p className="text-zinc-500">Loading partner panel...</p>
        </div>
      </div>
    );
  }

  // 1. Auth Page
  if (!user) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-zinc-50 dark:bg-black p-6 font-sans">
        <div className="w-full max-w-md bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl shadow-xl p-8 space-y-6">
          <div className="text-center space-y-2">
            <h1 className="text-3xl font-extrabold tracking-tight text-orange-600">FOODFUSION</h1>
            <p className="text-zinc-500 dark:text-zinc-400">Restaurant Partner Portal</p>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            <div className="space-y-1">
              <label className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Email Address</label>
              <input 
                type="email" 
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                className="w-full px-4 py-3 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 focus:outline-none focus:ring-2 focus:ring-orange-500"
                placeholder="partner@restaurant.com"
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Password</label>
              <input 
                type="password" 
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                className="w-full px-4 py-3 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 focus:outline-none focus:ring-2 focus:ring-orange-500"
                placeholder="••••••••"
              />
            </div>

            {authError && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600 flex items-center gap-2">
                <ShieldAlert size={16} />
                <span>{authError}</span>
              </div>
            )}

            <button 
              type="submit"
              className="w-full py-3 bg-orange-600 hover:bg-orange-700 text-white rounded-lg font-bold shadow-lg transition-transform active:scale-95"
            >
              SIGN IN
            </button>
          </form>

          <div className="border-t border-zinc-200 dark:border-zinc-800 pt-4">
            <button 
              onClick={() => {
                setEmail("adhithya@restaurant.com");
                setPassword("123456");
              }}
              className="w-full py-2 border border-zinc-300 dark:border-zinc-700 rounded-lg text-xs font-semibold hover:bg-zinc-50 dark:hover:bg-zinc-800 text-zinc-600 dark:text-zinc-400"
            >
              Use Demo Restaurant Partner Account
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 2. Role Authorization Block
  if (!isPartner) {
    return (
      <div className="flex h-screen items-center justify-center bg-zinc-50 dark:bg-black font-sans p-6 text-center">
        <div className="max-w-sm space-y-4">
          <ShieldAlert size={64} className="text-red-500 mx-auto" />
          <h2 className="text-2xl font-bold">Access Unauthorized</h2>
          <p className="text-zinc-500">Your account does not have partner roles assigned, or is deactivated. Please contact your FoodFusion Admin.</p>
          <button onClick={signOut} className="px-6 py-2 bg-orange-600 text-white rounded-lg">Sign Out</button>
        </div>
      </div>
    );
  }

  const activeOrders = orders.filter(o => 
    o.orderStatus !== "DELIVERED" && o.orderStatus !== "CANCELLED"
  );
  
  const completedOrders = orders.filter(o => 
    o.orderStatus === "DELIVERED" || o.orderStatus === "CANCELLED"
  );

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-black text-zinc-900 dark:text-zinc-100 font-sans flex flex-col">
      
      {/* Top Navigation */}
      <header className="bg-white dark:bg-zinc-900 border-b border-zinc-200 dark:border-zinc-800 py-4 px-6 flex justify-between items-center sticky top-0 z-50">
        <div className="flex items-center gap-3">
          <Store className="text-orange-600" size={28} />
          <div>
            <h1 className="text-xl font-bold tracking-tight">FoodFusion Partner</h1>
            <p className="text-xs text-zinc-500">Restaurant Operations Portal</p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          {/* Restaurant Selector */}
          {profile?.restaurantIds && profile.restaurantIds.length > 1 && (
            <select
              value={selectedRestaurantId || ""}
              onChange={e => setSelectedRestaurantId(e.target.value)}
              className="px-3 py-1.5 border rounded-lg bg-zinc-100 dark:bg-zinc-800 text-sm font-semibold focus:outline-none"
            >
              {profile.restaurantIds.map(rid => (
                <option key={rid} value={rid}>Store ID: {rid.slice(0,8).toUpperCase()}</option>
              ))}
            </select>
          )}

          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-zinc-500">Hi, {profile?.displayName || "Partner"}</span>
            <button 
              onClick={signOut}
              className="p-2 text-zinc-500 hover:text-red-600 rounded-full hover:bg-zinc-100 dark:hover:bg-zinc-800"
              title="Sign Out"
            >
              <LogOut size={20} />
            </button>
          </div>
        </div>
      </header>

      {/* Main Operations Dashboard */}
      <main className="flex-1 p-6 max-w-7xl w-full mx-auto grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Orders Board (2 Cols) */}
        <section className="lg:col-span-2 space-y-6">
          <div className="flex justify-between items-center">
            <h2 className="text-2xl font-extrabold flex items-center gap-2">
              <ClipboardList className="text-orange-600" />
              <span>Active Orders ({activeOrders.length})</span>
            </h2>
            {loadingOrders && <span className="text-sm text-zinc-500 animate-pulse">Syncing orders...</span>}
          </div>

          <div className="space-y-4">
            {activeOrders.length === 0 && !loadingOrders && (
              <div className="bg-white dark:bg-zinc-900 border border-dashed rounded-xl py-16 text-center text-zinc-500 space-y-2">
                <Package size={48} className="mx-auto opacity-30" />
                <p className="font-medium text-lg">No active orders right now</p>
                <p className="text-sm">New customer orders will appear here automatically.</p>
              </div>
            )}

            {activeOrders.map(order => (
              <div 
                key={order.id}
                className={`bg-white dark:bg-zinc-900 border rounded-xl shadow-sm overflow-hidden flex flex-col ${order.deliveryIssue ? 'border-red-500 dark:border-red-800' : 'border-zinc-200 dark:border-zinc-800'}`}
              >
                {/* Header info */}
                <div className="p-4 border-b border-zinc-100 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/50 flex justify-between items-start">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-extrabold text-sm text-zinc-500">ORDER ID:</span>
                      <span className="font-bold text-sm tracking-widest">{order.id.take(8).toUpperCase()}...</span>
                      {order.deliveryIssue && (
                        <span className="px-2 py-0.5 bg-red-100 text-red-800 rounded-full text-xs font-bold flex items-center gap-1">
                          <AlertTriangle size={10} /> ISSUE
                        </span>
                      )}
                    </div>
                    <span className="text-xs text-zinc-400">Total amount: ₹{order.totalAmount}</span>
                  </div>
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    order.orderStatus === 'CONFIRMED' ? 'bg-blue-100 text-blue-800' :
                    order.orderStatus === 'PREPARING' ? 'bg-purple-100 text-purple-800' :
                    order.orderStatus === 'READY_FOR_PICKUP' ? 'bg-orange-100 text-orange-800' :
                    'bg-indigo-100 text-indigo-800'
                  }`}>
                    {order.orderStatus}
                  </span>
                </div>

                {/* Items & details */}
                <div className="p-5 flex-1 grid grid-cols-1 md:grid-cols-2 gap-4">
                  
                  {/* Items list */}
                  <div className="space-y-3">
                    <h4 className="text-xs font-bold text-zinc-400 tracking-wider">ITEMS</h4>
                    <div className="space-y-2">
                      {order.items?.map((item, idx) => (
                        <div key={idx} className="flex justify-between text-sm">
                          <span className="font-medium text-zinc-800 dark:text-zinc-200">
                            {item.quantity}x {item.name || item.foodId}
                          </span>
                          <span className="text-zinc-400">₹{item.price * item.quantity}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Delivery Partner Status info */}
                  <div className="border-l border-zinc-100 dark:border-zinc-800 pl-4 space-y-3">
                    <h4 className="text-xs font-bold text-zinc-400 tracking-wider">DELIVERY PARTNER</h4>
                    
                    {order.deliveryPartner ? (
                      <div className="space-y-2">
                        <div className="flex items-center gap-2">
                          <div className="p-1.5 bg-zinc-100 dark:bg-zinc-800 rounded-full">
                            <User size={16} />
                          </div>
                          <div>
                            <p className="text-sm font-bold">{order.deliveryPartner.name}</p>
                            <p className="text-xs text-zinc-400">{order.deliveryPartner.vehicleType} ({order.deliveryPartner.vehicleNumber})</p>
                          </div>
                        </div>
                        <div className="text-xs text-zinc-500 space-y-1">
                          <p className="flex items-center gap-1"><Phone size={12} /> {order.deliveryPartner.phone}</p>
                          <p>Status: <span className="font-semibold text-orange-500">{order.orderStatus === 'READY_FOR_PICKUP' ? 'Arriving for pickup' : order.orderStatus}</span></p>
                          {order.deliveryOtp && <p>Confirmation Code: <span className="font-bold text-green-600">{order.deliveryOtp}</span></p>}
                        </div>
                      </div>
                    ) : (
                      <div className="space-y-2">
                        <p className="text-xs text-zinc-500 italic">No delivery partner assigned yet.</p>
                        {order.orderStatus === "READY_FOR_PICKUP" && (
                          <button
                            onClick={() => triggerManualAssignment(order.id)}
                            disabled={processingId === order.id}
                            className="text-xs py-1.5 px-3 bg-zinc-100 hover:bg-zinc-200 text-zinc-800 dark:bg-zinc-800 dark:hover:bg-zinc-700 dark:text-white rounded-lg font-bold"
                          >
                            Assign Driver
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>

                {/* Delivery Issue display */}
                {order.deliveryIssue && (
                  <div className="mx-5 mb-4 p-3 bg-red-50 border border-red-200 dark:bg-red-950/20 dark:border-red-900 rounded-lg text-xs space-y-1">
                    <p className="font-bold text-red-600 flex items-center gap-1">
                      <ShieldAlert size={14} /> Issue Reported: {order.deliveryIssue.reason}
                    </p>
                    <p className="text-zinc-600 dark:text-zinc-400">{order.deliveryIssue.description}</p>
                  </div>
                )}

                {/* Actions footer */}
                <div className="p-4 border-t border-zinc-100 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/50 flex justify-between items-center gap-4">
                  {order.orderStatus === "CONFIRMED" && (
                    <div className="flex items-center gap-3 w-full">
                      <div className="flex items-center gap-2">
                        <label className="text-xs text-zinc-500">Prep Time:</label>
                        <select 
                          value={selectedPrepTimes[order.id] || 30}
                          onChange={e => setSelectedPrepTimes(prev => ({ ...prev, [order.id]: parseInt(e.target.value) }))}
                          className="px-2 py-1 border rounded bg-white dark:bg-zinc-800 text-xs font-semibold"
                        >
                          <option value={15}>15 Mins</option>
                          <option value={20}>20 Mins</option>
                          <option value={30}>30 Mins</option>
                          <option value={45}>45 Mins</option>
                          <option value={60}>60 Mins</option>
                        </select>
                      </div>
                      <button 
                        onClick={() => acceptOrder(order.id)}
                        disabled={processingId === order.id}
                        className="ml-auto px-5 py-2 bg-orange-600 hover:bg-orange-700 text-white rounded-lg text-xs font-bold"
                      >
                        Accept &amp; Start Preparing
                      </button>
                    </div>
                  )}

                  {order.orderStatus === "PREPARING" && (
                    <button 
                      onClick={() => markReady(order.id)}
                      disabled={processingId === order.id}
                      className="ml-auto px-5 py-2 bg-orange-600 hover:bg-orange-700 text-white rounded-lg text-xs font-bold"
                    >
                      Mark Ready for Pickup
                    </button>
                  )}

                  {order.orderStatus === "READY_FOR_PICKUP" && (
                    <span className="text-xs text-zinc-500 italic">Waiting for driver collection...</span>
                  )}

                  {order.orderStatus === "OUT_FOR_DELIVERY" && (
                    <span className="text-xs text-zinc-500 italic flex items-center gap-1">
                      <Truck size={14} className="animate-bounce" /> Order is out for delivery!
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* History / Summary Stats Panel (1 Col) */}
        <section className="space-y-6">
          <h2 className="text-xl font-bold flex items-center gap-2">
            <CheckCircle className="text-green-600" />
            <span>Operational History</span>
          </h2>

          <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl shadow-sm p-4 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-zinc-50 dark:bg-zinc-800 p-4 rounded-lg text-center">
                <p className="text-2xl font-black text-orange-600">{completedOrders.filter(o => o.orderStatus === 'DELIVERED').length}</p>
                <p className="text-xs text-zinc-500 font-semibold mt-1">Today's Completed</p>
              </div>
              <div className="bg-zinc-50 dark:bg-zinc-800 p-4 rounded-lg text-center">
                <p className="text-2xl font-black text-red-500">{completedOrders.filter(o => o.orderStatus === 'CANCELLED').length}</p>
                <p className="text-xs text-zinc-500 font-semibold mt-1">Today's Cancelled</p>
              </div>
            </div>

            <div className="border-t border-zinc-100 dark:border-zinc-800 pt-4 space-y-3">
              <h4 className="text-xs font-bold text-zinc-400 tracking-wider">COMPLETED DELIVERIES</h4>
              
              <div className="space-y-2 max-h-96 overflow-y-auto">
                {completedOrders.length === 0 && (
                  <p className="text-xs text-zinc-500 italic text-center py-4">No completed orders yet today.</p>
                )}
                {completedOrders.map(order => (
                  <div key={order.id} className="flex justify-between items-center text-xs py-2 border-b last:border-0 border-zinc-100 dark:border-zinc-800">
                    <div>
                      <p className="font-bold">#{order.id.slice(0,6).toUpperCase()}</p>
                      <p className="text-zinc-400">Total: ₹{order.totalAmount}</p>
                    </div>
                    <span className={`px-2 py-0.5 rounded-full font-bold ${
                      order.orderStatus === 'DELIVERED' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                    }`}>
                      {order.orderStatus}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

      </main>

    </div>
  );
}

// Extension to take first N characters
declare global {
  interface String {
    take(n: number): string;
  }
}
String.prototype.take = function(n: number) {
  return this.substring(0, n);
};
