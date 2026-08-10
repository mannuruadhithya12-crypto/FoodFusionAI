"use client";

import { useState } from "react";
import { ref, uploadBytesResumable, getDownloadURL } from "firebase/storage";
import Image from "next/image";
import { storage } from "../lib/firebase";
import { UploadCloud, X, Loader2 } from "lucide-react";

interface ImageUploadProps {
  label: string;
  folder: string;
  currentUrl?: string;
  onUploadSuccess: (url: string) => void;
  onUploadError: (error: string) => void;
  onRemove: () => void;
}

export default function ImageUpload({ label, folder, currentUrl, onUploadSuccess, onUploadError, onRemove }: ImageUploadProps) {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // File validation
    if (!file.type.startsWith('image/')) {
      onUploadError("Please upload an image file (JPEG, PNG, WEBP).");
      return;
    }
    if (file.size > 5 * 1024 * 1024) { // 5MB limit
      onUploadError("Image size must be less than 5MB.");
      return;
    }

    setUploading(true);
    setProgress(0);

    const storageRef = ref(storage, `${folder}/${Date.now()}_${file.name}`);
    const uploadTask = uploadBytesResumable(storageRef, file);

    uploadTask.on(
      "state_changed",
      (snapshot) => {
        const p = (snapshot.bytesTransferred / snapshot.totalBytes) * 100;
        setProgress(p);
      },
      (error) => {
        console.error("Upload error", error);
        setUploading(false);
        onUploadError("Failed to upload image. Please try again.");
      },
      async () => {
        try {
          const downloadURL = await getDownloadURL(uploadTask.snapshot.ref);
          setUploading(false);
          onUploadSuccess(downloadURL);
        } catch {
           setUploading(false);
           onUploadError("Failed to get download URL.");
        }
      }
    );
  };

  const handleRemove = async () => {
    // Note: We don't necessarily delete from storage immediately here to prevent 
    // accidental deletion of in-use images if they cancel the form, 
    // but a robust system would clean up orphaned files.
    // For Phase 11, we just clear the URL in the form.
    onRemove();
  };

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-gray-700">{label}</label>
      
      {currentUrl ? (
        <div className="relative inline-block border rounded p-1">
          <Image src={currentUrl} alt="Uploaded" width={128} height={128} className="h-32 w-auto object-cover rounded" />
          <button 
            type="button" 
            onClick={handleRemove}
            className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full p-1 shadow hover:bg-red-600"
          >
            <X size={14} />
          </button>
        </div>
      ) : (
        <div className="border-2 border-dashed border-gray-300 rounded-lg p-6 text-center hover:bg-gray-50 transition-colors">
          {uploading ? (
            <div className="flex flex-col items-center justify-center text-blue-600">
              <Loader2 className="animate-spin mb-2" size={24} />
              <span className="text-sm">Uploading... {Math.round(progress)}%</span>
            </div>
          ) : (
            <>
              <UploadCloud className="mx-auto text-gray-400 mb-2" size={28} />
              <label className="cursor-pointer">
                <span className="text-blue-600 hover:text-blue-700 text-sm font-medium">Click to upload</span>
                <span className="text-gray-500 text-sm"> or drag and drop</span>
                <input type="file" className="hidden" accept="image/*" onChange={handleFileChange} />
              </label>
              <p className="text-xs text-gray-500 mt-1">PNG, JPG, WEBP up to 5MB</p>
            </>
          )}
        </div>
      )}
    </div>
  );
}
