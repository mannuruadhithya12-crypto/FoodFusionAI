"use client";

import { useEffect, useState } from "react";
import { collection, query, orderBy, limit, getDocs } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { FileText, ShieldAlert } from "lucide-react";
import { AuditLog } from "../../types";

export default function AuditLogsPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchLogs = async () => {
    try {
      const q = query(collection(db, "auditLogs"), orderBy("timestamp", "desc"), limit(100));
      const snap = await getDocs(q);
      setLogs(snap.docs.map(d => ({ id: d.id, ...d.data() } as AuditLog)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchLogs();
  }, []);

  if (loading) return <div>Loading Audit Logs...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">System Audit Logs</h1>
        <button onClick={() => { setLoading(true); fetchLogs(); }} className="text-sm bg-gray-100 hover:bg-gray-200 px-4 py-2 rounded-lg font-medium">
          Refresh
        </button>
      </div>

      <div className="bg-yellow-50 border-l-4 border-yellow-400 p-4 mb-6 text-sm text-yellow-800 flex items-center space-x-2">
        <ShieldAlert size={16} />
        <span>Audit logs are strictly read-only and maintained for compliance and security monitoring.</span>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="p-4 font-semibold text-gray-600 w-48">Timestamp</th>
              <th className="p-4 font-semibold text-gray-600 w-48">Admin</th>
              <th className="p-4 font-semibold text-gray-600">Action & Resource</th>
              <th className="p-4 font-semibold text-gray-600">Changes</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr><td colSpan={4} className="p-4 text-center text-gray-500 py-12"><FileText className="mx-auto text-gray-300 mb-2" size={32} /> No audit logs found</td></tr>
            ) : (
              logs.map(log => (
                <tr key={log.id} className="border-b hover:bg-gray-50 text-sm">
                  <td className="p-4 text-gray-500">
                    {log.timestamp ? new Date(log.timestamp.seconds * 1000).toLocaleString() : 'N/A'}
                  </td>
                  <td className="p-4 font-medium text-gray-800">
                    {log.adminEmail || log.adminId}
                  </td>
                  <td className="p-4">
                    <span className="font-bold text-blue-600 bg-blue-50 px-2 py-1 rounded text-xs mr-2">{log.action}</span>
                    <span className="text-gray-600">{log.resourceType}: {log.resourceId}</span>
                  </td>
                  <td className="p-4 text-xs font-mono text-gray-600">
                    {log.changes ? (
                      <pre className="whitespace-pre-wrap">{JSON.stringify(log.changes, null, 2)}</pre>
                    ) : (
                      <span className="italic text-gray-400">No diff recorded</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
