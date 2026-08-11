"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { collection, query, where, onSnapshot, doc, getDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { Order } from "@/types";
import { DollarSign, IndianRupee, ClipboardList, TrendingUp } from "lucide-react";

export default function EarningsPage() {
  const { selectedRestaurantId } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  // Financial aggregates
  const [grossSales, setGrossSales] = useState(0);
  const [platformCommission, setPlatformCommission] = useState(0);
  const [netEarnings, setNetEarnings] = useState(0);
  const [commissionRate, setCommissionRate] = useState(0.15); // Default to 15%

  useEffect(() => {
    if (!selectedRestaurantId) return;

    setLoading(true);
    // Listen to orders that are completed / delivered or out for delivery
    const q = query(
      collection(db, "orders"),
      where("restaurantId", "==", selectedRestaurantId),
      where("orderStatus", "in", ["OUT_FOR_DELIVERY", "DELIVERED"])
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list: Order[] = [];
      let totalSales = 0;

      snapshot.forEach((doc) => {
        const data = doc.data();
        const o = { id: doc.id, ...data } as Order;
        list.push(o);
        totalSales += o.totalAmount || 0;
      });

      // Sort by date desc
      list.sort((a, b) => {
        const timeA = a.createdAt?.seconds ? a.createdAt.seconds * 1000 : a.createdAt;
        const timeB = b.createdAt?.seconds ? b.createdAt.seconds * 1000 : b.createdAt;
        return (timeB || 0) - (timeA || 0);
      });

      setOrders(list);
      setGrossSales(totalSales);
      
      // Fetch dynamic commission rate
      getDoc(doc(db, "restaurants", selectedRestaurantId)).then(restaurantDoc => {
        let rate = 0.15;
        if (restaurantDoc.exists()) {
           rate = restaurantDoc.data().commission ?? 0.15;
        }
        setCommissionRate(rate);
        const comm = totalSales * rate;
        setPlatformCommission(comm);
        setNetEarnings(totalSales - comm);
        setLoading(false);
      }).catch(err => {
        console.error("Failed to fetch commission", err);
        const comm = totalSales * 0.15;
        setPlatformCommission(comm);
        setNetEarnings(totalSales - comm);
        setLoading(false);
      });
    });

    return unsubscribe;
  }, [selectedRestaurantId]);

  return (
    <PartnerLayout>
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight flex items-center gap-2">
            <DollarSign className="text-orange-500" /> Financial Statements
          </h1>
          <p className="text-slate-400 text-sm font-medium">Track total income, platform commissions, and cashouts.</p>
        </div>

        {loading ? (
          <div className="flex justify-center items-center py-32">
            <div className="w-10 h-10 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="glass-card stat-card-blue p-6">
                <p className="text-xs font-bold text-blue-400 uppercase tracking-wider mb-2">Gross Sales Volume</p>
                <p className="text-3xl font-black text-slate-100 flex items-center gap-1">
                  <IndianRupee size={24} /> {grossSales.toLocaleString("en-IN")}
                </p>
                <p className="text-[10px] text-slate-500 mt-2 font-medium">Total volume of completed sales transactions.</p>
              </div>

              <div className="glass-card stat-card-red p-6">
                <p className="text-xs font-bold text-red-400 uppercase tracking-wider mb-2">Platform Fee ({(commissionRate * 100).toFixed(0)}%)</p>
                <p className="text-3xl font-black text-slate-100 flex items-center gap-1">
                  <IndianRupee size={24} /> {platformCommission.toLocaleString("en-IN")}
                </p>
                <p className="text-[10px] text-slate-500 mt-2 font-medium">FoodFusion marketplace operational commission.</p>
              </div>

              <div className="glass-card stat-card-green p-6">
                <p className="text-xs font-bold text-green-400 uppercase tracking-wider mb-2">Net Cashout Balance</p>
                <p className="text-3xl font-black text-slate-100 flex items-center gap-1">
                  <IndianRupee size={24} /> {netEarnings.toLocaleString("en-IN")}
                </p>
                <p className="text-[10px] text-slate-500 mt-2 font-medium">Settled net funds dispatched to partner bank account.</p>
              </div>
            </div>

            {/* Completed list */}
            <div className="glass-card p-6">
              <h2 className="text-base font-bold text-slate-200 mb-4 flex items-center gap-1.5">
                <ClipboardList size={18} className="text-orange-500" /> Dispatch History Ledger
              </h2>

              {orders.length === 0 ? (
                <div className="text-center py-12 text-slate-500 text-sm">
                  No completed transactions recorded yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-sm">
                    <thead>
                      <tr className="border-b border-white/5 text-slate-400 text-xs font-bold uppercase tracking-wider">
                        <th className="py-3 px-4">Order ID</th>
                        <th className="py-3 px-4">Date</th>
                        <th className="py-3 px-4">Gross Amt</th>
                        <th className="py-3 px-4">Net Payout</th>
                        <th className="py-3 px-4 text-right">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/5 text-slate-300">
                      {orders.map((o) => {
                        const date = new Date(o.createdAt?.seconds ? o.createdAt.seconds * 1000 : o.createdAt);
                        const netPay = o.totalAmount * (1 - commissionRate);
                        return (
                          <tr key={o.id} className="hover:bg-white/5 transition-colors">
                            <td className="py-3 px-4 font-semibold text-slate-200">
                              #{o.id.slice(-6).toUpperCase()}
                            </td>
                            <td className="py-3 px-4 text-slate-400 text-xs">
                              {date.toLocaleString()}
                            </td>
                            <td className="py-3 px-4 font-bold text-slate-200">
                              ₹{o.totalAmount}
                            </td>
                            <td className="py-3 px-4 font-bold text-green-400">
                              ₹{netPay.toFixed(1)}
                            </td>
                            <td className="py-3 px-4 text-right">
                              <span className="badge badge-green text-[10px] uppercase font-bold">
                                Settled
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </PartnerLayout>
  );
}
