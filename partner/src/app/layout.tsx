import "./globals.css";
import { AuthProvider } from "@/context/AuthContext";

export const metadata = {
  title: "FoodFusion Partner Portal",
  description: "Manage your restaurant orders, menu, and analytics in real-time.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="antialiased">
        <AuthProvider>
          {children}
        </AuthProvider>
      </body>
    </html>
  );
}
