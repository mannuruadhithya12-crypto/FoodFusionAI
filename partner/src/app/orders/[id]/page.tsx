"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useParams, useRouter } from "next/navigation";
import { doc, getDoc } from "firebase/firestore";
import { db, functions } from "@/lib/firebase";
import { httpsCallable } from "firebase/functions";
import { Order } from "@/types";
import { Clock, ArrowLeft, User, MapPin, Clipboard, Utensils, Check, X, Phone } from "lucide-react";

export default function OrderDetailPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);

  // Modal actions
  const [acceptOpen, setAcceptOpen] = useState(false);
  const [prepMinutes, setPrepMinutes] = useState(20);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const fetchOrder = async () => {
    if (!id) return;
    try {
      const docRef = doc(db, "orders", id);
      const docSnap = await getDoc(docRef);
      if (docSnap.exists()) {
        setOrder({ id: docSnap.id, ...docSnap.data() } as Order);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrder();
  }, [id]);

  const handleAccept = async () => {
    setProcessing(true);
    setAcceptOpen(false);
    try {
      const acceptFn = httpsCallable(functions, "partnerAcceptOrder");
      await acceptFn({ orderId: id, prepMinutes });
      await fetchOrder();
    } catch (e: any) {
      alert("Error: " + e.message);
    } finally {
      setProcessing(false);
    }
  };

  const handleReject = async () => {
    setProcessing(true);
    setRejectOpen(false);
    try {
      const rejectFn = httpsCallable(functions, "partnerRejectOrder");
      await rejectFn({ orderId: id, reason: rejectReason });
      await fetchOrder();
    } catch (e: any) {
      alert("Error: " + e.message);
    } finally {
      setProcessing(false);
    }
  };

  const handleStatusUpdate = async (status: string) => {
    setProcessing(true);
    try {
      const updateFn = httpsCallable(functions, "partnerUpdateOrderStatus");
      await updateFn({ orderId: id, newStatus: status });
      await fetchOrder();
    } catch (e: any) {
      alert("Error: " + e.message);
    } finally {
      setProcessing(false);
    }
  };

  if (loading) {
    return (
      <PartnerLayout>
        <div className="flex justify-center items-center py-32">
          <div className="w-10 h-10 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
        </div>
      </PartnerLayout>
    );
  }

  if (!order) {
    return (
      <PartnerLayout>
        <div className="text-center py-20">
          <h2 className="text-xl font-bold text-slate-300">Order not found.</h2>
          <button onClick={() => router.push("/orders")} className="btn-primary mt-4">
            Back to Orders
          </button>
        </div>
      </PartnerLayout>
    );
  }

  return (
    <PartnerLayout>
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Back Button & Title */}
        <div className="flex items-center gap-4">
          <button onClick={() => router.push("/orders")} className="p-2 bg-white/5 border border-white/5 rounded-xl text-slate-400 hover:text-slate-200 transition-colors">
            <ArrowLeft size={18} />
          </button>
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-100">Order #{order.id.slice(-6).toUpperCase()}</h1>
            <p className="text-xs text-slate-500 mt-0.5">Placed on {new Date(order.createdAt?.seconds ? order.createdAt.seconds * 1000 : order.createdAt).toLocaleString()}</p>
          </div>
        </div>

        {/* Content Layout */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Main Info */}
          <div className="md:col-span-2 space-y-6">
            {/* Items Card */}
            <div className="glass-card p-6">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex items-center gap-2">
                <Utensils size={16} /> Order Items ({order.items.length})
              </h2>
              <div className="divide-y divide-white/5">
                {order.items.map((item, idx) => (
                  <div key={idx} className="py-4 flex justify-between items-center first:pt-0 last:pb-0">
                    <div>
                      <p className="text-sm font-semibold text-slate-200">{item.name}</p>
                      {item.customization && (
                        <p className="text-xs text-orange-400/80 mt-0.5 font-medium">Customization: {item.customization}</p>
                      )}
                      <p className="text-xs text-slate-500 mt-1">₹{item.price} × {item.quantity}</p>
                    </div>
                    <p className="text-sm font-bold text-slate-200">₹{item.price * item.quantity}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Delivery address / details card */}
            <div className="glass-card p-6">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex items-center gap-2">
                <MapPin size={16} /> Customer & Delivery Details
              </h2>
              <div className="space-y-4">
                <div className="flex gap-3">
                  <User className="text-slate-500 mt-0.5" size={18} />
                  <div>
                    <p className="text-sm font-semibold text-slate-200">Customer ID</p>
                    <p className="text-xs text-slate-500 mt-0.5">{order.userId}</p>
                  </div>
                </div>

                {order.deliveryAddress && (
                  <div className="flex gap-3">
                    <MapPin className="text-slate-500 mt-0.5" size={18} />
                    <div>
                      <p className="text-sm font-semibold text-slate-200">Delivery Address</p>
                      <p className="text-xs text-slate-400 mt-0.5">
                        {order.deliveryAddress.street}, {order.deliveryAddress.city} - {order.deliveryAddress.zipCode}
                      </p>
                    </div>
                  </div>
                )}

                {order.deliveryInstructions && (
                  <div className="p-3 bg-orange-500/5 border border-orange-500/10 rounded-xl text-left">
                    <p className="text-xs text-orange-400 font-semibold mb-0.5">Instructions:</p>
                    <p className="text-xs text-slate-300">{order.deliveryInstructions}</p>
                  </div>
                )}
              </div>
            </div>

            {/* Order status history */}
            <div className="glass-card p-6">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-6 flex items-center gap-2">
                <Clipboard size={16} /> Tracking Logs Timeline
              </h2>
              <div className="relative border-l-2 border-white/5 ml-3 space-y-6">
                {(order.statusHistory || []).map((step: any, idx: number) => (
                  <div key={idx} className="relative pl-6">
                    <div className={`absolute -left-[7px] top-1 w-3 h-3 rounded-full border-2 border-[#12121a] ${
                      step.status === "CANCELLED" ? "bg-red-500" :
                      step.status === "READY_FOR_PICKUP" ? "bg-green-500" :
                      step.status === "PREPARING" ? "bg-blue-500" : "bg-orange-500"
                    }`} />
                    <div className="text-xs">
                      <div className="flex justify-between items-center mb-1">
                        <span className="font-bold text-slate-200">{step.status}</span>
                        <span className="text-slate-500 font-medium">
                          {new Date(step.timestamp).toLocaleString()}
                        </span>
                      </div>
                      <p className="text-slate-400">{step.message || `Status changed from ${step.previousStatus} to ${step.status}`}</p>
                    </div>
                  </div>
                ))}
                {(order.statusHistory || []).length === 0 && (
                  <div className="pl-6 text-xs text-slate-500">No status timeline logged yet.</div>
                )}
              </div>
            </div>
          </div>

          {/* Actions & Price Panel */}
          <div className="space-y-6">
            {/* Price breakdown */}
            <div className="glass-card p-6 bg-gradient-to-br from-[#12121a] to-[#151522]">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Invoice</h2>
              <div className="space-y-3 text-sm">
                <div className="flex justify-between text-slate-400">
                  <span>Grand Total</span>
                  <span className="font-bold text-slate-200">₹{order.totalAmount}</span>
                </div>
                <div className="flex justify-between text-slate-400 border-t border-white/5 pt-3">
                  <span>Status</span>
                  <span className={`badge text-[10px] font-bold ${
                    order.orderStatus === "CONFIRMED" ? "badge-amber" :
                    order.orderStatus === "PREPARING" ? "badge-blue" :
                    order.orderStatus === "READY_FOR_PICKUP" ? "badge-green" :
                    order.orderStatus === "DELIVERED" ? "badge-green" :
                    order.orderStatus === "CANCELLED" ? "badge-red" : "badge-slate"
                  }`}>
                    {order.orderStatus}
                  </span>
                </div>
              </div>
            </div>

            {/* Quick action buttons */}
            <div className="glass-card p-6 space-y-3">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Actions</h2>
              {order.orderStatus === "CONFIRMED" && (
                <>
                  <button
                    onClick={() => setAcceptOpen(true)}
                    disabled={processing}
                    className="w-full btn-primary justify-center cursor-pointer"
                  >
                    Accept Order
                  </button>
                  <button
                    onClick={() => setRejectOpen(true)}
                    disabled={processing}
                    className="w-full btn-danger justify-center cursor-pointer"
                  >
                    Reject Order
                  </button>
                </>
              )}

              {order.orderStatus === "PREPARING" && (
                <button
                  onClick={() => handleStatusUpdate("READY_FOR_PICKUP")}
                  disabled={processing}
                  className="w-full btn-primary justify-center cursor-pointer"
                >
                  Mark Preparing Done (Ready)
                </button>
              )}

              {order.orderStatus === "READY_FOR_PICKUP" && (
                <button
                  onClick={() => handleStatusUpdate("DELIVERED")}
                  disabled={processing}
                  className="w-full btn-success justify-center cursor-pointer"
                >
                  Mark Order Delivered
                </button>
              )}

              {order.orderStatus === "CANCELLED" && (
                <p className="text-xs text-red-400/80 font-medium text-center">This order has been cancelled.</p>
              )}

              {order.orderStatus === "DELIVERED" && (
                <p className="text-xs text-green-400/80 font-medium text-center">This order is completed.</p>
              )}
            </div>
          </div>
        </div>

        {/* Accept Modal */}
        {acceptOpen && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h3 className="text-lg font-bold text-slate-100 mb-2">Accept Order</h3>
              <p className="text-slate-400 text-sm mb-6 font-medium">Select kitchen prep duration:</p>
              <div className="mb-6">
                <label className="input-label">Minutes</label>
                <select className="input-field" value={prepMinutes} onChange={(e) => setPrepMinutes(Number(e.target.value))}>
                  <option value={15}>15 Mins</option>
                  <option value={20}>20 Mins</option>
                  <option value={30}>30 Mins</option>
                  <option value={45}>45 Mins</option>
                </select>
              </div>
              <div className="flex gap-3 justify-end">
                <button onClick={() => setAcceptOpen(false)} className="btn-secondary">Cancel</button>
                <button onClick={handleAccept} className="btn-primary">Confirm</button>
              </div>
            </div>
          </div>
        )}

        {/* Reject Modal */}
        {rejectOpen && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h3 className="text-lg font-bold text-slate-100 mb-2">Reject Order</h3>
              <p className="text-slate-400 text-sm mb-6 font-medium">Select cancellation reason:</p>
              <div className="mb-6">
                <label className="input-label">Reason</label>
                <select className="input-field" value={rejectReason} onChange={(e) => setRejectReason(e.target.value)}>
                  <option value="">Select reason</option>
                  <option value="Out of Stock">Ingredients Out of Stock</option>
                  <option value="Kitchen Overloaded">Kitchen Overloaded</option>
                  <option value="Closing">Closing Time</option>
                </select>
              </div>
              <div className="flex gap-3 justify-end">
                <button onClick={() => setRejectOpen(false)} className="btn-secondary">Cancel</button>
                <button onClick={handleReject} disabled={!rejectReason} className="btn-danger disabled:opacity-50">Reject</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </PartnerLayout>
  );
}
