"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { useRouter } from "next/navigation";
import { doc, getDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { Restaurant } from "@/types";
import { Clock, AlertTriangle, RefreshCw, XCircle, LogOut } from "lucide-react";

export default function OnboardingPage() {
  const { user, profile, selectedRestaurantId, refreshProfile, signOut } = useAuth();
  const router = useRouter();
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [loading, setLoading] = useState(true);
  const [checking, setChecking] = useState(false);

  const checkStatus = async () => {
    if (!selectedRestaurantId) {
      setLoading(false);
      return;
    }
    setChecking(true);
    try {
      const docRef = doc(db, "restaurants", selectedRestaurantId);
      const docSnap = await getDoc(docRef);
      if (docSnap.exists()) {
        const data = docSnap.data() as Restaurant;
        setRestaurant(data);
        if (data.approvalStatus === "APPROVED") {
          await refreshProfile();
          router.push("/");
        }
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
      setChecking(false);
    }
  };

  useEffect(() => {
    checkStatus();
  }, [selectedRestaurantId]);

  const handleSignOut = async () => {
    await signOut();
    router.push("/login");
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0a0a0f] flex items-center justify-center">
        <div className="w-10 h-10 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
      </div>
    );
  }

  // Handle case where user is logged in but doesn't have a restaurantId assigned
  if (!selectedRestaurantId) {
    return (
      <div className="min-h-screen bg-[#0a0a0f] bg-radial-at-t from-[#1b1b26] to-[#0a0a0f] flex items-center justify-center p-6">
        <div className="w-full max-w-md glass-card p-8 text-center">
          <AlertTriangle className="mx-auto text-amber-500 mb-4" size={48} />
          <h2 className="text-xl font-bold text-slate-100 mb-2">No Restaurant Assigned</h2>
          <p className="text-slate-400 text-sm mb-6">
            Your partner account has been created, but it is not associated with any restaurant profile. Please contact administrators to assign your account.
          </p>
          <div className="flex flex-col gap-3">
            <button onClick={checkStatus} className="btn-primary justify-center w-full py-2.5">
              <RefreshCw size={16} className={checking ? "animate-spin" : ""} /> Retry Check
            </button>
            <button onClick={handleSignOut} className="btn-secondary justify-center w-full py-2.5 text-red-400 border-red-500/10 hover:bg-red-500/5">
              <LogOut size={16} /> Sign Out
            </button>
          </div>
        </div>
      </div>
    );
  }

  const status = restaurant?.approvalStatus || "PENDING";

  return (
    <div className="min-h-screen bg-[#0a0a0f] bg-radial-at-t from-[#1b1b26] to-[#0a0a0f] flex items-center justify-center p-6">
      <div className="w-full max-w-md glass-card p-8 text-center relative overflow-hidden">
        {status === "PENDING" && (
          <div>
            <div className="w-16 h-16 mx-auto bg-orange-500/10 border border-orange-500/20 text-orange-500 rounded-full flex items-center justify-center mb-6">
              <Clock size={32} className="animate-pulse" />
            </div>
            <h2 className="text-2xl font-bold text-slate-100 mb-2">Approval Pending</h2>
            <p className="text-slate-400 text-sm mb-6">
              Your restaurant registration for <strong className="text-slate-200">{restaurant?.name}</strong> is currently being reviewed by our administrators. You will gain access as soon as it is approved.
            </p>
          </div>
        )}

        {status === "REJECTED" && (
          <div>
            <div className="w-16 h-16 mx-auto bg-red-500/10 border border-red-500/20 text-red-500 rounded-full flex items-center justify-center mb-6">
              <XCircle size={32} />
            </div>
            <h2 className="text-2xl font-bold text-slate-100 mb-2">Application Rejected</h2>
            <p className="text-slate-400 text-sm mb-4">
              Unfortunately, your partner request has been rejected.
            </p>
            {restaurant?.suspensionReason && (
              <div className="mb-6 p-4 bg-red-500/5 border border-red-500/10 rounded-xl text-left">
                <p className="text-xs text-red-400/70 font-semibold mb-1">Reason for Rejection:</p>
                <p className="text-sm text-slate-300">{restaurant.suspensionReason}</p>
              </div>
            )}
          </div>
        )}

        {status === "SUSPENDED" && (
          <div>
            <div className="w-16 h-16 mx-auto bg-red-500/10 border border-red-500/20 text-red-500 rounded-full flex items-center justify-center mb-6 animate-bounce">
              <AlertTriangle size={32} />
            </div>
            <h2 className="text-2xl font-bold text-slate-100 mb-2">Account Suspended</h2>
            <p className="text-slate-400 text-sm mb-4">
              Your restaurant account <strong className="text-slate-200">{restaurant?.name}</strong> is suspended.
            </p>
            {restaurant?.suspensionReason && (
              <div className="mb-6 p-4 bg-red-500/5 border border-red-500/10 rounded-xl text-left">
                <p className="text-xs text-red-400/70 font-semibold mb-1">Reason for Suspension:</p>
                <p className="text-sm text-slate-300">{restaurant.suspensionReason}</p>
              </div>
            )}
          </div>
        )}

        <div className="flex flex-col gap-3">
          <button
            onClick={checkStatus}
            disabled={checking}
            className="btn-primary justify-center w-full py-2.5"
          >
            <RefreshCw size={16} className={checking ? "animate-spin" : ""} />
            {checking ? "Checking..." : "Refresh Status"}
          </button>
          <button
            onClick={handleSignOut}
            className="btn-secondary justify-center w-full py-2.5 text-slate-400"
          >
            <LogOut size={16} /> Sign Out
          </button>
        </div>
      </div>
    </div>
  );
}
