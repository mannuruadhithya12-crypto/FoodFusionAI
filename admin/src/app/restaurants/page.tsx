"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, addDoc, updateDoc, doc, deleteDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { Plus, Edit, Trash2 } from "lucide-react";
import ImageUpload from "../../../components/ImageUpload";
import Image from "next/image";
import { Restaurant } from "../../types";

const initialForm = {
  id: "", name: "", description: "", imageUrl: "", bannerUrl: "",
  cuisine: "", phone: "", email: "", address: "", city: "", state: "", pincode: "",
  latitude: 0, longitude: 0, openingTime: "09:00", closingTime: "22:00",
  deliveryRadius: 5, deliveryFee: 0, minimumOrder: 0, preparationTime: 30,
  isOpen: true, isApproved: true
};

export default function RestaurantsPage() {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [formData, setFormData] = useState(initialForm);
  const [isEditing, setIsEditing] = useState(false);

  const fetchRestaurants = async () => {
    try {
      const snap = await getDocs(collection(db, "restaurants"));
      setRestaurants(snap.docs.map(doc => ({ id: doc.id, ...doc.data() } as Restaurant)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchRestaurants();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const { id, ...dataToSave } = formData;

    try {
      if (isEditing) {
        await updateDoc(doc(db, "restaurants", id), dataToSave);
      } else {
        await addDoc(collection(db, "restaurants"), dataToSave);
      }
      setShowModal(false);
      setLoading(true);
      fetchRestaurants();
    } catch (err) {
      console.error("Failed to save restaurant", err);
    }
  };

  const handleEdit = (restaurant: Restaurant) => {
    setFormData({ ...initialForm, ...restaurant } as unknown as typeof initialForm);
    setIsEditing(true);
    setShowModal(true);
  };

  const handleDelete = async (id: string) => {
    if (confirm("Are you sure you want to delete this restaurant?")) {
      await deleteDoc(doc(db, "restaurants", id));
      setLoading(true);
      fetchRestaurants();
    }
  };

  const openNewModal = () => {
    setFormData(initialForm);
    setIsEditing(false);
    setShowModal(true);
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">Restaurants</h1>
        <button onClick={openNewModal} className="flex items-center space-x-2 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          <Plus size={20} /> <span>Add Restaurant</span>
        </button>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table>
          <thead>
            <tr>
              <th>Logo</th>
              <th>Name</th>
              <th>Address</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {restaurants.length === 0 ? (
              <tr><td colSpan={5} className="p-4 text-center text-gray-500">No restaurants available</td></tr>
            ) : (
              restaurants.map(rest => (
                <tr key={rest.id} className="hover:bg-slate-50 transition-colors">
                  <td>
                    {rest.imageUrl ? <Image src={rest.imageUrl} alt={rest.name} width={40} height={40} className="h-10 w-10 object-cover rounded-full" /> : <div className="h-10 w-10 bg-gray-200 rounded-full"></div>}
                  </td>
                  <td className="font-semibold text-gray-800">{rest.name}</td>
                  <td className="text-gray-500">{rest.address}</td>
                  <td>
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${rest.isOpen ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {rest.isOpen ? 'Active' : 'Suspended'}
                    </span>
                  </td>
                  <td className="text-right">
                    <button onClick={() => handleEdit(rest)} className="text-blue-600 hover:text-blue-800 mr-3">Edit</button>
                    <button onClick={() => handleDelete(rest.id)} className="text-red-600 hover:text-red-800">Delete</button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg p-6 w-full max-w-4xl max-h-[90vh] overflow-y-auto">
            <h2 className="text-2xl font-bold mb-6">{isEditing ? "Edit" : "Add"} Restaurant</h2>
            <form onSubmit={handleSubmit} className="space-y-6">
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-4">
                  <h3 className="font-semibold text-gray-700 border-b pb-2">Basic Info</h3>
                  <div><label className="block text-sm font-medium">Name</label><input required className="w-full border p-2 rounded" value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} /></div>
                  <div><label className="block text-sm font-medium">Description</label><textarea className="w-full border p-2 rounded" value={formData.description} onChange={e => setFormData({...formData, description: e.target.value})} /></div>
                  <div><label className="block text-sm font-medium">Cuisine</label><input className="w-full border p-2 rounded" value={formData.cuisine} onChange={e => setFormData({...formData, cuisine: e.target.value})} /></div>
                  <div className="grid grid-cols-2 gap-4">
                    <div><label className="block text-sm font-medium">Phone</label><input className="w-full border p-2 rounded" value={formData.phone} onChange={e => setFormData({...formData, phone: e.target.value})} /></div>
                    <div><label className="block text-sm font-medium">Email</label><input type="email" className="w-full border p-2 rounded" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} /></div>
                  </div>
                </div>

                <div className="space-y-4">
                  <h3 className="font-semibold text-gray-700 border-b pb-2">Images</h3>
                  <ImageUpload label="Logo" folder="restaurants/logos" currentUrl={formData.imageUrl} onUploadSuccess={(url) => setFormData({...formData, imageUrl: url})} onUploadError={(err) => alert(err)} onRemove={() => setFormData({...formData, imageUrl: ""})} />
                  <ImageUpload label="Banner" folder="restaurants/banners" currentUrl={formData.bannerUrl} onUploadSuccess={(url) => setFormData({...formData, bannerUrl: url})} onUploadError={(err) => alert(err)} onRemove={() => setFormData({...formData, bannerUrl: ""})} />
                </div>
              </div>

              <div className="space-y-4">
                <h3 className="font-semibold text-gray-700 border-b pb-2">Location</h3>
                <div><label className="block text-sm font-medium">Address</label><input required className="w-full border p-2 rounded" value={formData.address} onChange={e => setFormData({...formData, address: e.target.value})} /></div>
                <div className="grid grid-cols-3 gap-4">
                  <div><label className="block text-sm font-medium">City</label><input required className="w-full border p-2 rounded" value={formData.city} onChange={e => setFormData({...formData, city: e.target.value})} /></div>
                  <div><label className="block text-sm font-medium">State</label><input required className="w-full border p-2 rounded" value={formData.state} onChange={e => setFormData({...formData, state: e.target.value})} /></div>
                  <div><label className="block text-sm font-medium">Pincode</label><input className="w-full border p-2 rounded" value={formData.pincode} onChange={e => setFormData({...formData, pincode: e.target.value})} /></div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div><label className="block text-sm font-medium">Latitude</label><input type="number" step="any" className="w-full border p-2 rounded" value={formData.latitude} onChange={e => setFormData({...formData, latitude: Number(e.target.value)})} /></div>
                  <div><label className="block text-sm font-medium">Longitude</label><input type="number" step="any" className="w-full border p-2 rounded" value={formData.longitude} onChange={e => setFormData({...formData, longitude: Number(e.target.value)})} /></div>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                 <div className="space-y-4">
                    <h3 className="font-semibold text-gray-700 border-b pb-2">Operations</h3>
                    <div className="grid grid-cols-2 gap-4">
                      <div><label className="block text-sm font-medium">Opening Time</label><input type="time" className="w-full border p-2 rounded" value={formData.openingTime} onChange={e => setFormData({...formData, openingTime: e.target.value})} /></div>
                      <div><label className="block text-sm font-medium">Closing Time</label><input type="time" className="w-full border p-2 rounded" value={formData.closingTime} onChange={e => setFormData({...formData, closingTime: e.target.value})} /></div>
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                       <div><label className="block text-sm font-medium">Delivery Fee (₹)</label><input type="number" className="w-full border p-2 rounded" value={formData.deliveryFee} onChange={e => setFormData({...formData, deliveryFee: Number(e.target.value)})} /></div>
                       <div><label className="block text-sm font-medium">Min Order (₹)</label><input type="number" className="w-full border p-2 rounded" value={formData.minimumOrder} onChange={e => setFormData({...formData, minimumOrder: Number(e.target.value)})} /></div>
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                       <div><label className="block text-sm font-medium">Delivery Radius (km)</label><input type="number" className="w-full border p-2 rounded" value={formData.deliveryRadius} onChange={e => setFormData({...formData, deliveryRadius: Number(e.target.value)})} /></div>
                       <div><label className="block text-sm font-medium">Prep Time (mins)</label><input type="number" className="w-full border p-2 rounded" value={formData.preparationTime} onChange={e => setFormData({...formData, preparationTime: Number(e.target.value)})} /></div>
                    </div>
                 </div>
                 
                 <div className="space-y-4">
                    <h3 className="font-semibold text-gray-700 border-b pb-2">Status</h3>
                    <div className="flex items-center space-x-3 bg-gray-50 p-3 rounded border">
                      <input type="checkbox" id="isOpen" className="w-5 h-5 text-blue-600" checked={formData.isOpen} onChange={e => setFormData({...formData, isOpen: e.target.checked})} />
                      <label htmlFor="isOpen" className="font-medium text-gray-700">Active (Open for Orders)</label>
                    </div>
                    <div className="flex items-center space-x-3 bg-gray-50 p-3 rounded border">
                      <input type="checkbox" id="isApproved" className="w-5 h-5 text-blue-600" checked={formData.isApproved} onChange={e => setFormData({...formData, isApproved: e.target.checked})} />
                      <label htmlFor="isApproved" className="font-medium text-gray-700">Approved Platform Partner</label>
                    </div>
                 </div>
              </div>

              <div className="flex justify-end space-x-3 pt-6 border-t">
                <button type="button" onClick={() => setShowModal(false)} className="px-6 py-2 bg-gray-200 text-gray-800 font-medium rounded-lg hover:bg-gray-300 transition-colors">Cancel</button>
                <button type="submit" className="px-6 py-2 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition-colors">Save Restaurant</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
