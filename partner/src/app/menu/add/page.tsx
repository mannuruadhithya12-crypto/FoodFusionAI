"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { collection, addDoc, getDocs } from "firebase/firestore";
import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
import { db, storage } from "@/lib/firebase";
import { Category } from "@/types";
import { useRouter } from "next/navigation";
import { ArrowLeft, Save, Upload, Sparkles, Leaf } from "lucide-react";

export default function AddFoodItemPage() {
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
  const [imageFile, setImageFile] = useState<File | null>(null);

  const [loading, setLoading] = useState(false);
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
        if (list.length > 0) setCategoryId(list[0].id);
      } catch (e) {
        console.error(e);
      }
    };
    fetchCategories();
  }, []);

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      
      // Limit to 5MB
      if (file.size > 5 * 1024 * 1024) {
        setError("File size exceeds 5MB limit.");
        return;
      }
      
      // Limit to images
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
    if (!selectedRestaurantId) return;

    setError("");
    setLoading(true);

    try {
      let imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500"; // default placeholder

      if (imageFile) {
        // Upload image to Firebase Storage
        const fileExt = imageFile.name.split(".").pop();
        const fileName = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}.${fileExt}`;
        const storageRef = ref(storage, `foods/${selectedRestaurantId}/${fileName}`);
        
        const uploadSnapshot = await uploadBytes(storageRef, imageFile);
        imageUrl = await getDownloadURL(uploadSnapshot.ref);
      }

      // Save food document
      await addDoc(collection(db, "foods"), {
        restaurantId: selectedRestaurantId,
        categoryId: categoryId,
        name: name,
        description: description,
        price: Number(price),
        imageUrl: imageUrl,
        isVegetarian: isVegetarian,
        isAvailable: true,
        preparationTime: Number(prepTime),
        rating: 5.0,
        ratingCount: 0,
        averageRating: 5.0,
        reviewCount: 0,
        createdAt: new Date().getTime(),
      });

      router.push("/menu");
    } catch (err: any) {
      setError(err.message || "Failed to add food item.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <PartnerLayout>
      <div className="space-y-6 max-w-2xl mx-auto">
        {/* Back Button & Header */}
        <div className="flex items-center gap-4">
          <button onClick={() => router.push("/menu")} className="p-2 bg-white/5 border border-white/5 rounded-xl text-slate-400 hover:text-slate-200 transition-colors">
            <ArrowLeft size={18} />
          </button>
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-100">Add Food Item</h1>
            <p className="text-xs text-slate-500 mt-0.5 font-medium">Create a new recipe entry in your catalog.</p>
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
                placeholder="Spicy Pepperoni Pizza"
                className="input-field"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>

            <div className="md:col-span-2">
              <label className="input-label">Description / Ingredients</label>
              <textarea
                required
                placeholder="Delicious hand-tossed dough with pure mozzarella cheese, garlic bits, pepperoni slices, and fresh basil leaves."
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
                placeholder="399"
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
                placeholder="15"
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

            {/* Image upload */}
            <div className="md:col-span-2">
              <label className="input-label">Food Photo</label>
              <div className="relative border-2 border-dashed border-white/10 hover:border-white/20 transition-colors rounded-xl p-6 text-center cursor-pointer flex flex-col items-center justify-center">
                <input
                  type="file"
                  accept="image/*"
                  className="absolute inset-0 opacity-0 cursor-pointer"
                  onChange={handleImageChange}
                />
                <Upload className="text-slate-500 mb-2" size={24} />
                <p className="text-sm font-semibold text-slate-300">
                  {imageFile ? imageFile.name : "Select or drag files to upload"}
                </p>
                <p className="text-xs text-slate-500 mt-1">PNG, JPG, WEBP (Max 5MB)</p>
              </div>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full btn-primary py-3.5 justify-center font-bold text-base shadow-lg"
          >
            {loading ? (
              <>
                <div className="w-5 h-5 border-2 border-t-white border-white/10 rounded-full animate-spin"></div>
                Creating Food...
              </>
            ) : (
              <>
                <Save size={18} />
                Save Item
              </>
            )}
          </button>
        </form>
      </div>
    </PartnerLayout>
  );
}
