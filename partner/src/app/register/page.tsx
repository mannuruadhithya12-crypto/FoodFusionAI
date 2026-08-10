"use client";

import React, { useState } from "react";
import { createUserWithEmailAndPassword } from "firebase/auth";
import { auth, db } from "@/lib/firebase";
import { doc, setDoc, collection } from "firebase/firestore";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { User, Mail, Lock, Building, MapPin, Phone, Sparkles } from "lucide-react";

export default function RegisterPage() {
  const [partnerName, setPartnerName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  
  const [restaurantName, setRestaurantName] = useState("");
  const [cuisine, setCuisine] = useState("");
  const [address, setAddress] = useState("");
  const [city, setCity] = useState("");
  const [phone, setPhone] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      // 1. Sign up user
      const credentials = await createUserWithEmailAndPassword(auth, email, password);
      const uid = credentials.user.uid;

      // 2. Generate new restaurant ID
      const restaurantRef = doc(collection(db, "restaurants"));
      const restaurantId = restaurantRef.id;

      // 3. Create restaurant document (ApprovalStatus: PENDING)
      await setDoc(restaurantRef, {
        id: restaurantId,
        name: restaurantName,
        cuisine: cuisine,
        address: address,
        city: city,
        phone: phone,
        email: email,
        imageUrl: "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=500", // Placeholder default image
        bannerUrl: "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800",
        isOpen: false,
        isApproved: false,
        approvalStatus: "PENDING",
        rating: 5.0,
        ratingCount: 0,
        averageRating: 5.0,
        commission: 15, // default commission percentage
        createdAt: new Date().getTime(),
      });

      // 4. Create user document at users/{uid}
      await setDoc(doc(db, "users", uid), {
        uid: uid,
        email: email,
        displayName: partnerName,
        phoneNumber: phone,
        role: "RESTAURANT_OWNER",
        restaurantIds: [restaurantId],
        active: true,
        createdAt: new Date().getTime(),
      });

      router.push("/onboarding");
    } catch (err: any) {
      setError(err.message || "Failed to register account.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0a0a0f] bg-radial-at-t from-[#1b1b26] to-[#0a0a0f] flex items-center justify-center p-6 py-12">
      <div className="w-full max-w-2xl glass-card p-8 md:p-10 relative overflow-hidden">
        {/* Glow decoration */}
        <div className="absolute top-0 right-0 w-64 h-64 bg-orange-500/5 rounded-full blur-3xl pointer-events-none"></div>

        {/* Header */}
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-2 mb-2">
            <span className="text-orange-500 font-extrabold text-3xl">FoodFusion</span>
            <span className="text-[10px] bg-orange-500/10 text-orange-400 px-2 py-0.5 rounded font-bold border border-orange-500/20">PARTNER ONBOARDING</span>
          </div>
          <p className="text-slate-400 text-sm">Register your restaurant and join the FoodFusion delivery network.</p>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleRegister} className="space-y-6">
          {/* Section 1: Partner Info */}
          <div>
            <h3 className="text-sm font-semibold uppercase tracking-wider text-orange-500 mb-4 flex items-center gap-1.5">
              <Sparkles size={16} /> Partner Account Details
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="input-label">Full Name</label>
                <div className="relative">
                  <User className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
                  <input
                    type="text"
                    required
                    placeholder="John Doe"
                    className="input-field pl-12"
                    value={partnerName}
                    onChange={(e) => setPartnerName(e.target.value)}
                  />
                </div>
              </div>
              <div>
                <label className="input-label">Email Address</label>
                <div className="relative">
                  <Mail className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
                  <input
                    type="email"
                    required
                    placeholder="john@restaurant.com"
                    className="input-field pl-12"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </div>
              </div>
              <div className="md:col-span-2">
                <label className="input-label">Password</label>
                <div className="relative">
                  <Lock className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
                  <input
                    type="password"
                    required
                    placeholder="Minimum 6 characters"
                    className="input-field pl-12"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Section 2: Restaurant Info */}
          <div className="pt-4 border-t border-white/5">
            <h3 className="text-sm font-semibold uppercase tracking-wider text-orange-500 mb-4 flex items-center gap-1.5">
              <Building size={16} /> Restaurant Information
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="input-label">Restaurant Name</label>
                <div className="relative">
                  <Building className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
                  <input
                    type="text"
                    required
                    placeholder="Pizza Palace"
                    className="input-field pl-12"
                    value={restaurantName}
                    onChange={(e) => setRestaurantName(e.target.value)}
                  />
                </div>
              </div>
              <div>
                <label className="input-label">Cuisines (e.g. Italian, Fast Food)</label>
                <div className="relative">
                  <Sparkles className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
                  <input
                    type="text"
                    required
                    placeholder="Burgers, Shakes, Desserts"
                    className="input-field pl-12"
                    value={cuisine}
                    onChange={(e) => setCuisine(e.target.value)}
                  />
                </div>
              </div>
              <div>
                <label className="input-label">Contact Phone</label>
                <div className="relative">
                  <Phone className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
                  <input
                    type="tel"
                    required
                    placeholder="+91 98765 43210"
                    className="input-field pl-12"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                  />
                </div>
              </div>
              <div>
                <label className="input-label">City</label>
                <div className="relative">
                  <MapPin className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
                  <input
                    type="text"
                    required
                    placeholder="Mumbai"
                    className="input-field pl-12"
                    value={city}
                    onChange={(e) => setCity(e.target.value)}
                  />
                </div>
              </div>
              <div className="md:col-span-2">
                <label className="input-label">Full Address</label>
                <div className="relative">
                  <MapPin className="absolute left-4 top-12 -translate-y-1/2 text-slate-500" size={18} />
                  <textarea
                    required
                    placeholder="Shop No. 12, Ground Floor, Ocean Heights, Link Road..."
                    rows={2}
                    className="input-field pl-12 resize-none"
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                  />
                </div>
              </div>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full btn-primary py-4 justify-center mt-6 text-base font-bold shadow-lg"
          >
            {loading ? "Registering..." : "Submit Registration"}
          </button>
        </form>

        <div className="text-center mt-8 pt-6 border-t border-white/5 text-sm text-slate-400">
          Already registered?{" "}
          <Link href="/login" className="text-orange-500 hover:text-orange-400 font-semibold transition-colors">
            Sign In
          </Link>
        </div>
      </div>
    </div>
  );
}
