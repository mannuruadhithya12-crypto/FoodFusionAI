"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { collection, query, where, onSnapshot, doc, updateDoc, deleteDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { Food, Category } from "@/types";
import { Plus, Search, Edit2, Trash2, Leaf, AlertCircle } from "lucide-react";
import Link from "next/link";

export default function MenuPage() {
  const { selectedRestaurantId } = useAuth();
  const [foods, setFoods] = useState<Food[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [deleteConfirm, setDeleteConfirm] = useState<Food | null>(null);

  useEffect(() => {
    // 1. Fetch categories
    const catUnsubscribe = onSnapshot(collection(db, "categories"), (snap) => {
      const list: Category[] = [];
      snap.forEach((doc) => {
        list.push({ id: doc.id, ...doc.data() } as Category);
      });
      setCategories(list);
    });

    return catUnsubscribe;
  }, []);

  useEffect(() => {
    if (!selectedRestaurantId) return;

    setLoading(true);
    const q = query(
      collection(db, "foods"),
      where("restaurantId", "==", selectedRestaurantId)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list: Food[] = [];
      snapshot.forEach((doc) => {
        list.push({ id: doc.id, ...doc.data() } as Food);
      });
      setFoods(list);
      setLoading(false);
    });

    return unsubscribe;
  }, [selectedRestaurantId]);

  const handleToggleAvailable = async (food: Food) => {
    try {
      const docRef = doc(db, "foods", food.id);
      await updateDoc(docRef, {
        isAvailable: !food.isAvailable
      });
    } catch (e: any) {
      alert("Failed to toggle availability: " + e.message);
    }
  };

  const handleDelete = async () => {
    if (!deleteConfirm) return;
    try {
      const docRef = doc(db, "foods", deleteConfirm.id);
      await deleteDoc(docRef);
      setDeleteConfirm(null);
    } catch (e: any) {
      alert("Failed to delete food: " + e.message);
    }
  };

  // Filter foods by category & search query
  const filteredFoods = foods.filter((f) => {
    const matchesCategory = selectedCategory === "all" || f.categoryId === selectedCategory;
    const matchesSearch = f.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
                          f.description.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  return (
    <PartnerLayout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight">Menu Manager</h1>
            <p className="text-slate-400 text-sm">Add recipe custom fields, edit foods, toggle availability.</p>
          </div>
          <Link href="/menu/add" className="btn-primary">
            <Plus size={18} /> Add Food Item
          </Link>
        </div>

        {/* Filters and search */}
        <div className="flex flex-col md:flex-row gap-4 items-center justify-between">
          {/* Category Tabs */}
          <div className="flex gap-2 overflow-x-auto w-full md:w-auto pb-2 md:pb-0">
            <button
              onClick={() => setSelectedCategory("all")}
              className={`px-4 py-2 rounded-xl text-xs font-semibold whitespace-nowrap border transition-all cursor-pointer ${
                selectedCategory === "all"
                  ? "bg-orange-500/10 text-orange-400 border-orange-500/20"
                  : "bg-white/5 text-slate-400 border-white/5 hover:bg-white/10"
              }`}
            >
              All Items
            </button>
            {categories.map((cat) => (
              <button
                key={cat.id}
                onClick={() => setSelectedCategory(cat.id)}
                className={`px-4 py-2 rounded-xl text-xs font-semibold whitespace-nowrap border transition-all cursor-pointer ${
                  selectedCategory === cat.id
                    ? "bg-orange-500/10 text-orange-400 border-orange-500/20"
                    : "bg-white/5 text-slate-400 border-white/5 hover:bg-white/10"
                }`}
              >
                {cat.name}
              </button>
            ))}
          </div>

          {/* Search bar */}
          <div className="relative w-full md:w-80">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
            <input
              type="text"
              placeholder="Search food items..."
              className="input-field pl-11"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>

        {/* Foods Grid */}
        {loading ? (
          <div className="flex justify-center items-center py-32">
            <div className="w-10 h-10 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
          </div>
        ) : filteredFoods.length === 0 ? (
          <div className="text-center py-20 text-slate-500 text-sm">
            No food items found matching your filters.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {filteredFoods.map((food) => (
              <div key={food.id} className="glass-card overflow-hidden flex flex-col group">
                {/* Food Image */}
                <div className="relative h-44 bg-slate-800">
                  <img
                    src={food.imageUrl}
                    alt={food.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  />
                  {food.isVegetarian && (
                    <span className="absolute top-3 left-3 bg-emerald-500/90 text-white p-1 rounded-lg" title="Vegetarian">
                      <Leaf size={14} />
                    </span>
                  )}
                </div>

                {/* Card Content */}
                <div className="p-4 flex-1 flex flex-col">
                  <div className="flex justify-between items-start gap-2 mb-1">
                    <h3 className="font-bold text-sm text-slate-200 truncate">{food.name}</h3>
                    <span className="text-sm font-extrabold text-orange-400">₹{food.price}</span>
                  </div>
                  <p className="text-xs text-slate-500 line-clamp-2 mb-4 leading-relaxed">{food.description}</p>

                  <div className="mt-auto pt-3 border-t border-white/5 flex items-center justify-between">
                    {/* Toggle switch */}
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleToggleAvailable(food)}
                        className={`toggle-switch ${food.isAvailable ? "active" : ""}`}
                      />
                      <span className="text-xs text-slate-400 font-semibold">
                        {food.isAvailable ? "In Stock" : "Sold Out"}
                      </span>
                    </div>

                    {/* Edit/Delete Buttons */}
                    <div className="flex gap-1.5">
                      <Link
                        href={`/menu/edit/${food.id}`}
                        className="p-1.5 bg-white/5 border border-white/5 hover:bg-white/10 rounded-lg text-slate-400 hover:text-slate-200 transition-colors"
                        title="Edit Item"
                      >
                        <Edit2 size={13} />
                      </Link>
                      <button
                        onClick={() => setDeleteConfirm(food)}
                        className="p-1.5 bg-red-500/10 hover:bg-red-500/20 rounded-lg text-red-400 transition-colors cursor-pointer"
                        title="Delete Item"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Delete Confirmation Modal */}
        {deleteConfirm && (
          <div className="modal-overlay">
            <div className="modal-content">
              <div className="w-12 h-12 bg-red-500/10 border border-red-500/20 text-red-500 rounded-full flex items-center justify-center mb-4">
                <AlertCircle size={24} />
              </div>
              <h3 className="text-lg font-bold text-slate-100 mb-2">Delete Food Item?</h3>
              <p className="text-slate-400 text-sm mb-6">
                Are you sure you want to delete <strong className="text-slate-300">{deleteConfirm.name}</strong>? This action is permanent and cannot be undone.
              </p>
              <div className="flex gap-3 justify-end">
                <button onClick={() => setDeleteConfirm(null)} className="btn-secondary">Cancel</button>
                <button onClick={handleDelete} className="btn-danger">Delete</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </PartnerLayout>
  );
}
