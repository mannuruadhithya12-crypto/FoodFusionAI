"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, addDoc, updateDoc, doc, deleteDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { Plus, Edit, Trash2 } from "lucide-react";
import ImageUpload from "../../../components/ImageUpload";
import { Food, Restaurant, Category } from "../../types";

export default function FoodsPage() {
  const [foods, setFoods] = useState<Food[]>([]);
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  
  const [formData, setFormData] = useState({ 
    id: "", name: "", price: 0, description: "", imageUrl: "",
    restaurantId: "", categoryId: "", isAvailable: true, isVegetarian: true, preparationTime: 20 
  });

  const fetchData = async () => {
    try {
      const [foodsSnap, restsSnap, catsSnap] = await Promise.all([
        getDocs(collection(db, "foods")),
        getDocs(collection(db, "restaurants")),
        getDocs(collection(db, "categories")),
      ]);
      
      setFoods(foodsSnap.docs.map(d => ({ id: d.id, ...d.data() } as Food)));
      setRestaurants(restsSnap.docs.map(d => ({ id: d.id, ...d.data() } as Restaurant)));
      setCategories(catsSnap.docs.map(d => ({ id: d.id, ...d.data() } as Category)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchData();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const { id, ...dataToSave } = formData;

    try {
      if (isEditing) {
        await updateDoc(doc(db, "foods", id), dataToSave);
      } else {
        await addDoc(collection(db, "foods"), dataToSave);
      }
      setShowModal(false);
      setLoading(true);
      fetchData();
    } catch (err) {
      console.error("Failed to save food", err);
    }
  };

  const handleEdit = (food: Food) => {
    setFormData(food as unknown as typeof formData);
    setIsEditing(true);
    setShowModal(true);
  };

  const handleDelete = async (id: string) => {
    if (confirm("Are you sure you want to delete this food item?")) {
      await deleteDoc(doc(db, "foods", id));
      setLoading(true);
      fetchData();
    }
  };

  const openNewModal = () => {
    setFormData({ id: "", name: "", price: 0, description: "", imageUrl: "", restaurantId: "", categoryId: "", isAvailable: true, isVegetarian: true, preparationTime: 20 });
    setIsEditing(false);
    setShowModal(true);
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">Menu Items (Foods)</h1>
        <button onClick={openNewModal} className="flex items-center space-x-2 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          <Plus size={20} /> <span>Add Food</span>
        </button>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="p-4 font-semibold text-gray-600">Name</th>
              <th className="p-4 font-semibold text-gray-600">Restaurant</th>
              <th className="p-4 font-semibold text-gray-600">Price</th>
              <th className="p-4 font-semibold text-gray-600">Type</th>
              <th className="p-4 font-semibold text-gray-600">Status</th>
              <th className="p-4 font-semibold text-gray-600 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {foods.length === 0 ? (
              <tr>
                <td colSpan={6} className="p-4 text-center text-gray-500">No foods available</td>
              </tr>
            ) : (
              foods.map(food => {
                const restName = restaurants.find(r => r.id === food.restaurantId)?.name || "Unknown";
                return (
                <tr key={food.id} className="border-b hover:bg-gray-50">
                  <td className="p-4 font-medium">{food.name}</td>
                  <td className="p-4 text-gray-600">{restName}</td>
                  <td className="p-4">₹{food.price}</td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${food.isVegetarian ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {food.isVegetarian ? 'Veg' : 'Non-Veg'}
                    </span>
                  </td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${food.isAvailable ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800'}`}>
                      {food.isAvailable ? 'Available' : 'Out of Stock'}
                    </span>
                  </td>
                  <td className="p-4 text-right space-x-3 flex justify-end">
                    <button onClick={() => handleEdit(food)} className="text-blue-600 hover:text-blue-800"><Edit size={18} /></button>
                    <button onClick={() => handleDelete(food.id)} className="text-red-600 hover:text-red-800"><Trash2 size={18} /></button>
                  </td>
                </tr>
              )})
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
            <h2 className="text-xl font-bold mb-4">{isEditing ? "Edit" : "Add"} Food</h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm">Name</label>
                <input required className="w-full border p-2 rounded" value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />
              </div>
              <div>
                <label className="block text-sm">Description</label>
                <input required className="w-full border p-2 rounded" value={formData.description} onChange={e => setFormData({...formData, description: e.target.value})} />
              </div>
              <div>
                <label className="block text-sm">Price</label>
                <input required type="number" className="w-full border p-2 rounded" value={formData.price} onChange={e => setFormData({...formData, price: Number(e.target.value)})} />
              </div>
              <div>
                <label className="block text-sm">Restaurant</label>
                <select required className="w-full border p-2 rounded bg-white" value={formData.restaurantId} onChange={e => setFormData({...formData, restaurantId: e.target.value})}>
                  <option value="">Select Restaurant</option>
                  {restaurants.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm">Category</label>
                <select required className="w-full border p-2 rounded bg-white" value={formData.categoryId} onChange={e => setFormData({...formData, categoryId: e.target.value})}>
                  <option value="">Select Category</option>
                  {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div className="flex space-x-6">
                <div className="flex items-center space-x-2">
                  <input type="checkbox" id="isAvailable" checked={formData.isAvailable} onChange={e => setFormData({...formData, isAvailable: e.target.checked})} />
                  <label htmlFor="isAvailable">Available</label>
                </div>
                <div className="flex items-center space-x-2">
                  <input type="checkbox" id="isVegetarian" checked={formData.isVegetarian} onChange={e => setFormData({...formData, isVegetarian: e.target.checked})} />
                  <label htmlFor="isVegetarian">Vegetarian</label>
                </div>
              </div>
              
              <div>
                <label className="block text-sm mb-1">Prep Time (mins)</label>
                <input required type="number" className="w-full border p-2 rounded" value={formData.preparationTime} onChange={e => setFormData({...formData, preparationTime: Number(e.target.value)})} />
              </div>

              <div>
                <ImageUpload 
                   label="Food Image"
                   folder="foods"
                   currentUrl={formData.imageUrl}
                   onUploadSuccess={(url) => setFormData({...formData, imageUrl: url})}
                   onUploadError={(err) => alert(err)}
                   onRemove={() => setFormData({...formData, imageUrl: ""})}
                />
              </div>

              <div className="flex justify-end space-x-3 pt-4 border-t mt-4">
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
