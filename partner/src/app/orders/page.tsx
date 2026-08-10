"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { collection, query, where, onSnapshot } from "firebase/firestore";
import { db, functions } from "@/lib/firebase";
import { httpsCallable } from "firebase/functions";
import { Order } from "@/types";
import { ClipboardList, Clock, IndianRupee, Eye, Check, X, Bell } from "lucide-react";
import Link from "next/link";

export default function OrdersPage() {
  const { selectedRestaurantId } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [acceptOrder, setAcceptOrder] = useState<Order | null>(null);
  const [prepMinutes, setPrepMinutes] = useState(20);
  const [rejectOrder, setRejectOrder] = useState<Order | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [processingId, setProcessingId] = useState("");
  const [soundEnabled, setSoundEnabled] = useState(true);

  // Web Audio chime generator to bypass missing asset errors
  const playNewOrderChime = () => {
    if (!soundEnabled) return;
    try {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      
      // Synthesis chime: Two-tone notification
      const osc1 = ctx.createOscillator();
      const osc2 = ctx.createOscillator();
      const gainNode = ctx.createGain();

      osc1.type = "sine";
      osc1.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
      osc1.frequency.setValueAtTime(880.00, ctx.currentTime + 0.15); // A5

      osc2.type = "triangle";
      osc2.frequency.setValueAtTime(293.66, ctx.currentTime); // D4
      osc2.frequency.setValueAtTime(440.00, ctx.currentTime + 0.15); // A4

      gainNode.gain.setValueAtTime(0.15, ctx.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5);

      osc1.connect(gainNode);
      osc2.connect(gainNode);
      gainNode.connect(ctx.destination);

      osc1.start();
      osc2.start();
      osc1.stop(ctx.currentTime + 0.5);
      osc2.stop(ctx.currentTime + 0.5);
    } catch (e) {
      console.warn("Audio Context blocked or unsupported.", e);
    }
  };

  useEffect(() => {
    if (!selectedRestaurantId) return;

    setLoading(true);
    const q = query(
      collection(db, "orders"),
      where("restaurantId", "==", selectedRestaurantId)
    );

    let isInitialLoad = true;

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list: Order[] = [];
      let hasNewOrder = false;

      snapshot.forEach((doc) => {
        const data = doc.data();
        const o = { id: doc.id, ...data } as Order;
        list.push(o);

        // Check if there's a new confirmed order
        if (!isInitialLoad && o.orderStatus === "CONFIRMED") {
          hasNewOrder = true;
        }
      });

      if (hasNewOrder) {
        playNewOrderChime();
      }

      isInitialLoad = false;
      setOrders(list);
      setLoading(false);
    });

    return unsubscribe;
  }, [selectedRestaurantId]);

  // Status handlers
  const handleAccept = async () => {
    if (!acceptOrder) return;
    setProcessingId(acceptOrder.id);
    const orderId = acceptOrder.id;
    setAcceptOrder(null);

    try {
      const acceptFn = httpsCallable(functions, "partnerAcceptOrder");
      await acceptFn({ orderId, prepMinutes: Number(prepMinutes) });
    } catch (e: any) {
      alert("Error accepting order: " + e.message);
    } finally {
      setProcessingId("");
    }
  };

  const handleReject = async () => {
    if (!rejectOrder || !rejectReason) return;
    setProcessingId(rejectOrder.id);
    const orderId = rejectOrder.id;
    const reason = rejectReason;
    setRejectOrder(null);
    setRejectReason("");

    try {
      const rejectFn = httpsCallable(functions, "partnerRejectOrder");
      await rejectFn({ orderId, reason });
    } catch (e: any) {
      alert("Error rejecting order: " + e.message);
    } finally {
      setProcessingId("");
    }
  };

  const handleUpdateStatus = async (orderId: string, newStatus: string) => {
    setProcessingId(orderId);
    try {
      const updateFn = httpsCallable(functions, "partnerUpdateOrderStatus");
      await updateFn({ orderId, newStatus });
    } catch (e: any) {
      alert("Error updating order status: " + e.message);
    } finally {
      setProcessingId("");
    }
  };

  // Grouping orders by column
  const getOrdersByStatus = (statusGroup: string[]) => {
    return orders.filter((o) => statusGroup.includes(o.orderStatus));
  };

  const columns = [
    { title: "New Orders", statuses: ["CONFIRMED"], style: "border-t-4 border-t-amber-500 bg-amber-500/5" },
    { title: "Preparing", statuses: ["PREPARING"], style: "border-t-4 border-t-blue-500 bg-blue-500/5" },
    { title: "Ready for Pickup", statuses: ["READY_FOR_PICKUP"], style: "border-t-4 border-t-green-500 bg-green-500/5" },
    { title: "Out / Completed", statuses: ["OUT_FOR_DELIVERY", "DELIVERED"], style: "border-t-4 border-t-emerald-500 bg-emerald-500/5" },
    { title: "Cancelled", statuses: ["CANCELLED"], style: "border-t-4 border-t-red-500 bg-red-500/5" }
  ];

  return (
    <PartnerLayout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight flex items-center gap-2">
              <ClipboardList className="text-orange-500" />
              Order Management Board
            </h1>
            <p className="text-slate-400 text-sm">Update cooking steps and trigger delivery handshakes.</p>
          </div>
          <button
            onClick={() => {
              setSoundEnabled(!soundEnabled);
              playNewOrderChime();
            }}
            className={`btn-secondary items-center gap-2 ${soundEnabled ? "text-orange-400" : "text-slate-500"}`}
          >
            <Bell size={18} className={soundEnabled ? "animate-bounce" : ""} />
            Sound: {soundEnabled ? "ON" : "OFF"}
          </button>
        </div>

        {/* Board */}
        {loading ? (
          <div className="flex justify-center items-center py-32">
            <div className="w-10 h-10 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 overflow-x-auto pb-6">
            {columns.map((col) => {
              const colOrders = getOrdersByStatus(col.statuses);
              return (
                <div key={col.title} className={`kanban-column flex flex-col p-4 w-full min-w-[250px] ${col.style}`}>
                  <div className="flex justify-between items-center mb-4 pb-2 border-b border-white/5">
                    <h3 className="font-bold text-sm text-slate-200">{col.title}</h3>
                    <span className="text-xs bg-white/5 text-slate-400 px-2 py-0.5 rounded-full font-bold">
                      {colOrders.length}
                    </span>
                  </div>

                  <div className="space-y-4 flex-1 overflow-y-auto max-h-[70vh]">
                    {colOrders.length === 0 ? (
                      <div className="text-center py-12 text-slate-600 text-xs">
                        Empty column
                      </div>
                    ) : (
                      colOrders.map((order) => {
                        const isProcessing = processingId === order.id;
                        return (
                          <div
                            key={order.id}
                            className={`glass-card p-4 transition-all duration-200 relative ${isProcessing ? "opacity-50 pointer-events-none" : ""}`}
                          >
                            <div className="flex justify-between items-start mb-2">
                              <span className="text-xs font-bold text-slate-400">
                                #{order.id.slice(-6).toUpperCase()}
                              </span>
                              <span className="text-xs font-extrabold text-orange-400">
                                ₹{order.totalAmount}
                              </span>
                            </div>

                            <p className="text-xs font-medium text-slate-300 line-clamp-2 mb-3">
                              {order.items.map((i) => `${i.name} x${i.quantity}`).join(", ")}
                            </p>

                            {/* Actions bar */}
                            <div className="flex items-center gap-2 mt-4 pt-3 border-t border-white/5 justify-between">
                              <Link
                                href={`/orders/${order.id}`}
                                className="p-2 bg-white/5 hover:bg-white/10 rounded-lg text-slate-400 hover:text-slate-200 transition-colors"
                                title="View Details"
                              >
                                <Eye size={14} />
                              </Link>

                              <div className="flex gap-1">
                                {order.orderStatus === "CONFIRMED" && (
                                  <>
                                    <button
                                      onClick={() => setRejectOrder(order)}
                                      className="p-1.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-lg transition-colors cursor-pointer"
                                      title="Reject"
                                    >
                                      <X size={14} />
                                    </button>
                                    <button
                                      onClick={() => setAcceptOrder(order)}
                                      className="p-1.5 bg-green-500/10 hover:bg-green-500/20 text-green-400 rounded-lg transition-colors cursor-pointer"
                                      title="Accept"
                                    >
                                      <Check size={14} />
                                    </button>
                                  </>
                                )}

                                {order.orderStatus === "PREPARING" && (
                                  <button
                                    onClick={() => handleUpdateStatus(order.id, "READY_FOR_PICKUP")}
                                    className="px-2.5 py-1 text-xs bg-blue-500/10 hover:bg-blue-500/20 text-blue-400 border border-blue-500/20 rounded-lg transition-all font-semibold cursor-pointer"
                                  >
                                    Mark Ready
                                  </button>
                                )}

                                {order.orderStatus === "READY_FOR_PICKUP" && (
                                  <button
                                    onClick={() => handleUpdateStatus(order.id, "DELIVERED")}
                                    className="px-2.5 py-1 text-xs bg-green-500/10 hover:bg-green-500/20 text-green-400 border border-green-500/20 rounded-lg transition-all font-semibold cursor-pointer"
                                  >
                                    Delivered
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        );
                      })
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Accept Order Modal */}
        {acceptOrder && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h3 className="text-lg font-bold text-slate-100 mb-2">Accept Order #{acceptOrder.id.slice(-6).toUpperCase()}</h3>
              <p className="text-slate-400 text-sm mb-6">Select estimated preparation time for kitchen setup.</p>
              
              <div className="space-y-4 mb-6">
                <div>
                  <label className="input-label">Preparation Time (minutes)</label>
                  <select
                    className="input-field"
                    value={prepMinutes}
                    onChange={(e) => setPrepMinutes(Number(e.target.value))}
                  >
                    <option value={10}>10 Minutes</option>
                    <option value={15}>15 Minutes</option>
                    <option value={20}>20 Minutes</option>
                    <option value={30}>30 Minutes</option>
                    <option value={45}>45 Minutes</option>
                    <option value={60}>60 Minutes</option>
                  </select>
                </div>
              </div>

              <div className="flex gap-3 justify-end">
                <button onClick={() => setAcceptOrder(null)} className="btn-secondary">Cancel</button>
                <button onClick={handleAccept} className="btn-primary">Confirm & Prepare</button>
              </div>
            </div>
          </div>
        )}

        {/* Reject Order Modal */}
        {rejectOrder && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h3 className="text-lg font-bold text-slate-100 mb-2">Reject Order #{rejectOrder.id.slice(-6).toUpperCase()}</h3>
              <p className="text-slate-400 text-sm mb-6">Specify rejection reason to notify the client.</p>

              <div className="space-y-4 mb-6">
                <div>
                  <label className="input-label">Rejection Reason</label>
                  <select
                    className="input-field"
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                  >
                    <option value="">-- Select Reason --</option>
                    <option value="Out of Stock">Ingredients Out of Stock</option>
                    <option value="Kitchen Overloaded">Kitchen Overloaded / Too Busy</option>
                    <option value="Restaurant Closing">Restaurant Closing Soon</option>
                    <option value="Customization Unavailable">Customization Request Unavailable</option>
                  </select>
                </div>
              </div>

              <div className="flex gap-3 justify-end">
                <button onClick={() => setRejectOrder(null)} className="btn-secondary">Cancel</button>
                <button
                  onClick={handleReject}
                  disabled={!rejectReason}
                  className="btn-danger disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Confirm Reject
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </PartnerLayout>
  );
}
