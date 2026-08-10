"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, addDoc, updateDoc, doc, deleteDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { Plus, Edit, Trash2 } from "lucide-react";
import ImageUpload from "../../../components/ImageUpload";
import Image from "next/image";
import { Offer } from "../../types";

export default function OffersPage() {
  const [offers, setOffers] = useState<Offer[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  
  const [formData, setFormData] = useState({ 
    id: "", title: "", description: "", imageUrl: "", 
    priority: 0, target: "HOME_BANNER", discount: 0, 
    startDate: "", endDate: "", isActive: true 
  });

  const fetchOffers = async () => {
    try {
      const snap = await getDocs(collection(db, "offers"));
      setOffers(snap.docs.map(d => ({ id: d.id, ...d.data() } as Offer)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchOffers();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const { id, ...dataToSave } = formData;

    try {
      if (isEditing) {
        await updateDoc(doc(db, "offers", id), dataToSave);
      } else {
        await addDoc(collection(db, "offers"), dataToSave);
      }
      setShowModal(false);
      setLoading(true);
      fetchOffers();
    } catch (err) {
      console.error("Failed to save offer", err);
    }
  };

  const handleEdit = (offer: Offer) => {
    setFormData(offer as unknown as typeof formData);
    setIsEditing(true);
    setShowModal(true);
  };

  const handleDelete = async (id: string) => {
    if (confirm("Are you sure you want to delete this offer?")) {
      await deleteDoc(doc(db, "offers", id));
      setLoading(true);
      fetchOffers();
    }
  };

  const openNewModal = () => {
    setFormData({ 
        id: "", title: "", description: "", imageUrl: "", 
        priority: 0, target: "HOME_BANNER", discount: 0, 
        startDate: "", endDate: "", isActive: true 
    });
    setIsEditing(false);
    setShowModal(true);
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">Offers & Banners</h1>
        <button onClick={openNewModal} className="flex items-center space-x-2 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          <Plus size={20} /> <span>Create Offer</span>
        </button>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="p-4 font-semibold text-gray-600">Banner</th>
              <th className="p-4 font-semibold text-gray-600">Title</th>
              <th className="p-4 font-semibold text-gray-600">Target</th>
              <th className="p-4 font-semibold text-gray-600">Status</th>
              <th className="p-4 font-semibold text-gray-600 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {offers.length === 0 ? (
              <tr><td colSpan={5} className="p-4 text-center text-gray-500">No offers available</td></tr>
            ) : (
              offers.map(offer => (
                <tr key={offer.id} className="border-b hover:bg-gray-50">
                  <td className="p-4">
                    {offer.imageUrl ? <Image src={offer.imageUrl} alt={offer.title} width={80} height={40} className="h-10 w-20 object-cover rounded" /> : <div className="h-10 w-20 bg-gray-200 rounded"></div>}
                  </td>
                  <td className="p-4 font-medium">{offer.title}</td>
                  <td className="p-4">{offer.target}</td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${offer.isActive ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {offer.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="p-4 text-right space-x-3 flex justify-end">
                    <button onClick={() => handleEdit(offer)} className="text-blue-600 hover:text-blue-800"><Edit size={18} /></button>
                    <button onClick={() => handleDelete(offer.id)} className="text-red-600 hover:text-red-800"><Trash2 size={18} /></button>
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
            <h2 className="text-xl font-bold mb-4">{isEditing ? "Edit" : "Create"} Offer</h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              
              <div>
                <label className="block text-sm">Title</label>
                <input required className="w-full border p-2 rounded" value={formData.title} onChange={e => setFormData({...formData, title: e.target.value})} />
              </div>
              
              <div>
                <label className="block text-sm">Description</label>
                <input required className="w-full border p-2 rounded" value={formData.description} onChange={e => setFormData({...formData, description: e.target.value})} />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm">Target Audience/Location</label>
                  <select className="w-full border p-2 rounded bg-white" value={formData.target} onChange={e => setFormData({...formData, target: e.target.value})}>
                    <option value="HOME_BANNER">Home Banner</option>
                    <option value="RESTAURANT_PROMO">Restaurant Promo</option>
                    <option value="FOOD_PROMO">Food Promo</option>
                    <option value="FIRST_ORDER">First Order Offer</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm">Priority (Higher = Shows First)</label>
                  <input type="number" className="w-full border p-2 rounded" value={formData.priority} onChange={e => setFormData({...formData, priority: Number(e.target.value)})} />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm">Start Date</label>
                  <input type="date" className="w-full border p-2 rounded" value={formData.startDate} onChange={e => setFormData({...formData, startDate: e.target.value})} />
                </div>
                <div>
                  <label className="block text-sm">End Date</label>
                  <input type="date" className="w-full border p-2 rounded" value={formData.endDate} onChange={e => setFormData({...formData, endDate: e.target.value})} />
                </div>
              </div>
              
              <div>
                <ImageUpload 
                   label="Banner Image"
                   folder="offers"
                   currentUrl={formData.imageUrl}
                   onUploadSuccess={(url) => setFormData({...formData, imageUrl: url})}
                   onUploadError={(err) => alert(err)}
                   onRemove={() => setFormData({...formData, imageUrl: ""})}
                />
              </div>

              <div className="flex space-x-6 border-t pt-4">
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
