"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, addDoc, updateDoc, doc, deleteDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { Plus, Edit, Trash2 } from "lucide-react";
import ImageUpload from "../../../components/ImageUpload";
import Image from "next/image";
import { Category } from "../../types";

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  
  const [formData, setFormData] = useState({ 
    id: "", name: "", imageUrl: "" 
  });

  const fetchData = async () => {
    try {
      const snap = await getDocs(collection(db, "categories"));
      setCategories(snap.docs.map(d => ({ id: d.id, ...d.data() } as Category)));
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
        await updateDoc(doc(db, "categories", id), dataToSave);
      } else {
        await addDoc(collection(db, "categories"), dataToSave);
      }
      setShowModal(false);
      setLoading(true);
      fetchData();
    } catch (err) {
      console.error("Failed to save category", err);
    }
  };

  const handleEdit = (category: Category) => {
    setFormData(category);
    setIsEditing(true);
    setShowModal(true);
  };

  const handleDelete = async (id: string) => {
    if (confirm("Are you sure you want to delete this category?")) {
      await deleteDoc(doc(db, "categories", id));
      setLoading(true);
      fetchData();
    }
  };

  const openNewModal = () => {
    setFormData({ id: "", name: "", imageUrl: "" });
    setIsEditing(false);
    setShowModal(true);
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">Categories</h1>
        <button onClick={openNewModal} className="flex items-center space-x-2 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          <Plus size={20} /> <span>Add Category</span>
        </button>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="p-4 font-semibold text-gray-600">Image</th>
              <th className="p-4 font-semibold text-gray-600">Name</th>
              <th className="p-4 font-semibold text-gray-600 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {categories.length === 0 ? (
              <tr>
                <td colSpan={3} className="p-4 text-center text-gray-500">No categories available</td>
              </tr>
            ) : (
              categories.map(cat => (
                <tr key={cat.id} className="border-b hover:bg-gray-50">
                  <td className="p-4">
                    {cat.imageUrl ? <Image src={cat.imageUrl} alt={cat.name} width={40} height={40} className="h-10 w-10 object-cover rounded" /> : <div className="h-10 w-10 bg-gray-200 rounded"></div>}
                  </td>
                  <td className="p-4 font-medium">{cat.name}</td>
                  <td className="p-4 text-right space-x-3 flex justify-end">
                    <button onClick={() => handleEdit(cat)} className="text-blue-600 hover:text-blue-800"><Edit size={18} /></button>
                    <button onClick={() => handleDelete(cat.id)} className="text-red-600 hover:text-red-800"><Trash2 size={18} /></button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-bold mb-4">{isEditing ? "Edit" : "Add"} Category</h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm">Name</label>
                <input required className="w-full border p-2 rounded" value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />
              </div>
              <div>
                <ImageUpload 
                   label="Category Image"
                   folder="categories"
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
