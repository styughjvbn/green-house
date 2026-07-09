"use client";

import { LogOut } from "lucide-react";
import { logout } from "../api/authApi";

type LogoutButtonProps = {
  variant?: "header" | "sidebar";
};

export function LogoutButton({ variant = "header" }: LogoutButtonProps) {
  async function handleLogout() {
    try {
      await logout();
    } finally {
      document.cookie = "JSESSIONID=; Path=/; Max-Age=0; SameSite=Lax";
      window.location.assign("/login");
    }
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      className={
        variant === "sidebar"
          ? "mt-3 flex h-8 w-full items-center justify-center gap-1 rounded-md bg-white/10 px-2 text-xs font-medium text-white hover:bg-white/15"
          : "flex h-8 items-center gap-1 rounded-md border border-[#d7ddd4] px-2 text-xs font-medium text-[#4f5d55] hover:bg-[#f5f7f4]"
      }
    >
      <LogOut className="h-4 w-4" aria-hidden="true" />
      로그아웃
    </button>
  );
}
