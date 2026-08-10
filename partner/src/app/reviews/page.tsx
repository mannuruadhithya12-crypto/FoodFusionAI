"use client";

import React, { useEffect, useState } from "react";
import PartnerLayout from "@/components/PartnerLayout";
import { useAuth } from "@/context/AuthContext";
import { collection, query, where, onSnapshot, orderBy } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { Review } from "@/types";
import { Star, MessageSquare } from "lucide-react";

export default function ReviewsPage() {
  const { selectedRestaurantId } = useAuth();
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  
  // Stats
  const [averageRating, setAverageRating] = useState(5.0);
  const [ratingCount, setRatingCount] = useState(0);

  useEffect(() => {
    if (!selectedRestaurantId) return;

    setLoading(true);
    const q = query(
      collection(db, "reviews"),
      where("restaurantId", "==", selectedRestaurantId)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list: Review[] = [];
      let totalStars = 0;

      snapshot.forEach((doc) => {
        const data = doc.data();
        const r = { id: doc.id, ...data } as Review;
        list.push(r);
        totalStars += r.rating || 0;
      });

      // Sort by date desc
      list.sort((a, b) => {
        const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return timeB - timeA;
      });

      setReviews(list);
      setRatingCount(list.length);
      setAverageRating(list.length > 0 ? Number((totalStars / list.length).toFixed(1)) : 5.0);
      setLoading(false);
    });

    return unsubscribe;
  }, [selectedRestaurantId]);

  return (
    <PartnerLayout>
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight flex items-center gap-2">
            <Star className="text-orange-500 fill-orange-500/20" /> Customer Reviews
          </h1>
          <p className="text-slate-400 text-sm font-medium">Read review comments and rating distributions from users.</p>
        </div>

        {loading ? (
          <div className="flex justify-center items-center py-32">
            <div className="w-10 h-10 border-4 border-t-orange-500 border-white/10 rounded-full animate-spin"></div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Summary Panel */}
            <div className="glass-card p-6 flex flex-col items-center justify-center text-center h-fit bg-gradient-to-br from-[#12121a] to-[#151522]">
              <p className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Average Score</p>
              <p className="text-5xl font-black text-slate-100 tracking-tight">{averageRating}</p>
              <div className="flex gap-1.5 my-3">
                {[...Array(5)].map((_, i) => (
                  <Star
                    key={i}
                    size={16}
                    className={i < Math.round(averageRating) ? "text-orange-500 fill-orange-500" : "text-slate-700"}
                  />
                ))}
              </div>
              <p className="text-xs text-slate-500 font-semibold">{ratingCount} Verified Reviews</p>
            </div>

            {/* Reviews List */}
            <div className="glass-card p-6 md:col-span-2 space-y-4">
              <h2 className="text-base font-bold text-slate-200">Customer Testimonials</h2>
              
              {reviews.length === 0 ? (
                <div className="text-center py-12 text-slate-600 text-sm">
                  No reviews submitted yet for this restaurant.
                </div>
              ) : (
                <div className="divide-y divide-white/5">
                  {reviews.map((rev) => (
                    <div key={rev.id} className="py-4 first:pt-0 last:pb-0">
                      <div className="flex justify-between items-start gap-2 mb-2">
                        <div>
                          <p className="text-sm font-bold text-slate-200">{rev.userName || "Verified Customer"}</p>
                          <div className="flex gap-1 mt-1">
                            {[...Array(5)].map((_, i) => (
                              <Star
                                key={i}
                                size={12}
                                className={i < rev.rating ? "text-orange-500 fill-orange-500" : "text-slate-700"}
                              />
                            ))}
                          </div>
                        </div>
                        <span className="text-[10px] text-slate-500 font-medium">
                          {rev.createdAt ? new Date(rev.createdAt).toLocaleDateString() : ""}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 leading-relaxed italic">
                        "{rev.comment || "No comment provided."}"
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </PartnerLayout>
  );
}
