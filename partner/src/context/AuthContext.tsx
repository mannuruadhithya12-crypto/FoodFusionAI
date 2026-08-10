"use client";

import React, { createContext, useContext, useEffect, useState } from "react";
import { User as FirebaseUser, signOut as fbSignOut } from "firebase/auth";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { auth, db } from "../lib/firebase";
import { User as PartnerUser } from "../types";

interface AuthContextType {
  user: FirebaseUser | null;
  profile: PartnerUser | null;
  loading: boolean;
  isPartner: boolean;
  selectedRestaurantId: string | null;
  setSelectedRestaurantId: (id: string | null) => void;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  profile: null,
  loading: true,
  isPartner: false,
  selectedRestaurantId: null,
  setSelectedRestaurantId: () => {},
  signOut: async () => {},
  refreshProfile: async () => {},
});

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [profile, setProfile] = useState<PartnerUser | null>(null);
  const [selectedRestaurantId, setSelectedRestaurantIdState] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchProfile = async (uid: string) => {
    try {
      const docRef = doc(db, "users", uid);
      const docSnap = await getDoc(docRef);
      if (docSnap.exists()) {
        const data = docSnap.data();
        const pUser: PartnerUser = {
          uid: uid,
          email: data.email || "",
          displayName: data.displayName || "",
          phoneNumber: data.phoneNumber || "",
          role: data.role || "RESTAURANT_STAFF",
          restaurantIds: data.restaurantIds || [],
          active: data.active ?? true,
        };
        setProfile(pUser);

        // Pre-select default restaurant if available
        if (pUser.restaurantIds && pUser.restaurantIds.length > 0) {
          setSelectedRestaurantIdState((prev) => prev || pUser.restaurantIds![0]);
        }
      } else {
        // First-time signup via web might not have user document yet, let's create a pending user document
        const pUser: PartnerUser = {
          uid: uid,
          email: auth.currentUser?.email || "",
          displayName: auth.currentUser?.displayName || "",
          role: "RESTAURANT_OWNER",
          restaurantIds: [],
          active: true,
        };
        await setDoc(docRef, {
          uid: uid,
          email: pUser.email,
          displayName: pUser.displayName,
          role: pUser.role,
          restaurantIds: pUser.restaurantIds,
          active: pUser.active,
          createdAt: new Date().getTime(),
        });
        setProfile(pUser);
      }
    } catch (e) {
      console.error("Error fetching user profile", e);
    }
  };

  useEffect(() => {
    const unsubscribe = auth.onAuthStateChanged(async (fbUser) => {
      setUser(fbUser);
      if (fbUser) {
        await fetchProfile(fbUser.uid);
      } else {
        setProfile(null);
        setSelectedRestaurantIdState(null);
      }
      setLoading(false);
    });
    return unsubscribe;
  }, []);

  const refreshProfile = async () => {
    if (user) {
      await fetchProfile(user.uid);
    }
  };

  const setSelectedRestaurantId = (id: string | null) => {
    setSelectedRestaurantIdState(id);
  };

  const signOut = async () => {
    await fbSignOut(auth);
  };

  const isPartner = !!profile && ["RESTAURANT_OWNER", "RESTAURANT_MANAGER", "RESTAURANT_STAFF", "SUPER_ADMIN"].includes(profile.role || "") && profile.active === true;

  return (
    <AuthContext.Provider
      value={{
        user,
        profile,
        loading,
        isPartner,
        selectedRestaurantId,
        setSelectedRestaurantId,
        signOut,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
