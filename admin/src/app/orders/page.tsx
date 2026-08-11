"use client";

import { useEffect, useState } from "react";
import { collection, query, orderBy, onSnapshot, doc, updateDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { Clock, CheckCircle, Truck, Package, XCircle, ClipboardList } from "lucide-react";
import { Order, OrderItem } from "../../types";

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);

  useEffect(() => {
    const q = query(collection(db, "orders"), orderBy("createdAt", "desc"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      setOrders(snapshot.docs.map(d => ({ id: d.id, ...d.data() } as Order)));
      setLoading(false);
    }, (error) => {
      console.error("Error fetching orders:", error);
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  const updateOrderStatus = async (orderId: string, newStatus: string) => {
    try {
      await updateDoc(doc(db, "orders", orderId), {
        orderStatus: newStatus,
        updatedAt: new Date().toISOString()
      });
    } catch (err) {
      console.error("Failed to update status", err);
      alert("Failed to update status");
    }
  };

  const getStatusBadge = (status: string) => {
    switch(status) {
      case "PENDING": return <span className="px-2 py-1 bg-yellow-100 text-yellow-800 rounded-full text-xs font-semibold">PENDING</span>;
      case "CONFIRMED": return <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded-full text-xs font-semibold">CONFIRMED</span>;
      case "PREPARING": return <span className="px-2 py-1 bg-purple-100 text-purple-800 rounded-full text-xs font-semibold">PREPARING</span>;
      case "READY_FOR_PICKUP": return <span className="px-2 py-1 bg-orange-100 text-orange-800 rounded-full text-xs font-semibold">READY</span>;
      case "OUT_FOR_DELIVERY": return <span className="px-2 py-1 bg-indigo-100 text-indigo-800 rounded-full text-xs font-semibold">ON THE WAY</span>;
      case "DELIVERED": return <span className="px-2 py-1 bg-green-100 text-green-800 rounded-full text-xs font-semibold">DELIVERED</span>;
      case "CANCELLED": return <span className="px-2 py-1 bg-red-100 text-red-800 rounded-full text-xs font-semibold">CANCELLED</span>;
      default: return <span className="px-2 py-1 bg-gray-100 text-gray-800 rounded-full text-xs font-semibold">{status}</span>;
    }
  };

  if (loading) return <div>Loading Orders...</div>;

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold text-gray-800">Live Orders</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Orders List */}
        <div className="lg:col-span-2 bg-white shadow rounded-lg overflow-hidden border">
          <table>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Amount</th>
                <th>Payment</th>
                <th>Status</th>
                <th>Time</th>
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 && <tr><td colSpan={5} className="p-4 text-center text-gray-500">No orders yet</td></tr>}
              {orders.map(order => (
                <tr key={order.id} onClick={() => setSelectedOrder(order)} className={`cursor-pointer hover:bg-slate-50 transition-colors ${selectedOrder?.id === order.id ? 'bg-slate-50' : ''}`}>
                  <td className="font-medium text-sm">{order.id.slice(0,8).toUpperCase()}...</td>
                  <td>₹{order.totalAmount}</td>
                  <td>
                    <span className={`text-xs font-bold ${order.paymentStatus === 'SUCCESS' ? 'text-green-600' : 'text-red-600'}`}>
                      {order.paymentStatus}
                    </span>
                  </td>
                  <td>{getStatusBadge(order.orderStatus)}</td>
                  <td className="text-sm text-gray-500">
                    {order.createdAt ? new Date(order.createdAt).toLocaleTimeString() : 'N/A'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Order Details Panel */}
        <div className="bg-white shadow rounded-lg border p-6 h-fit sticky top-6">
          {selectedOrder ? (
            <div className="space-y-6">
              <h2 className="text-xl font-bold border-b pb-2">Order Details</h2>
              
              <div className="space-y-2 text-sm">
                <p><span className="text-gray-500 font-medium">Order ID:</span> {selectedOrder.id}</p>
                <p><span className="text-gray-500 font-medium">Customer ID:</span> {selectedOrder.userId}</p>
                <p><span className="text-gray-500 font-medium">Restaurant ID:</span> {selectedOrder.restaurantId}</p>
              </div>

              <div>
                <h3 className="font-semibold text-gray-700 mb-2">Items</h3>
                <div className="bg-gray-50 p-3 rounded space-y-2 text-sm">
                  {selectedOrder.items?.map((item: OrderItem, idx: number) => (
                    <div key={idx} className="flex justify-between">
                      <span>{item.quantity}x {item.foodId}</span>
                      <span className="font-medium">₹{item.price * item.quantity}</span>
                    </div>
                  ))}
                  <div className="border-t pt-2 mt-2 flex justify-between font-bold">
                    <span>Total</span>
                    <span>₹{selectedOrder.totalAmount}</span>
                  </div>
                </div>
              </div>

              <div>
                <h3 className="font-semibold text-gray-700 mb-2">Update Status</h3>
                <div className="grid grid-cols-2 gap-2">
                  <button onClick={() => updateOrderStatus(selectedOrder.id, "CONFIRMED")} className="p-2 border rounded hover:bg-gray-50 text-sm font-medium flex items-center justify-center gap-1">
                    <CheckCircle size={14} className="text-blue-500"/> Confirm
                  </button>
                  <button onClick={() => updateOrderStatus(selectedOrder.id, "PREPARING")} className="p-2 border rounded hover:bg-gray-50 text-sm font-medium flex items-center justify-center gap-1">
                    <Clock size={14} className="text-purple-500"/> Preparing
                  </button>
                  <button onClick={() => updateOrderStatus(selectedOrder.id, "READY_FOR_PICKUP")} className="p-2 border rounded hover:bg-gray-50 text-sm font-medium flex items-center justify-center gap-1">
                    <Package size={14} className="text-orange-500"/> Ready
                  </button>
                  <button onClick={() => updateOrderStatus(selectedOrder.id, "OUT_FOR_DELIVERY")} className="p-2 border rounded hover:bg-gray-50 text-sm font-medium flex items-center justify-center gap-1">
                    <Truck size={14} className="text-indigo-500"/> Out for Dev
                  </button>
                  <button onClick={() => updateOrderStatus(selectedOrder.id, "DELIVERED")} className="p-2 border rounded hover:bg-gray-50 text-sm font-medium flex items-center justify-center gap-1 col-span-2 bg-green-50 hover:bg-green-100 text-green-700 border-green-200">
                    <CheckCircle size={14}/> Delivered
                  </button>
                  <button onClick={() => updateOrderStatus(selectedOrder.id, "CANCELLED")} className="p-2 border rounded hover:bg-gray-50 text-sm font-medium flex items-center justify-center gap-1 col-span-2 text-red-600 border-red-200 hover:bg-red-50">
                    <XCircle size={14}/> Cancel Order
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <div className="h-full flex flex-col items-center justify-center text-gray-400 py-12">
              <ClipboardList size={48} className="mb-4 opacity-50" />
              <p>Select an order to view details</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
