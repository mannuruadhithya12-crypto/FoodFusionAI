"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, updateDoc, doc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { UserCheck, UserX, Search, Mail } from "lucide-react";
import { User } from "../../types";

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");

  const fetchUsers = async () => {
    try {
      const snap = await getDocs(collection(db, "users"));
      setUsers(snap.docs.map(d => ({ id: d.id, ...d.data() } as User)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchUsers();
  }, []);

  const toggleUserSuspension = async (userId: string, currentStatus: boolean) => {
    const action = currentStatus ? "suspend" : "reactivate";
    if (confirm(`Are you sure you want to ${action} this user?`)) {
      try {
        await updateDoc(doc(db, "users", userId), {
          isSuspended: !currentStatus,
          updatedAt: new Date().toISOString()
        });
        setLoading(true);
        fetchUsers();
      } catch (err) {
        console.error("Failed to update user", err);
        alert("Failed to update user status.");
      }
    }
  };

  const filteredUsers = users.filter(u => 
    (u.name?.toLowerCase() || "").includes(searchQuery.toLowerCase()) || 
    (u.email?.toLowerCase() || "").includes(searchQuery.toLowerCase()) ||
    (u.phone?.toLowerCase() || "").includes(searchQuery.toLowerCase())
  );

  if (loading) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">Users</h1>
        
        <div className="relative">
          <input 
            type="text" 
            placeholder="Search by name, email..." 
            className="pl-10 pr-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
          <Search className="absolute left-3 top-2.5 text-gray-400" size={18} />
        </div>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="p-4 font-semibold text-gray-600">User Details</th>
              <th className="p-4 font-semibold text-gray-600">Contact</th>
              <th className="p-4 font-semibold text-gray-600">Status</th>
              <th className="p-4 font-semibold text-gray-600">Joined</th>
              <th className="p-4 font-semibold text-gray-600 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredUsers.length === 0 ? (
              <tr><td colSpan={5} className="p-4 text-center text-gray-500">No users found</td></tr>
            ) : (
              filteredUsers.map(user => (
                <tr key={user.id} className="border-b hover:bg-gray-50">
                  <td className="p-4">
                    <div className="flex items-center space-x-3">
                      <div className="h-10 w-10 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center font-bold">
                        {user.name ? user.name.charAt(0).toUpperCase() : <Mail size={16}/>}
                      </div>
                      <div>
                        <p className="font-medium">{user.name || "Unknown"}</p>
                        <p className="text-xs text-gray-500">ID: {user.id.slice(0,8)}...</p>
                      </div>
                    </div>
                  </td>
                  <td className="p-4 text-sm">
                    <p>{user.email}</p>
                    <p className="text-gray-500">{user.phone || "No phone"}</p>
                  </td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${user.isSuspended ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800'}`}>
                      {user.isSuspended ? 'Suspended' : 'Active'}
                    </span>
                  </td>
                  <td className="p-4 text-sm text-gray-500">
                    {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : 'N/A'}
                  </td>
                  <td className="p-4 text-right">
                    {user.isSuspended ? (
                      <button onClick={() => toggleUserSuspension(user.id, true)} className="text-green-600 hover:text-green-800 flex items-center justify-end space-x-1 ml-auto">
                        <UserCheck size={16} /> <span>Reactivate</span>
                      </button>
                    ) : (
                      <button onClick={() => toggleUserSuspension(user.id, false)} className="text-red-600 hover:text-red-800 flex items-center justify-end space-x-1 ml-auto">
                        <UserX size={16} /> <span>Suspend</span>
                      </button>
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
