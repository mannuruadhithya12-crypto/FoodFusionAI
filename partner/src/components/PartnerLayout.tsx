"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { useRouter, usePathname } from "next/navigation";
import Link from "next/link";
import { doc, getDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { Restaurant } from "@/types";
import {
  LayoutDashboard,
  ClipboardList,
  UtensilsCrossed,
  Settings,
  Users,
  Star,
  DollarSign,
  FileText,
  LogOut,
  ChevronDown,
  Menu,
  X
} from "lucide-react";

export default function PartnerLayout({ children }: { children: React.ReactNode }) {
  const { user, profile, loading, isPartner, selectedRestaurantId, setSelectedRestaurantId, signOut } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [restaurantDropdownOpen, setRestaurantDropdownOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [currentRestaurantName, setCurrentRestaurantName] = useState("Loading...");

  useEffect(() => {
    if (!loading) {
      if (!user) {
        router.push("/login");
      } else if (!isPartner || !profile?.restaurantIds || profile.restaurantIds.length === 0) {
        router.push("/onboarding");
      }
    }
  }, [user, profile, loading, isPartner, router]);

  useEffect(() => {
    const fetchRestaurants = async () => {
      if (profile?.restaurantIds && profile.restaurantIds.length > 0) {
        const list: Restaurant[] = [];
        for (const id of profile.restaurantIds) {
          const docRef = doc(db, "restaurants", id);
          const docSnap = await getDoc(docRef);
          if (docSnap.exists()) {
            list.push({ id, ...docSnap.data() } as Restaurant);
          }
        }
        setRestaurants(list);
        
        // Find selected restaurant name
        const selected = list.find(r => r.id === selectedRestaurantId);
        if (selected) {
          setCurrentRestaurantName(selected.name);
          // If the selected restaurant is suspended or not approved, redirect to onboarding
          if (selected.approvalStatus !== "APPROVED") {
            router.push("/onboarding");
          }
        }
      }
    };
    if (profile?.restaurantIds) {
      fetchRestaurants();
    }
  }, [profile, selectedRestaurantId, router]);

  if (loading || !user || !isPartner) {
    return (
      <div className="min-height-screen bg-[#0a0a0f] flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
          <p className="text-slate-400 text-sm">Authenticating partner account...</p>
        </div>
      </div>
    );
  }

  const menuItems = [
    { name: "Dashboard", href: "/", icon: LayoutDashboard },
    { name: "Orders", href: "/orders", icon: ClipboardList },
    { name: "Menu", href: "/menu", icon: UtensilsCrossed },
    { name: "Settings", href: "/settings", icon: Settings },
    { name: "Staff", href: "/staff", icon: Users, ownerOnly: true },
    { name: "Reviews", href: "/reviews", icon: Star },
    { name: "Earnings", href: "/earnings", icon: DollarSign },
    { name: "Audit Logs", href: "/audit-logs", icon: FileText }
  ];

  return (
    <div className="min-h-screen bg-[#0a0a0f] text-slate-100 flex flex-col md:flex-row">
      {/* Mobile Top Bar */}
      <div className="md:hidden flex items-center justify-between px-6 py-4 bg-[#12121a] border-b border-white/5">
        <div className="flex items-center gap-2">
          <span className="text-orange-500 font-extrabold text-lg">FoodFusion</span>
          <span className="text-xs bg-orange-500/10 text-orange-400 px-2 py-0.5 rounded font-medium border border-orange-500/20">Partner</span>
        </div>
        <button onClick={() => setMobileMenuOpen(!mobileMenuOpen)} className="p-2 text-slate-400 hover:text-slate-200">
          {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
        </button>
      </div>

      {/* Sidebar Navigation */}
      <aside className={`fixed inset-y-0 left-0 z-40 w-64 bg-[#12121a] border-r border-white/5 flex flex-col transform transition-transform duration-300 md:relative md:translate-x-0 ${mobileMenuOpen ? "translate-x-0" : "-translate-x-full"}`}>
        {/* Sidebar Header */}
        <div className="p-6 border-b border-white/5 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-orange-500 font-extrabold text-xl">FoodFusion</span>
            <span className="text-[10px] bg-orange-500/10 text-orange-400 px-2 py-0.5 rounded font-semibold border border-orange-500/20">PARTNER</span>
          </div>
          <button onClick={() => setMobileMenuOpen(false)} className="md:hidden p-1 text-slate-400 hover:text-slate-200">
            <X size={20} />
          </button>
        </div>

        {/* Restaurant Switcher */}
        {restaurants.length > 0 && (
          <div className="px-4 py-3 border-b border-white/5 relative">
            <button 
              onClick={() => setRestaurantDropdownOpen(!restaurantDropdownOpen)}
              className="w-full flex items-center justify-between px-3 py-2 bg-white/5 border border-white/5 rounded-xl text-left hover:bg-white/10 transition-colors"
            >
              <div className="truncate">
                <p className="text-[10px] text-slate-500 font-semibold tracking-wider uppercase">Active Restaurant</p>
                <p className="text-sm font-semibold truncate text-slate-200">{currentRestaurantName}</p>
              </div>
              <ChevronDown size={16} className={`text-slate-400 transition-transform ${restaurantDropdownOpen ? "rotate-180" : ""}`} />
            </button>

            {restaurantDropdownOpen && (
              <div className="absolute left-4 right-4 mt-2 bg-[#1a1a24] border border-white/10 rounded-xl shadow-2xl z-50 py-1 overflow-hidden animate-scaleIn">
                {restaurants.map((r) => (
                  <button
                    key={r.id}
                    onClick={() => {
                      setSelectedRestaurantId(r.id);
                      setRestaurantDropdownOpen(false);
                    }}
                    className={`w-full text-left px-4 py-2 text-sm hover:bg-white/5 transition-colors ${selectedRestaurantId === r.id ? "text-orange-500 bg-orange-500/5 font-semibold" : "text-slate-300"}`}
                  >
                    {r.name}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Navigation Links */}
        <nav className="flex-1 px-4 py-6 space-y-1.5 overflow-y-auto">
          {menuItems.map((item) => {
            if (item.ownerOnly && profile?.role !== "RESTAURANT_OWNER") return null;
            const isActive = pathname === item.href;
            return (
              <Link
                key={item.name}
                href={item.href}
                onClick={() => setMobileMenuOpen(false)}
                className={`sidebar-link ${isActive ? "active" : ""}`}
              >
                <item.icon size={18} />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </nav>

        {/* User Info & Logout */}
        <div className="p-4 border-t border-white/5 bg-[#0e0e14]/50 flex items-center justify-between">
          <div className="truncate flex-1 mr-2">
            <p className="text-sm font-semibold truncate text-slate-200">{profile?.displayName || profile?.email}</p>
            <p className="text-[10px] text-slate-500 font-semibold uppercase tracking-wider">{profile?.role?.replace("RESTAURANT_", "")}</p>
          </div>
          <button 
            onClick={() => signOut().then(() => router.push("/login"))}
            className="p-2.5 rounded-xl bg-white/5 border border-white/5 text-slate-400 hover:text-red-400 hover:bg-red-500/10 hover:border-red-500/20 transition-all cursor-pointer"
            title="Sign Out"
          >
            <LogOut size={16} />
          </button>
        </div>
      </aside>

      {/* Main Workspace Area */}
      <main className="flex-1 min-w-0 p-6 md:p-10 page-enter">
        {children}
      </main>
    </div>
  );
}
