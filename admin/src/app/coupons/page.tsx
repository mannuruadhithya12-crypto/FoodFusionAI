"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, addDoc, updateDoc, doc, deleteDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { Plus, Edit, Trash2 } from "lucide-react";
import { Coupon } from "../../types";

export default function CouponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  
  const [formData, setFormData] = useState({ 
    id: "", code: "", type: "PERCENTAGE", discountValue: 0, 
    maxDiscount: 0, minOrderAmount: 0, expiryDate: "", 
    usageLimit: 0, currentUses: 0, perUserLimit: 1, 
    firstOrderOnly: false, isActive: true 
  });

  const fetchCoupons = async () => {
    try {
      const snap = await getDocs(collection(db, "coupons"));
      setCoupons(snap.docs.map(d => ({ id: d.id, ...d.data() } as Coupon)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchCoupons();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const { id, ...dataToSave } = formData;

    try {
      if (isEditing) {
        await updateDoc(doc(db, "coupons", id), dataToSave);
      } else {
        await addDoc(collection(db, "coupons"), dataToSave);
      }
      setShowModal(false);
      setLoading(true);
      fetchCoupons();
    } catch (err) {
      console.error("Failed to save coupon", err);
    }
  };

  const handleEdit = (coupon: Coupon) => {
    setFormData(coupon as unknown as typeof formData);
    setIsEditing(true);
    setShowModal(true);
  };

  const handleDelete = async (id: string) => {
    if (confirm("Are you sure you want to delete this coupon?")) {
      await deleteDoc(doc(db, "coupons", id));
      setLoading(true);
      fetchCoupons();
    }
  };

  const openNewModal = () => {
    setFormData({ 
        id: "", code: "", type: "PERCENTAGE", discountValue: 0, 
        maxDiscount: 0, minOrderAmount: 0, expiryDate: "", 
        usageLimit: 0, currentUses: 0, perUserLimit: 1, 
        firstOrderOnly: false, isActive: true 
    });
    setIsEditing(false);
    setShowModal(true);
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">Coupons</h1>
        <button onClick={openNewModal} className="flex items-center space-x-2 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          <Plus size={20} /> <span>Create Coupon</span>
        </button>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="p-4 font-semibold text-gray-600">Code</th>
              <th className="p-4 font-semibold text-gray-600">Discount</th>
              <th className="p-4 font-semibold text-gray-600">Expiry</th>
              <th className="p-4 font-semibold text-gray-600">Status</th>
              <th className="p-4 font-semibold text-gray-600 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {coupons.length === 0 ? (
              <tr><td colSpan={5} className="p-4 text-center text-gray-500">No coupons available</td></tr>
            ) : (
              coupons.map(coupon => (
                <tr key={coupon.id} className="border-b hover:bg-gray-50">
                  <td className="p-4 font-bold text-blue-600">{coupon.code}</td>
                  <td className="p-4">{coupon.type === "PERCENTAGE" ? `${coupon.discountValue}% (Max ₹${coupon.maxDiscount})` : `Flat ₹${coupon.discountValue}`}</td>
                  <td className="p-4">{coupon.expiryDate ? new Date(coupon.expiryDate).toLocaleDateString() : 'No expiry'}</td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${coupon.isActive ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {coupon.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="p-4 text-right space-x-3 flex justify-end">
                    <button onClick={() => handleEdit(coupon)} className="text-blue-600 hover:text-blue-800"><Edit size={18} /></button>
                    <button onClick={() => handleDelete(coupon.id)} className="text-red-600 hover:text-red-800"><Trash2 size={18} /></button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <h2 className="text-xl font-bold mb-4">{isEditing ? "Edit" : "Create"} Coupon</h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm">Coupon Code</label>
                  <input required className="w-full border p-2 rounded uppercase" value={formData.code} onChange={e => setFormData({...formData, code: e.target.value.toUpperCase()})} />
                </div>
                <div>
                  <label className="block text-sm">Type</label>
                  <select className="w-full border p-2 rounded bg-white" value={formData.type} onChange={e => setFormData({...formData, type: e.target.value})}>
                    <option value="PERCENTAGE">Percentage</option>
                    <option value="FLAT">Flat Amount</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm">Discount Value</label>
                  <input required type="number" className="w-full border p-2 rounded" value={formData.discountValue} onChange={e => setFormData({...formData, discountValue: Number(e.target.value)})} />
                </div>
                <div>
                  <label className="block text-sm">Max Discount (₹)</label>
                  <input type="number" className="w-full border p-2 rounded" value={formData.maxDiscount} onChange={e => setFormData({...formData, maxDiscount: Number(e.target.value)})} />
                </div>
                <div>
                  <label className="block text-sm">Min Order (₹)</label>
                  <input required type="number" className="w-full border p-2 rounded" value={formData.minOrderAmount} onChange={e => setFormData({...formData, minOrderAmount: Number(e.target.value)})} />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm">Expiry Date</label>
                  <input type="date" className="w-full border p-2 rounded" value={formData.expiryDate} onChange={e => setFormData({...formData, expiryDate: e.target.value})} />
                </div>
                <div>
                  <label className="block text-sm">Usage Limit (0 = ∞)</label>
                  <input type="number" className="w-full border p-2 rounded" value={formData.usageLimit} onChange={e => setFormData({...formData, usageLimit: Number(e.target.value)})} />
                </div>
                <div>
                  <label className="block text-sm">Per User Limit</label>
                  <input type="number" className="w-full border p-2 rounded" value={formData.perUserLimit} onChange={e => setFormData({...formData, perUserLimit: Number(e.target.value)})} />
                </div>
              </div>

              <div className="flex space-x-6 border-t pt-4">
                <div className="flex items-center space-x-2">
                  <input type="checkbox" id="firstOrderOnly" checked={formData.firstOrderOnly} onChange={e => setFormData({...formData, firstOrderOnly: e.target.checked})} />
                  <label htmlFor="firstOrderOnly">First Order Only</label>
                </div>
                <div className="flex items-center space-x-2">
                  <input type="checkbox" id="isActive" checked={formData.isActive} onChange={e => setFormData({...formData, isActive: e.target.checked})} />
                  <label htmlFor="isActive">Active</label>
                </div>
              </div>
              
              <div className="flex justify-end space-x-3 pt-4 mt-4">
                <button type="button" onClick={() => setShowModal(false)} className="px-4 py-2 bg-gray-200 rounded">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
