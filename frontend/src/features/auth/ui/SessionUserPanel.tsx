"use client";

import { useEffect, useState } from "react";
import { UserRound } from "lucide-react";
import { getCurrentUser, type AuthenticatedUser } from "../api/authApi";
import { LogoutButton } from "./LogoutButton";

function getRoleLabel(role: AuthenticatedUser["role"]) {
  return role === "ADMIN" ? "관리자" : "작업자";
}

export function SessionUserPanel() {
  const [user, setUser] = useState<AuthenticatedUser | null>(null);

  useEffect(() => {
    let mounted = true;

    getCurrentUser().then((currentUser) => {
      if (mounted) {
        setUser(currentUser);
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="mt-auto min-w-0 border-t border-white/10 pt-3">
      <div className="min-w-0 overflow-hidden rounded-md bg-white/8 p-3 text-[#dcebe0]">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-white/10">
            <UserRound className="h-4 w-4" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold whitespace-nowrap text-white">
              {user?.username ?? "로그인 계정"}
            </p>
            <p className="mt-0.5 truncate text-xs whitespace-nowrap text-[#b9cbbf]">
              {user ? getRoleLabel(user.role) : "권한 확인 중"}
            </p>
          </div>
        </div>

        <LogoutButton variant="sidebar" />
      </div>
    </div>
  );
}
