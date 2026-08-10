"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { collection, query, where, onSnapshot } from "firebase/firestore";
import { db, functions } from "@/lib/firebase";
import { httpsCallable } from "firebase/functions";
import { User } from "@/types";
import { Users, UserPlus, Trash2, ShieldAlert } from "lucide-react";

export default function StaffPage() {
  const { selectedRestaurantId, profile } = useAuth();
  const [staffList, setStaffList] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);

  // Invite states
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState("RESTAURANT_STAFF");
  const [inviting, setInviting] = useState(false);
  const [inviteMsg, setInviteMsg] = useState("");
  const [inviteErr, setInviteErr] = useState("");

  // Remove states
  const [removeTarget, setRemoveTarget] = useState<User | null>(null);
  const [removing, setRemoving] = useState(false);

  useEffect(() => {
    if (!selectedRestaurantId) return;

    setLoading(true);
    const q = query(
      collection(db, "users"),
      where("restaurantIds", "array-contains", selectedRestaurantId)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list: User[] = [];
      snapshot.forEach((doc) => {
        const data = doc.data();
        list.push({ uid: doc.id, ...data } as User);
      });
      setStaffList(list);
      setLoading(false);
    });

    return unsubscribe;
  }, [selectedRestaurantId]);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRestaurantId) return;

    setInviteMsg("");
    setInviteErr("");
    setInviting(true);

    try {
      const inviteFn = httpsCallable(functions, "inviteRestaurantStaff");
      const res: any = await inviteFn({
        email: inviteEmail,
        role: inviteRole,
        restaurantId: selectedRestaurantId
      });
      setInviteMsg(res.data?.message || "Invitation sent successfully.");
      setInviteEmail("");
    } catch (e: any) {
      setInviteErr(e.message || "Failed to invite staff.");
    } finally {
      setInviting(false);
    }
  };

  const handleRemove = async () => {
    if (!removeTarget || !selectedRestaurantId) return;
    setRemoving(true);
    try {
      const removeFn = httpsCallable(functions, "removeRestaurantStaff");
      await removeFn({
        targetUid: removeTarget.uid,
        restaurantId: selectedRestaurantId
      });
      setRemoveTarget(null);
    } catch (e: any) {
      alert("Error: " + e.message);
    } finally {
      setRemoving(false);
    }
  };

  const isOwner = profile?.role === "RESTAURANT_OWNER";

  return (
    <PartnerLayout>
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight flex items-center gap-2">
            <Users className="text-orange-500" /> Staff Management
          </h1>
          <p className="text-slate-400 text-sm font-medium">Add, assign roles, or remove restaurant staff accounts.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Staff List */}
          <div className="glass-card p-6 md:col-span-2 space-y-4">
            <h2 className="text-base font-bold text-slate-200">Current Staff Members</h2>

            {loading ? (
              <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-2 border-t-orange-500 border-white/5 rounded-full animate-spin"></div>
              </div>
            ) : staffList.length === 0 ? (
              <p className="text-sm text-slate-500">No staff members linked to this restaurant.</p>
            ) : (
              <div className="divide-y divide-white/5">
                {staffList.map((staff) => (
                  <div key={staff.uid} className="py-3 flex justify-between items-center first:pt-0 last:pb-0">
                    <div>
                      <p className="text-sm font-semibold text-slate-200">{staff.displayName || staff.email}</p>
                      <p className="text-xs text-slate-500 mt-0.5">{staff.email}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className={`badge text-[10px] uppercase font-bold ${
                        staff.role === "RESTAURANT_OWNER" ? "badge-purple" :
                        staff.role === "RESTAURANT_MANAGER" ? "badge-blue" : "badge-green"
                      }`}>
                        {staff.role?.replace("RESTAURANT_", "")}
                      </span>
                      {isOwner && staff.uid !== profile?.uid && (
                        <button
                          onClick={() => setRemoveTarget(staff)}
                          className="p-1.5 hover:bg-red-500/10 border border-transparent hover:border-red-500/10 text-slate-500 hover:text-red-400 rounded-lg transition-colors cursor-pointer"
                          title="Remove Staff"
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Invitation Panel */}
          <div className="glass-card p-6 h-fit space-y-4">
            <h2 className="text-base font-bold text-slate-200 flex items-center gap-1.5">
              <UserPlus size={18} className="text-orange-500" /> Invite Staff
            </h2>
            {isOwner ? (
              <form onSubmit={handleInvite} className="space-y-4">
                {inviteMsg && (
                  <div className="p-3 bg-green-500/10 border border-green-500/20 rounded-xl text-green-400 text-xs">
                    {inviteMsg}
                  </div>
                )}
                {inviteErr && (
                  <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-xs">
                    {inviteErr}
                  </div>
                )}

                <div>
                  <label className="input-label">Email Address</label>
                  <input
                    type="email"
                    required
                    placeholder="staff@restaurant.com"
                    className="input-field"
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                  />
                </div>

                <div>
                  <label className="input-label">Role</label>
                  <select
                    className="input-field"
                    value={inviteRole}
                    onChange={(e) => setInviteRole(e.target.value)}
                  >
                    <option value="RESTAURANT_STAFF">Staff / Cook</option>
                    <option value="RESTAURANT_MANAGER">Manager</option>
                    <option value="RESTAURANT_OWNER">Co-Owner</option>
                  </select>
                </div>

                <button
                  type="submit"
                  disabled={inviting}
                  className="w-full btn-primary justify-center text-sm font-semibold"
                >
                  {inviting ? "Sending Invitation..." : "Send Invitation"}
                </button>
              </form>
            ) : (
              <div className="p-4 bg-white/5 border border-white/5 rounded-xl flex gap-2">
                <ShieldAlert className="text-amber-500 shrink-0 mt-0.5" size={16} />
                <p className="text-xs text-slate-500 leading-relaxed">
                  Only the primary restaurant Owner role is authorized to invite or remove staff members.
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Remove Confirm Modal */}
        {removeTarget && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h3 className="text-lg font-bold text-slate-100 mb-2">Remove Staff Member</h3>
              <p className="text-slate-400 text-sm mb-6">
                Are you sure you want to remove <strong className="text-slate-300">{removeTarget.displayName || removeTarget.email}</strong> from this restaurant? They will lose dashboard access.
              </p>
              <div className="flex gap-3 justify-end">
                <button onClick={() => setRemoveTarget(null)} className="btn-secondary">Cancel</button>
                <button onClick={handleRemove} disabled={removing} className="btn-danger">
                  {removing ? "Removing..." : "Remove Staff"}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </PartnerLayout>
  );
}
