"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { doc, getDoc, updateDoc, collection, getDocs } from "firebase/firestore";
import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
import { db, storage } from "@/lib/firebase";
import { Category, Food } from "@/types";
import { useRouter, useParams } from "next/navigation";
import { ArrowLeft, Save, Upload, Leaf } from "lucide-react";

export default function EditFoodItemPage() {
  const { id } = useParams() as { id: string };
  const { selectedRestaurantId } = useAuth();
  const router = useRouter();
  const [categories, setCategories] = useState<Category[]>([]);

  // Form states
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [price, setPrice] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [isVegetarian, setIsVegetarian] = useState(false);
  const [prepTime, setPrepTime] = useState("15");
  const [currentImageUrl, setCurrentImageUrl] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const snap = await getDocs(collection(db, "categories"));
        const list: Category[] = [];
        snap.forEach((doc) => {
          list.push({ id: doc.id, ...doc.data() } as Category);
        });
        setCategories(list);
      } catch (e) {
        console.error(e);
      }
    };
    fetchCategories();
  }, []);

  useEffect(() => {
    const fetchFood = async () => {
      if (!id) return;
      try {
        const docRef = doc(db, "foods", id);
        const snap = await getDoc(docRef);
        if (snap.exists()) {
          const data = snap.data() as Food;
          setName(data.name);
          setDescription(data.description);
          setPrice(data.price.toString());
          setCategoryId(data.categoryId);
          setIsVegetarian(data.isVegetarian);
          setPrepTime((data.preparationTime || 15).toString());
          setCurrentImageUrl(data.imageUrl);
        }
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchFood();
  }, [id]);

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      if (file.size > 5 * 1024 * 1024) {
        setError("File size exceeds 5MB limit.");
        return;
      }
      if (!file.type.startsWith("image/")) {
        setError("Only image files are allowed.");
        return;
      }
      setError("");
      setImageFile(file);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRestaurantId || !id) return;

    setError("");
    setSaving(true);

    try {
      let finalImageUrl = currentImageUrl;

      if (imageFile) {
        // Upload new image to Storage
        const fileExt = imageFile.name.split(".").pop();
        const fileName = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}.${fileExt}`;
        const storageRef = ref(storage, `foods/${selectedRestaurantId}/${fileName}`);
        
        const uploadSnapshot = await uploadBytes(storageRef, imageFile);
        finalImageUrl = await getDownloadURL(uploadSnapshot.ref);
      }

      // Update food doc
      const docRef = doc(db, "foods", id);
      await updateDoc(docRef, {
        categoryId: categoryId,
        name: name,
        description: description,
        price: Number(price),
        imageUrl: finalImageUrl,
        isVegetarian: isVegetarian,
        preparationTime: Number(prepTime),
        updatedAt: new Date().getTime(),
      });

      router.push("/menu");
    } catch (err: any) {
      setError(err.message || "Failed to update food item.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <PartnerLayout>
        <div className="flex justify-center items-center py-32">
          <div className="w-10 h-10 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
        </div>
      </PartnerLayout>
    );
  }

  return (
    <PartnerLayout>
      <div className="space-y-6 max-w-2xl mx-auto">
        {/* Back Button & Header */}
        <div className="flex items-center gap-4">
          <button onClick={() => router.push("/menu")} className="p-2 bg-white/5 border border-white/5 rounded-xl text-slate-400 hover:text-slate-200 transition-colors">
            <ArrowLeft size={18} />
          </button>
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-100">Edit Food Item</h1>
            <p className="text-xs text-slate-500 mt-0.5 font-medium">Modify existing recipe details or photo.</p>
          </div>
        </div>

        {error && (
          <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="glass-card p-6 md:p-8 space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="md:col-span-2">
              <label className="input-label">Food Name</label>
              <input
                type="text"
                required
                className="input-field"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>

            <div className="md:col-span-2">
              <label className="input-label">Description / Ingredients</label>
              <textarea
                required
                rows={3}
                className="input-field resize-none"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>

            <div>
              <label className="input-label">Price (₹)</label>
              <input
                type="number"
                required
                className="input-field"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </div>

            <div>
              <label className="input-label">Category</label>
              <select
                className="input-field"
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
              >
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="input-label">Preparation Time (mins)</label>
              <input
                type="number"
                required
                className="input-field"
                value={prepTime}
                onChange={(e) => setPrepTime(e.target.value)}
              />
            </div>

            <div className="flex items-center justify-between p-4 bg-white/5 border border-white/5 rounded-xl">
              <div className="flex items-center gap-2">
                <Leaf className="text-emerald-400" size={18} />
                <div>
                  <p className="text-xs font-semibold text-slate-200">Vegetarian Option</p>
                  <p className="text-[10px] text-slate-500">Tag recipe with veg label.</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsVegetarian(!isVegetarian)}
                className={`toggle-switch ${isVegetarian ? "active" : ""}`}
              />
            </div>

            {/* Current photo preview & Upload */}
            <div className="md:col-span-2">
              <label className="input-label">Current Photo</label>
              {currentImageUrl && !imageFile && (
                <div className="mb-4 rounded-xl overflow-hidden h-40 w-full md:w-60 border border-white/5">
                  <img src={currentImageUrl} alt="Current" className="w-full h-full object-cover" />
                </div>
              )}
              <div className="relative border-2 border-dashed border-white/10 hover:border-white/20 transition-colors rounded-xl p-6 text-center cursor-pointer flex flex-col items-center justify-center">
                <input
                  type="file"
                  accept="image/*"
                  className="absolute inset-0 opacity-0 cursor-pointer"
                  onChange={handleImageChange}
                />
                <Upload className="text-slate-500 mb-2" size={24} />
                <p className="text-sm font-semibold text-slate-300">
                  {imageFile ? imageFile.name : "Replace current photo"}
                </p>
                <p className="text-xs text-slate-500 mt-1">PNG, JPG, WEBP (Max 5MB)</p>
              </div>
            </div>
          </div>

          <button
            type="submit"
            disabled={saving}
            className="w-full btn-primary py-3.5 justify-center font-bold text-base shadow-lg"
          >
            {saving ? (
              <>
                <div className="w-5 h-5 border-2 border-t-white border-white/10 rounded-full animate-spin"></div>
                Updating Food...
              </>
            ) : (
              <>
                <Save size={18} />
                Save Changes
              </>
            )}
          </button>
        </form>
      </div>
    </PartnerLayout>
  );
}
