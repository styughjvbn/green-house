import Link from "next/link";

const summaryItems = [
  { label: "동", value: "15개", tone: "border-[#6b8f71]" },
  { label: "물리 배드", value: "45개", tone: "border-[#3d6f91]" },
  { label: "논리 구역", value: "90개", tone: "border-[#b48235]" },
];

const quickLinks = [
  { href: "/farm-status", label: "농장 현황", description: "전체 농장맵과 선택 범위의 난 묶음을 조회합니다." },
  { href: "/orchid-groups", label: "난 묶음 관리", description: "선택한 동의 상세맵에서 난 묶음을 관리합니다." },
];

export default function DashboardPage() {
  return (
    <main className="space-y-8">
      <section className="space-y-2">
        <p className="text-sm font-semibold text-[#3d6f91]">대시보드</p>
        <h1 className="text-3xl font-semibold">농장 운영 상태</h1>
        <p className="max-w-3xl text-lg text-[#5c6a60]">
          1차 구현 범위에서는 농장 구조와 조회 API를 준비합니다. 작업 화면은 다음 단계에서 실제 데이터와 연결합니다.
        </p>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        {summaryItems.map((item) => (
          <div key={item.label} className={`border-l-4 ${item.tone} bg-white p-5 shadow-sm`}>
            <p className="text-base text-[#5c6a60]">{item.label}</p>
            <p className="mt-3 text-3xl font-semibold">{item.value}</p>
          </div>
        ))}
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        {quickLinks.map((link) => (
          <Link
            key={link.href}
            href={link.href}
            className="block border border-[#d7ddd4] bg-white p-6 shadow-sm transition hover:border-[#3d6f91]"
          >
            <p className="text-xl font-semibold">{link.label}</p>
            <p className="mt-2 text-base text-[#5c6a60]">{link.description}</p>
          </Link>
        ))}
      </section>
    </main>
  );
}
