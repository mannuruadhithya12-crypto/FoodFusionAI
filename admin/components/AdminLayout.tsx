"use client";

import { useAuth } from "../context/AuthContext";
import { useRouter, usePathname } from "next/navigation";
import { useEffect } from "react";
import Link from "next/link";
import { LayoutDashboard, Store, Pizza, LogOut, Users, Settings, Tag, Ticket, FileText, MessageSquare, ClipboardList, Truck } from "lucide-react";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { user, isAdmin, loading, signOut } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && (!user || !isAdmin)) {
      if (pathname !== "/login") {
        router.push("/login");
      }
    }
  }, [user, isAdmin, loading, pathname, router]);

  if (loading) {
    return <div className="flex h-screen w-screen items-center justify-center">Loading...</div>;
  }

  if (!user || !isAdmin) {
    return <>{children}</>; // Login page handles its own rendering
  }

  const navItems = [
    { name: "Dashboard", href: "/", icon: LayoutDashboard },
    { name: "Orders", href: "/orders", icon: ClipboardList },
    { name: "Restaurants", href: "/restaurants", icon: Store },
    { name: "Foods", href: "/foods", icon: Pizza },
    { name: "Categories", href: "/categories", icon: Tag },
    { name: "Offers", href: "/offers", icon: Ticket },
    { name: "Coupons", href: "/coupons", icon: Ticket },
    { name: "Users", href: "/users", icon: Users },
    { name: "Drivers", href: "/drivers", icon: Truck },
    { name: "Reviews", href: "/reviews", icon: MessageSquare },
    { name: "Audit Logs", href: "/audit-logs", icon: FileText },
    { name: "Settings", href: "/settings", icon: Settings },
  ];

  return (
    <div className="flex h-screen bg-gray-100">
      {/* Sidebar */}
      <aside className="w-64 bg-white shadow-md flex flex-col">
        <div className="p-4 text-2xl font-bold text-gray-800 border-b">
          FoodFusion Admin
        </div>
        <nav className="flex-1 p-4 space-y-2 overflow-y-auto">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
            return (
              <Link
                key={item.name}
                href={item.href}
                className={`flex items-center space-x-3 px-4 py-3 rounded-lg transition-colors ${
                  isActive
                    ? "bg-blue-50 text-blue-600 font-medium"
                    : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                }`}
              >
                <Icon size={20} />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </nav>
        <div className="p-4 border-t">
          <button
            onClick={signOut}
            className="flex items-center space-x-3 px-4 py-3 text-red-600 hover:bg-red-50 rounded-lg w-full transition-colors"
          >
            <LogOut size={20} />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 overflow-y-auto p-8">
        {children}
      </main>
    </div>
  );
}
