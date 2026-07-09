"use client";

import { FormEvent, useState } from "react";
import Image from "next/image";
import { useRouter, useSearchParams } from "next/navigation";
import { LockKeyhole } from "lucide-react";
import { login } from "../api/authApi";

export function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await login(username, password);
      router.replace(searchParams.get("next") ?? "/");
      router.refresh();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "로그인에 실패했습니다.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f7f8f5] px-4">
      <section className="w-full max-w-sm rounded-lg border border-[#d7ddd4] bg-white p-6 shadow-sm">
        <div className="flex items-center gap-3">
          <Image src="/flower.png" alt="Logo" width={44} height={44} />
          <div>
            <h1 className="text-xl font-semibold text-[#123524]">난 농장</h1>
            <p className="mt-1 text-sm text-[#68746b]">관리 시스템 로그인</p>
          </div>
        </div>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <label className="block text-sm font-medium text-[#26352c]">
            계정
            <input
              className="mt-2 w-full rounded-md border border-[#cfd8cf] px-3 py-2 text-base outline-none focus:border-[#0f8f45] focus:ring-2 focus:ring-[#dcefe1]"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
            />
          </label>

          <label className="block text-sm font-medium text-[#26352c]">
            비밀번호
            <input
              className="mt-2 w-full rounded-md border border-[#cfd8cf] px-3 py-2 text-base outline-none focus:border-[#0f8f45] focus:ring-2 focus:ring-[#dcefe1]"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
            />
          </label>

          {errorMessage ? (
            <p className="rounded-md bg-[#fff1f1] px-3 py-2 text-sm text-[#b42318]">
              {errorMessage}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 rounded-md bg-[#0f8f45] px-4 py-3 text-base font-semibold text-white disabled:cursor-not-allowed disabled:bg-[#9fb5a6]"
          >
            <LockKeyhole className="h-4 w-4" aria-hidden="true" />
            {isSubmitting ? "로그인 중" : "로그인"}
          </button>
        </form>
      </section>
    </main>
  );
}
