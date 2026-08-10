"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, updateDoc, doc, query, orderBy } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { EyeOff, Eye, Star, AlertTriangle } from "lucide-react";
import { Review } from "../../types";

export default function ReviewsPage() {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchReviews = async () => {
    try {
      const q = query(collection(db, "reviews"), orderBy("createdAt", "desc"));
      const snap = await getDocs(q);
      setReviews(snap.docs.map(d => ({ id: d.id, ...d.data() } as Review)));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchReviews();
  }, []);

  const toggleReviewVisibility = async (reviewId: string, currentHidden: boolean) => {
    try {
      await updateDoc(doc(db, "reviews", reviewId), {
        isHidden: !currentHidden,
        updatedAt: new Date().toISOString()
      });
      setLoading(true);
      fetchReviews();
    } catch (err) {
      console.error("Failed to update review", err);
      alert("Failed to update review status.");
    }
  };

  const renderStars = (rating: number) => {
    return (
      <div className="flex space-x-1">
        {[1,2,3,4,5].map(star => (
          <Star key={star} size={14} className={star <= rating ? "text-yellow-400 fill-current" : "text-gray-300"} />
        ))}
      </div>
    );
  }

  if (loading) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-800">Review Moderation</h1>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b">
              <th className="p-4 font-semibold text-gray-600 w-1/4">User / Order Info</th>
              <th className="p-4 font-semibold text-gray-600">Review Content</th>
              <th className="p-4 font-semibold text-gray-600 w-32">Status</th>
              <th className="p-4 font-semibold text-gray-600 text-right w-32">Actions</th>
            </tr>
          </thead>
          <tbody>
            {reviews.length === 0 ? (
              <tr><td colSpan={4} className="p-4 text-center text-gray-500">No reviews found</td></tr>
            ) : (
              reviews.map(review => (
                <tr key={review.id} className={`border-b ${review.isHidden ? 'bg-gray-50' : 'hover:bg-gray-50'}`}>
                  <td className="p-4 text-sm">
                    <p><span className="text-gray-500">User:</span> {review.userId}</p>
                    <p><span className="text-gray-500">Order:</span> {review.orderId}</p>
                    <p><span className="text-gray-500">Rest.:</span> {review.restaurantId}</p>
                  </td>
                  <td className="p-4">
                    <div className="mb-2">{renderStars(review.rating)}</div>
                    <p className={`text-sm ${review.isHidden ? 'text-gray-400 line-through' : 'text-gray-800'}`}>
                      {review.comment || <i>No comment provided</i>}
                    </p>
                    {review.isReported && (
                      <div className="mt-2 flex items-center space-x-1 text-red-600 text-xs font-semibold">
                        <AlertTriangle size={12}/> <span>Reported by users</span>
                      </div>
                    )}
                  </td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${review.isHidden ? 'bg-gray-200 text-gray-800' : 'bg-green-100 text-green-800'}`}>
                      {review.isHidden ? 'Hidden' : 'Public'}
                    </span>
                  </td>
                  <td className="p-4 text-right">
                    {review.isHidden ? (
                      <button onClick={() => toggleReviewVisibility(review.id, true)} className="text-blue-600 hover:text-blue-800 flex items-center justify-end space-x-1 ml-auto text-sm">
                        <Eye size={16} /> <span>Restore</span>
                      </button>
                    ) : (
                      <button onClick={() => toggleReviewVisibility(review.id, false)} className="text-red-600 hover:text-red-800 flex items-center justify-end space-x-1 ml-auto text-sm">
                        <EyeOff size={16} /> <span>Hide</span>
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
