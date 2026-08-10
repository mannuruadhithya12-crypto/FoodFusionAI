"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { doc, getDoc, updateDoc } from "firebase/firestore";
import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
import { db, storage } from "@/lib/firebase";
import { Restaurant } from "@/types";
import { Save, Upload, Compass, Clock, MapPin, Sparkles } from "lucide-react";

export default function SettingsPage() {
  const { selectedRestaurantId } = useAuth();
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  
  // Form states
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [phone, setPhone] = useState("");
  const [cuisine, setCuisine] = useState("");
  const [address, setAddress] = useState("");
  const [city, setCity] = useState("");
  
  const [openingTime, setOpeningTime] = useState("09:00");
  const [closingTime, setClosingTime] = useState("22:00");
  
  const [deliveryRadius, setDeliveryRadius] = useState("5");
  const [deliveryFee, setDeliveryFee] = useState("30");
  const [minimumOrder, setMinimumOrder] = useState("100");
  const [isOpen, setIsOpen] = useState(false);

  const [bannerFile, setBannerFile] = useState<File | null>(null);
  const [logoFile, setLogoFile] = useState<File | null>(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchRestaurant = async () => {
      if (!selectedRestaurantId) return;
      try {
        const docRef = doc(db, "restaurants", selectedRestaurantId);
        const snap = await getDoc(docRef);
        if (snap.exists()) {
          const data = snap.data() as Restaurant;
          setRestaurant(data);
          setName(data.name || "");
          setDescription(data.description || "");
          setPhone(data.phone || "");
          setCuisine(data.cuisine || "");
          setAddress(data.address || "");
          setCity(data.city || "");
          setOpeningTime(data.openingTime || "09:00");
          setClosingTime(data.closingTime || "22:00");
          setDeliveryRadius((data.deliveryRadius || 5).toString());
          setDeliveryFee((data.deliveryFee || 30).toString());
          setMinimumOrder((data.minimumOrder || 100).toString());
          setIsOpen(data.isOpen ?? false);
        }
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchRestaurant();
  }, [selectedRestaurantId]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>, type: "logo" | "banner") => {
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
      if (type === "logo") setLogoFile(file);
      else setBannerFile(file);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRestaurantId) return;

    setError("");
    setSuccess(false);
    setSaving(true);

    try {
      let logoUrl = restaurant?.imageUrl || "";
      let bannerUrl = restaurant?.bannerUrl || "";

      if (logoFile) {
        const fileExt = logoFile.name.split(".").pop();
        const fileName = `logo-${Date.now()}.${fileExt}`;
        const logoRef = ref(storage, `restaurants/${selectedRestaurantId}/${fileName}`);
        const uploadSnapshot = await uploadBytes(logoRef, logoFile);
        logoUrl = await getDownloadURL(uploadSnapshot.ref);
      }

      if (bannerFile) {
        const fileExt = bannerFile.name.split(".").pop();
        const fileName = `banner-${Date.now()}.${fileExt}`;
        const bannerRef = ref(storage, `restaurants/${selectedRestaurantId}/${fileName}`);
        const uploadSnapshot = await uploadBytes(bannerRef, bannerFile);
        bannerUrl = await getDownloadURL(uploadSnapshot.ref);
      }

      const docRef = doc(db, "restaurants", selectedRestaurantId);
      await updateDoc(docRef, {
        name,
        description,
        phone,
        cuisine,
        address,
        city,
        openingTime,
        closingTime,
        deliveryRadius: Number(deliveryRadius),
        deliveryFee: Number(deliveryFee),
        minimumOrder: Number(minimumOrder),
        isOpen,
        imageUrl: logoUrl,
        bannerUrl: bannerUrl,
        updatedAt: new Date().getTime(),
      });

      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (err: any) {
      setError(err.message || "Failed to update restaurant profile.");
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
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight">Restaurant Settings</h1>
          <p className="text-slate-400 text-sm font-medium">Manage operating parameters, addresses, cuisines, and logos.</p>
        </div>

        {success && (
          <div className="p-4 bg-green-500/10 border border-green-500/20 rounded-xl text-green-400 text-sm">
            Restaurant profile updated successfully!
          </div>
        )}

        {error && (
          <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Status Panel */}
          <div className="glass-card p-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-gradient-to-br from-[#12121a] to-[#151522]">
            <div>
              <p className="text-sm font-bold text-slate-200">Accepting Orders</p>
              <p className="text-xs text-slate-500 mt-0.5">Toggle restaurant open/closed state on client-side search feeds.</p>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className={`toggle-switch ${isOpen ? "active" : ""}`}
              />
              <span className="text-xs font-bold text-slate-400">{isOpen ? "OPEN" : "CLOSED"}</span>
            </div>
          </div>

          {/* Section 1: Basic Info */}
          <div className="glass-card p-6 md:p-8 space-y-6">
            <h3 className="text-sm font-bold uppercase tracking-wider text-orange-500 flex items-center gap-2">
              <Sparkles size={16} /> Basic Restaurant Details
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="input-label">Restaurant Name</label>
                <input type="text" required className="input-field" value={name} onChange={(e) => setName(e.target.value)} />
              </div>
              <div>
                <label className="input-label">Contact Phone</label>
                <input type="tel" required className="input-field" value={phone} onChange={(e) => setPhone(e.target.value)} />
              </div>
              <div className="md:col-span-2">
                <label className="input-label">Cuisine (e.g. Italian, Fast Food)</label>
                <input type="text" required className="input-field" value={cuisine} onChange={(e) => setCuisine(e.target.value)} />
              </div>
              <div className="md:col-span-2">
                <label className="input-label">Description / Bio</label>
                <textarea rows={3} className="input-field resize-none" value={description} onChange={(e) => setDescription(e.target.value)} />
              </div>
            </div>
          </div>

          {/* Section 2: Hours & Delivery */}
          <div className="glass-card p-6 md:p-8 space-y-6">
            <h3 className="text-sm font-bold uppercase tracking-wider text-orange-500 flex items-center gap-2">
              <Clock size={16} /> Operating & Delivery Constraints
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
              <div>
                <label className="input-label">Opening Time</label>
                <input type="time" required className="input-field" value={openingTime} onChange={(e) => setOpeningTime(e.target.value)} />
              </div>
              <div>
                <label className="input-label">Closing Time</label>
                <input type="time" required className="input-field" value={closingTime} onChange={(e) => setClosingTime(e.target.value)} />
              </div>
              <div>
                <label className="input-label">Delivery Radius (km)</label>
                <input type="number" required className="input-field" value={deliveryRadius} onChange={(e) => setDeliveryRadius(e.target.value)} />
              </div>
              <div>
                <label className="input-label">Delivery Fee (₹)</label>
                <input type="number" required className="input-field" value={deliveryFee} onChange={(e) => setDeliveryFee(e.target.value)} />
              </div>
              <div>
                <label className="input-label">Minimum Order (₹)</label>
                <input type="number" required className="input-field" value={minimumOrder} onChange={(e) => setMinimumOrder(e.target.value)} />
              </div>
            </div>
          </div>

          {/* Section 3: Location */}
          <div className="glass-card p-6 md:p-8 space-y-6">
            <h3 className="text-sm font-bold uppercase tracking-wider text-orange-500 flex items-center gap-2">
              <MapPin size={16} /> Location Details
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="input-label">City</label>
                <input type="text" required className="input-field" value={city} onChange={(e) => setCity(e.target.value)} />
              </div>
              <div className="md:col-span-2">
                <label className="input-label">Full Address</label>
                <textarea rows={2} required className="input-field resize-none" value={address} onChange={(e) => setAddress(e.target.value)} />
              </div>
            </div>
          </div>

          {/* Section 4: Brand Photos */}
          <div className="glass-card p-6 md:p-8 space-y-6">
            <h3 className="text-sm font-bold uppercase tracking-wider text-orange-500 flex items-center gap-2">
              <Compass size={16} /> Brand Logos & Headers
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label className="input-label">Logo Square Image</label>
                {restaurant?.imageUrl && !logoFile && (
                  <div className="mb-4 rounded-xl overflow-hidden h-32 w-32 border border-white/5">
                    <img src={restaurant.imageUrl} alt="Logo" className="w-full h-full object-cover" />
                  </div>
                )}
                <div className="relative border border-dashed border-white/10 hover:border-white/20 transition-colors rounded-xl p-6 text-center cursor-pointer flex flex-col items-center justify-center">
                  <input type="file" accept="image/*" className="absolute inset-0 opacity-0 cursor-pointer" onChange={(e) => handleFileChange(e, "logo")} />
                  <Upload className="text-slate-500 mb-2" size={20} />
                  <span className="text-xs font-semibold text-slate-300">{logoFile ? logoFile.name : "Replace logo"}</span>
                </div>
              </div>

              <div>
                <label className="input-label">Banner Image</label>
                {restaurant?.bannerUrl && !bannerFile && (
                  <div className="mb-4 rounded-xl overflow-hidden h-32 w-full border border-white/5">
                    <img src={restaurant.bannerUrl} alt="Banner" className="w-full h-full object-cover" />
                  </div>
                )}
                <div className="relative border border-dashed border-white/10 hover:border-white/20 transition-colors rounded-xl p-6 text-center cursor-pointer flex flex-col items-center justify-center">
                  <input type="file" accept="image/*" className="absolute inset-0 opacity-0 cursor-pointer" onChange={(e) => handleFileChange(e, "banner")} />
                  <Upload className="text-slate-500 mb-2" size={20} />
                  <span className="text-xs font-semibold text-slate-300">{bannerFile ? bannerFile.name : "Replace banner"}</span>
                </div>
              </div>
            </div>
          </div>

          <button type="submit" disabled={saving} className="w-full btn-primary py-3.5 justify-center font-bold text-base shadow-lg">
            {saving ? "Saving Changes..." : "Save Settings"}
          </button>
        </form>
      </div>
    </PartnerLayout>
  );
}
