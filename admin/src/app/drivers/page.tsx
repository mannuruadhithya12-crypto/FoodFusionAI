"use client";

import { useEffect, useState } from "react";
import { collection, query, onSnapshot, orderBy, doc, updateDoc, deleteField } from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { db, functions } from "../../../lib/firebase";
import { 
  Users, 
  Store, 
  Truck, 
  CheckCircle, 
  XCircle, 
  AlertTriangle, 
  ShieldAlert,
  ClipboardList,
  ShieldCheck,
  Ban,
  Activity,
  DollarSign
} from "lucide-react";
import { Driver, Order } from "../../types";

export default function DriversPage() {
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [activeOrders, setActiveOrders] = useState<Order[]>([]);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);

  useEffect(() => {
    // 1. Listen to drivers
    const qDrivers = query(collection(db, "drivers"), orderBy("createdAt", "desc"));
    const unsubDrivers = onSnapshot(qDrivers, (snap) => {
      setDrivers(snap.docs.map(d => ({ uid: d.id, ...d.data() } as Driver)));
    });

    // 2. Listen to orders
    const qOrders = query(collection(db, "orders"), orderBy("createdAt", "desc"));
    const unsubOrders = onSnapshot(qOrders, (snap) => {
      setActiveOrders(snap.docs.map(d => ({ id: d.id, ...d.data() } as Order)));
    });

    // 3. Listen to audit logs
    const qLogs = query(collection(db, "auditLogs"), orderBy("timestamp", "desc"));
    const unsubLogs = onSnapshot(qLogs, (snap) => {
      setAuditLogs(snap.docs.map(d => ({ id: d.id, ...d.data() })));
      setLoading(false);
    });

    return () => {
      unsubDrivers();
      unsubOrders();
      unsubLogs();
    };
  }, []);

  const handleApprove = async (driverId: string) => {
    setProcessingId(driverId);
    try {
      const approve = httpsCallable(functions, "approveDriver");
      await approve({ driverId });
      alert("Driver approved successfully!");
    } catch (err: any) {
      alert("Failed to approve driver: " + err.message);
    } finally {
      setProcessingId(null);
    }
  };

  const handleSuspendToggle = async (driverId: string, currentStatus: string) => {
    setProcessingId(driverId);
    const suspend = currentStatus !== "SUSPENDED";
    try {
      const suspendFn = httpsCallable(functions, "suspendDriver");
      await suspendFn({ driverId, suspend });
      alert(`Driver status updated successfully!`);
    } catch (err: any) {
      alert("Failed to update status: " + err.message);
    } finally {
      setProcessingId(null);
    }
  };

  const resolveIssue = async (orderId: string, action: "CANCEL" | "REASSIGN") => {
    try {
      if (action === "CANCEL") {
        await updateDoc(doc(db, "orders", orderId), {
          orderStatus: "CANCELLED",
          updatedAt: new Date().toISOString(),
          deliveryIssue: deleteField()
        });
        alert("Order cancelled and resolved.");
      } else {
        // Re-assign order back to READY_FOR_PICKUP and clear driver
        await updateDoc(doc(db, "orders", orderId), {
          orderStatus: "READY_FOR_PICKUP",
          deliveryPartner: deleteField(),
          deliveryIssue: deleteField(),
          updatedAt: new Date().toISOString()
        });
        
        const assignFn = httpsCallable(functions, "assignDriverToOrder");
        await assignFn({ orderId });
        alert("Driver unassigned and order re-offered to available drivers.");
      }
    } catch (err: any) {
      alert("Failed to resolve issue: " + err.message);
    }
  };

  const [operationsAlerts, setOperationsAlerts] = useState<any[]>([]);
  const [deliveryZones, setDeliveryZones] = useState<any[]>([]);
  const [manualOrderId, setManualOrderId] = useState("");
  const [manualDriverId, setManualDriverId] = useState("");
  const [reassignReason, setReassignReason] = useState("");

  useEffect(() => {
    // 4. Listen to operations alerts
    const qAlerts = query(collection(db, "operationsAlerts"), orderBy("createdAt", "desc"));
    const unsubAlerts = onSnapshot(qAlerts, (snap) => {
      setOperationsAlerts(snap.docs.map(d => ({ id: d.id, ...d.data() })));
    });

    // 5. Listen to delivery zones
    const qZones = query(collection(db, "deliveryZones"));
    const unsubZones = onSnapshot(qZones, (snap) => {
      setDeliveryZones(snap.docs.map(d => ({ id: d.id, ...d.data() })));
    });

    return () => {
      unsubAlerts();
      unsubZones();
    };
  }, []);

  const handleManualDispatch = async () => {
    if (!manualOrderId) return alert("Enter an order ID");
    setProcessingId("manual-dispatch");
    try {
      const dispatch = httpsCallable(functions, "dispatchReadyOrder");
      const res = await dispatch({ orderId: manualOrderId }) as any;
      if (res.data?.success) {
        alert(`Order dispatched! Score: ${res.data.score?.toFixed(1)}`);
      } else {
        alert(res.data?.message || "No drivers available.");
      }
    } catch (err: any) {
      alert("Dispatch failed: " + err.message);
    } finally {
      setProcessingId(null);
      setManualOrderId("");
    }
  };

  const handleReassign = async () => {
    if (!manualOrderId || !reassignReason) return alert("Enter an order ID and reason");
    setProcessingId("reassign");
    try {
      const reassign = httpsCallable(functions, "reassignDriver");
      await reassign({ orderId: manualOrderId, reason: reassignReason });
      alert("Driver reassigned successfully.");
    } catch (err: any) {
      alert("Reassign failed: " + err.message);
    } finally {
      setProcessingId(null);
      setManualOrderId("");
      setReassignReason("");
    }
  };

  const handleDetectDelays = async () => {
    setProcessingId("detect-delays");
    try {
      const detect = httpsCallable(functions, "detectDeliveryDelay");
      const res = await detect({}) as any;
      alert(`Scanned ${res.data?.scannedCount} orders. Flagged ${res.data?.flaggedDelayCount} delays.`);
    } catch (err: any) {
      alert("Delay detection failed: " + err.message);
    } finally {
      setProcessingId(null);
    }
  };

  if (loading) return <div className="p-6 text-center text-zinc-500 font-sans">Loading Driver Operations Board...</div>;

  const totalDrivers = drivers.length;
  const onlineDrivers = drivers.filter(d => d.availability === "ONLINE").length;
  const busyDrivers = drivers.filter(d => d.availability === "BUSY").length;
  const pendingApprovals = drivers.filter(d => d.status === "PENDING").length;

  const outForDeliveryOrders = activeOrders.filter(o => o.orderStatus === "OUT_FOR_DELIVERY");
  const issueOrders = activeOrders.filter(o => o.deliveryIssue !== undefined);
  const delayedOrders = activeOrders.filter(o => (o as any).deliveryStatus === "DELAYED" || (o as any).deliveryStatus === "CRITICAL");
  const unassignedOrders = activeOrders.filter(o => o.orderStatus === "READY_FOR_PICKUP" && !o.deliveryPartner);
  const unresolvedAlerts = operationsAlerts.filter(a => a.status === "UNRESOLVED");

  // GPS health: check driver lastLocation freshness
  const now = Date.now();
  const staleGpsDrivers = drivers.filter(d => {
    if (!d.lastLocation?.updatedAt) return false;
    const ageSec = (now - d.lastLocation.updatedAt) / 1000;
    return ageSec > 60 && ageSec <= 300;
  });
  const offlineGpsDrivers = drivers.filter(d => {
    if (!d.lastLocation?.updatedAt) return d.availability === "ONLINE";
    const ageSec = (now - d.lastLocation.updatedAt) / 1000;
    return ageSec > 300;
  });

  return (
    <div className="space-y-8 font-sans p-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-zinc-950">Driver Operations</h1>
          <p className="text-zinc-500 text-sm">Monitor driver registrations, live tracking, audit logs, and delivery issues.</p>
        </div>
      </div>

      {/* Metrics Banner */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white p-6 rounded-xl border border-zinc-200 shadow-sm flex items-center space-x-4">
          <div className="p-3 rounded-full text-blue-600 bg-blue-50">
            <Users size={24} />
          </div>
          <div>
            <p className="text-xs text-zinc-500 font-medium">Total Registered</p>
            <p className="text-2xl font-black text-zinc-900">{totalDrivers}</p>
          </div>
        </div>

        <div className="bg-white p-6 rounded-xl border border-zinc-200 shadow-sm flex items-center space-x-4">
          <div className="p-3 rounded-full text-green-600 bg-green-50">
            <Activity size={24} className="animate-pulse" />
          </div>
          <div>
            <p className="text-xs text-zinc-500 font-medium">Online (Idle)</p>
            <p className="text-2xl font-black text-zinc-900">{onlineDrivers}</p>
          </div>
        </div>

        <div className="bg-white p-6 rounded-xl border border-zinc-200 shadow-sm flex items-center space-x-4">
          <div className="p-3 rounded-full text-orange-600 bg-orange-50">
            <Truck size={24} />
          </div>
          <div>
            <p className="text-xs text-zinc-500 font-medium">Busy (Delivering)</p>
            <p className="text-2xl font-black text-zinc-900">{busyDrivers}</p>
          </div>
        </div>

        <div className="bg-white p-6 rounded-xl border border-zinc-200 shadow-sm flex items-center space-x-4">
          <div className="p-3 rounded-full text-purple-600 bg-purple-50">
            <ShieldCheck size={24} />
          </div>
          <div>
            <p className="text-xs text-zinc-500 font-medium">Pending Verification</p>
            <p className="text-2xl font-black text-zinc-900">{pendingApprovals}</p>
          </div>
        </div>
      </div>

      {/* Phase 14: Operations Center Extended Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        <div className="bg-white p-4 rounded-xl border border-zinc-200 shadow-sm text-center">
          <p className="text-xs text-zinc-500 font-medium">Delayed Orders</p>
          <p className="text-2xl font-black text-red-600">{delayedOrders.length}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-zinc-200 shadow-sm text-center">
          <p className="text-xs text-zinc-500 font-medium">Unassigned Orders</p>
          <p className="text-2xl font-black text-amber-600">{unassignedOrders.length}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-zinc-200 shadow-sm text-center">
          <p className="text-xs text-zinc-500 font-medium">GPS Stale ({'>'}60s)</p>
          <p className="text-2xl font-black text-yellow-600">{staleGpsDrivers.length}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-zinc-200 shadow-sm text-center">
          <p className="text-xs text-zinc-500 font-medium">GPS Offline ({'>'}5m)</p>
          <p className="text-2xl font-black text-red-700">{offlineGpsDrivers.length}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-zinc-200 shadow-sm text-center">
          <p className="text-xs text-zinc-500 font-medium">Unresolved Alerts</p>
          <p className="text-2xl font-black text-orange-600">{unresolvedAlerts.length}</p>
        </div>
      </div>

      {/* Manual Dispatch & Reassignment Controls */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-zinc-200 shadow-sm p-6 space-y-4">
          <h3 className="font-bold text-zinc-900 flex items-center gap-2">
            <Truck className="text-orange-500" size={20} /> Manual Dispatch / Reassignment
          </h3>
          <div className="space-y-3">
            <input
              type="text"
              value={manualOrderId}
              onChange={e => setManualOrderId(e.target.value)}
              placeholder="Order ID"
              className="w-full px-3 py-2 border rounded-lg text-sm"
            />
            <div className="flex gap-2">
              <button
                onClick={handleManualDispatch}
                disabled={processingId === "manual-dispatch"}
                className="flex-1 py-2 bg-orange-600 hover:bg-orange-700 text-white rounded-lg text-xs font-bold"
              >
                Dispatch Order
              </button>
              <button
                onClick={handleDetectDelays}
                disabled={processingId === "detect-delays"}
                className="flex-1 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-bold"
              >
                Scan Delays
              </button>
            </div>
            <input
              type="text"
              value={reassignReason}
              onChange={e => setReassignReason(e.target.value)}
              placeholder="Reason for reassignment"
              className="w-full px-3 py-2 border rounded-lg text-sm"
            />
            <button
              onClick={handleReassign}
              disabled={processingId === "reassign" || !reassignReason}
              className="w-full py-2 bg-zinc-800 hover:bg-zinc-900 text-white rounded-lg text-xs font-bold"
            >
              Reassign Driver on Order
            </button>
          </div>
        </div>

        {/* Delivery Zones Panel */}
        <div className="bg-white rounded-xl border border-zinc-200 shadow-sm p-6 space-y-4">
          <h3 className="font-bold text-zinc-900">Delivery Zones ({deliveryZones.length})</h3>
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {deliveryZones.length === 0 && (
              <p className="text-xs text-zinc-500 italic">No delivery zones configured. Add zones via Firestore.</p>
            )}
            {deliveryZones.map(zone => (
              <div key={zone.id} className="border border-zinc-100 rounded-lg p-3 text-xs space-y-1">
                <div className="flex justify-between">
                  <span className="font-bold">{zone.zoneName || zone.id}</span>
                  <span className={`px-2 py-0.5 rounded-full font-bold ${zone.active ? 'bg-green-100 text-green-800' : 'bg-zinc-100 text-zinc-500'}`}>
                    {zone.active ? 'ACTIVE' : 'INACTIVE'}
                  </span>
                </div>
                <p className="text-zinc-400">Radius: {zone.radiusKm} km | Fee: ₹{zone.deliveryFee} | Min: ₹{zone.minimumOrder}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Left Side: Drivers list and approvals (2 cols) */}
        <div className="lg:col-span-2 space-y-8">
          
          {/* 1. Pending Approvals list */}
          {pendingApprovals > 0 && (
            <div className="bg-orange-50/50 border border-orange-200 rounded-xl p-6 space-y-4">
              <h2 className="text-lg font-bold text-orange-800 flex items-center gap-2">
                <ShieldCheck /> Awaiting Document Verification ({pendingApprovals})
              </h2>

              <div className="space-y-3">
                {drivers.filter(d => d.status === "PENDING").map(driver => (
                  <div key={driver.uid} className="bg-white p-4 rounded-lg border border-orange-200 flex justify-between items-center text-sm shadow-sm">
                    <div>
                      <p className="font-bold text-zinc-900">{driver.name}</p>
                      <p className="text-zinc-500 text-xs">Email: {driver.email} | Phone: {driver.phone}</p>
                      <p className="text-xs text-zinc-400 mt-1">Vehicle: {driver.vehicleType} ({driver.vehicleNumber}) | License: {driver.licenseNumber}</p>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleApprove(driver.uid)}
                        disabled={processingId === driver.uid}
                        className="py-1.5 px-4 bg-orange-600 hover:bg-orange-700 text-white rounded-lg text-xs font-bold"
                      >
                        APPROVE
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 2. Full Drivers List */}
          <div className="bg-white rounded-xl border border-zinc-200 shadow-sm overflow-hidden">
            <div className="p-5 border-b border-zinc-100 flex justify-between items-center">
              <h3 className="font-bold text-zinc-900">Registered Driver List</h3>
            </div>
            
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-zinc-50 border-b border-zinc-100 text-xs font-bold text-zinc-400">
                  <th className="p-4">NAME</th>
                  <th className="p-4">VEHICLE</th>
                  <th className="p-4">STATUS</th>
                  <th className="p-4">AVAILABILITY</th>
                  <th className="p-4 text-right">ACTION</th>
                </tr>
              </thead>
              <tbody className="text-sm">
                {drivers.length === 0 && (
                  <tr><td colSpan={5} className="p-4 text-center text-zinc-500">No drivers registered yet.</td></tr>
                )}
                {drivers.map(driver => (
                  <tr key={driver.uid} className="border-b border-zinc-50 hover:bg-zinc-50/50">
                    <td className="p-4">
                      <p className="font-semibold text-zinc-900">{driver.name}</p>
                      <p className="text-xs text-zinc-400">{driver.email} | {driver.phone}</p>
                    </td>
                    <td className="p-4 text-xs font-medium">
                      <p>{driver.vehicleType}</p>
                      <p className="text-zinc-400">{driver.vehicleNumber}</p>
                    </td>
                    <td className="p-4">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${
                        driver.status === 'APPROVED' ? 'bg-green-100 text-green-800' :
                        driver.status === 'PENDING' ? 'bg-orange-100 text-orange-800' :
                        'bg-red-100 text-red-800'
                      }`}>
                        {driver.status}
                      </span>
                    </td>
                    <td className="p-4">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                        driver.availability === 'ONLINE' ? 'bg-emerald-100 text-emerald-800' :
                        driver.availability === 'BUSY' ? 'bg-amber-100 text-amber-800' :
                        'bg-zinc-100 text-zinc-500'
                      }`}>
                        {driver.availability}
                      </span>
                    </td>
                    <td className="p-4 text-right">
                      {driver.status !== "PENDING" && (
                        <button
                          onClick={() => handleSuspendToggle(driver.uid, driver.status)}
                          disabled={processingId === driver.uid}
                          className={`text-xs py-1.5 px-3 rounded-lg font-bold border ${
                            driver.status === "SUSPENDED" 
                              ? "border-green-200 text-green-700 bg-green-50 hover:bg-green-100"
                              : "border-red-200 text-red-600 bg-red-50 hover:bg-red-100"
                          }`}
                        >
                          {driver.status === "SUSPENDED" ? "UNSUSPEND" : "SUSPEND"}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 3. Reported Delivery Issues */}
          <div className="bg-red-50/20 border border-red-200 rounded-xl p-6 space-y-4">
            <h2 className="text-lg font-bold text-red-800 flex items-center gap-2">
              <ShieldAlert /> Live Delivery Issues / Failures ({issueOrders.length})
            </h2>

            <div className="space-y-4">
              {issueOrders.length === 0 && (
                <p className="text-sm text-zinc-500 italic">No delivery issues currently flagged.</p>
              )}
              {issueOrders.map(order => (
                <div key={order.id} className="bg-white border border-red-200 p-5 rounded-lg space-y-3 shadow-sm text-sm">
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="font-extrabold">ORDER ID: #{order.id.slice(0,8).toUpperCase()}</p>
                      <p className="text-xs text-zinc-400">Assigned Driver: {order.deliveryPartner?.name || "None"}</p>
                    </div>
                    <span className="px-2 py-0.5 bg-red-100 text-red-700 font-bold rounded text-xs">
                      {order.deliveryIssue?.reason}
                    </span>
                  </div>

                  <p className="text-zinc-600 text-xs bg-zinc-50 p-2.5 rounded border border-zinc-100">
                    {order.deliveryIssue?.description || "No detail provided"}
                  </p>

                  <div className="flex gap-2">
                    <button
                      onClick={() => resolveIssue(order.id, "REASSIGN")}
                      className="py-1 px-3 border border-orange-300 hover:bg-orange-50 text-orange-700 text-xs font-bold rounded-lg"
                    >
                      Unassign &amp; Re-Offer
                    </button>
                    <button
                      onClick={() => resolveIssue(order.id, "CANCEL")}
                      className="py-1 px-3 border border-red-300 hover:bg-red-50 text-red-600 text-xs font-bold rounded-lg"
                    >
                      Cancel Order
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

        </div>

        {/* Right Side: Live deliveries & audit logs (1 col) */}
        <div className="space-y-8">
          
          {/* 1. Live Deliveries (OUT_FOR_DELIVERY) */}
          <div className="bg-white rounded-xl border border-zinc-200 shadow-sm p-6 space-y-4">
            <h3 className="font-bold text-zinc-950 flex items-center gap-2">
              <Truck className="text-orange-500" /> Active Deliveries ({outForDeliveryOrders.length})
            </h3>

            <div className="space-y-3 max-h-96 overflow-y-auto">
              {outForDeliveryOrders.length === 0 && (
                <p className="text-xs text-zinc-500 italic text-center py-8">No deliveries currently in transit.</p>
              )}
              {outForDeliveryOrders.map(order => (
                <div key={order.id} className="border-b last:border-0 border-zinc-100 pb-3 text-xs space-y-1.5">
                  <div className="flex justify-between font-bold">
                    <span>#{order.id.slice(0,8).toUpperCase()}</span>
                    <span className="text-orange-500">IN TRANSIT</span>
                  </div>
                  <p className="text-zinc-500">Driver: {order.deliveryPartner?.name || "N/A"}</p>
                  <p className="text-zinc-400">Total amount: ₹{order.totalAmount}</p>
                  {order.deliveryOtp && (
                    <p className="font-bold text-zinc-600">Verification OTP: {order.deliveryOtp}</p>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* 2. Driver Audit Logs */}
          <div className="bg-white rounded-xl border border-zinc-200 shadow-sm p-6 space-y-4">
            <h3 className="font-bold text-zinc-950 flex items-center gap-2">
              <ClipboardList className="text-zinc-600" /> Audit Log Timeline
            </h3>

            <div className="space-y-4 max-h-96 overflow-y-auto pr-1">
              {auditLogs.length === 0 && (
                <p className="text-xs text-zinc-500 italic text-center py-8">No operational logs recorded.</p>
              )}
              {auditLogs.map(log => (
                <div key={log.id} className="relative pl-5 border-l-2 border-zinc-200 pb-4 text-xs space-y-1">
                  <div className="absolute w-2.5 h-2.5 bg-orange-600 rounded-full -left-[6px] top-1"></div>
                  <div className="flex justify-between font-bold">
                    <span>{log.action}</span>
                    <span className="text-zinc-400 font-normal">
                      {log.timestamp?.toDate ? log.timestamp.toDate().toLocaleTimeString() : "N/A"}
                    </span>
                  </div>
                  <p className="text-zinc-500">Actor: {log.actorRole} | ID: {log.driverId || log.orderId || "System"}</p>
                </div>
              ))}
            </div>
          </div>

        </div>

      </div>
    </div>
  );
}
