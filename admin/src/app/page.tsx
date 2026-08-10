"use client";

import { useEffect, useState } from "react";
import { collection, getDocs } from "firebase/firestore";
import { db } from "../../lib/firebase";
import { Users, Store, Pizza, ShoppingBag, DollarSign } from "lucide-react";

export default function Dashboard() {
  const [metrics, setMetrics] = useState({
    totalUsers: 0,
    totalRestaurants: 0,
    totalFoods: 0,
    totalOrders: 0,
    todayRevenue: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchMetrics() {
      try {
        const usersSnap = await getDocs(collection(db, "users"));
        const restsSnap = await getDocs(collection(db, "restaurants"));
        const foodsSnap = await getDocs(collection(db, "foods"));
        const ordersSnap = await getDocs(collection(db, "orders"));
        
        let revenue = 0;
        ordersSnap.forEach((doc) => {
           const data = doc.data();
           if (data.paymentStatus === "SUCCESS") {
               revenue += data.totalAmount || 0;
           }
        });
        
        setMetrics({
          totalUsers: usersSnap.size,
          totalRestaurants: restsSnap.size,
          totalFoods: foodsSnap.size,
          totalOrders: ordersSnap.size,
          todayRevenue: revenue,
        });
      } catch (error) {
        console.error("Failed to load metrics", error);
      } finally {
        setLoading(false);
      }
    }
    
    fetchMetrics();
  }, []);

  if (loading) {
    return <div>Loading Dashboard...</div>;
  }

  const statCards = [
    { title: "Total Users", value: metrics.totalUsers, icon: Users, color: "bg-blue-500" },
    { title: "Total Restaurants", value: metrics.totalRestaurants, icon: Store, color: "bg-green-500" },
    { title: "Total Foods", value: metrics.totalFoods, icon: Pizza, color: "bg-yellow-500" },
    { title: "Total Orders", value: metrics.totalOrders, icon: ShoppingBag, color: "bg-purple-500" },
    { title: "Revenue", value: `₹${metrics.todayRevenue}`, icon: DollarSign, color: "bg-indigo-500" },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold text-gray-800">Dashboard</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-6">
        {statCards.map((stat, idx) => {
          const Icon = stat.icon;
          return (
            <div key={idx} className="bg-white p-6 rounded-xl shadow-sm border flex items-center space-x-4">
              <div className={`p-3 rounded-full text-white ${stat.color}`}>
                <Icon size={24} />
              </div>
              <div>
                <p className="text-sm text-gray-500 font-medium">{stat.title}</p>
                <p className="text-2xl font-bold text-gray-900">{stat.value === 0 && typeof stat.value === 'number' ? 'No data' : stat.value}</p>
              </div>
            </div>
          );
        })}
      </div>

      <div className="mt-8 bg-white p-6 rounded-xl shadow-sm border h-64 flex items-center justify-center">
        <p className="text-gray-500">More detailed charts will go here in future phases.</p>
      </div>
    </div>
  );
}
