"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { collection, query, where, onSnapshot } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { AuditLog } from "@/types";
import { FileText, ShieldAlert } from "lucide-react";

export default function AuditLogsPage() {
  const { selectedRestaurantId } = useAuth();
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!selectedRestaurantId) return;

    setLoading(true);
    const q = query(
      collection(db, "auditLogs"),
      where("restaurantId", "==", selectedRestaurantId)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list: AuditLog[] = [];
      snapshot.forEach((doc) => {
        list.push({ id: doc.id, ...doc.data() } as AuditLog);
      });

      // Sort by date desc
      list.sort((a, b) => {
        const timeA = a.timestamp?.seconds ? a.timestamp.seconds * 1000 : a.timestamp;
        const timeB = b.timestamp?.seconds ? b.timestamp.seconds * 1000 : b.timestamp;
        return (timeB || 0) - (timeA || 0);
      });

      setLogs(list);
      setLoading(false);
    });

    return unsubscribe;
  }, [selectedRestaurantId]);

  return (
    <PartnerLayout>
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight flex items-center gap-2">
            <FileText className="text-orange-500" /> Security Audit Trail
          </h1>
          <p className="text-slate-400 text-sm font-medium">Append-only log logging administrative updates and state transitions.</p>
        </div>

        <div className="p-4 bg-white/5 border border-white/5 rounded-xl flex gap-3 text-left">
          <ShieldAlert className="text-amber-500 shrink-0 mt-0.5" size={18} />
          <div>
            <p className="text-xs font-bold text-slate-200">Append-Only Constraints Enforced</p>
            <p className="text-[10px] text-slate-500 leading-relaxed mt-0.5">
              These records are server-authoritative and locked against edits or deletion by any restaurant member or supervisor.
            </p>
          </div>
        </div>

        {loading ? (
          <div className="flex justify-center items-center py-24">
            <div className="w-8 h-8 border-2 border-t-orange-500 border-white/5 rounded-full animate-spin"></div>
          </div>
        ) : logs.length === 0 ? (
          <div className="glass-card p-12 text-center text-slate-500 text-sm">
            No audit trails recorded yet.
          </div>
        ) : (
          <div className="glass-card p-6 space-y-4">
            <div className="divide-y divide-white/5">
              {logs.map((log) => {
                const date = new Date(log.timestamp?.seconds ? log.timestamp.seconds * 1000 : log.timestamp);
                return (
                  <div key={log.id} className="py-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 first:pt-0 last:pb-0">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className={`badge text-[9px] uppercase font-black ${
                          log.action === "ORDER_ACCEPTED" ? "badge-green" :
                          log.action === "ORDER_REJECTED" ? "badge-red" :
                          log.action === "STAFF_INVITED" ? "badge-blue" :
                          log.action === "STAFF_REMOVED" ? "badge-red" : "badge-orange"
                        }`}>
                          {log.action}
                        </span>
                        <span className="text-slate-200 text-xs font-semibold">
                          Target ID: {log.targetId ? log.targetId.slice(-8) : "N/A"}
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 font-medium">
                        Initiated by UID: <code className="text-slate-400">{log.actorUid}</code>
                      </p>
                      {log.changes && (
                        <p className="text-[10px] text-slate-500 italic mt-0.5">
                          Payload details: {JSON.stringify(log.changes)}
                        </p>
                      )}
                    </div>
                    <span className="text-[10px] text-slate-500 font-medium whitespace-nowrap self-start sm:self-center">
                      {date.toLocaleString()}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </PartnerLayout>
  );
}
