export default function OrchidGroupsPage() {
  return (
    <main className="space-y-6">
      <section>
        <p className="text-sm font-semibold text-[#3d6f91]">난 묶음 관리</p>
        <h1 className="mt-2 text-3xl font-semibold">동 상세맵 작업 화면</h1>
        <p className="mt-3 max-w-3xl text-lg text-[#5c6a60]">
          이번 단계에서는 라우트와 레이아웃만 준비합니다. CRUD와 이동 UX는 다음 구현 범위입니다.
        </p>
      </section>
      <section className="grid gap-4 lg:grid-cols-3">
        {[1, 2, 3].map((bedNumber) => (
          <div key={bedNumber} className="border border-[#d7ddd4] bg-white p-5 shadow-sm">
            <p className="text-xl font-semibold">{bedNumber}배드</p>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <div className="min-h-40 bg-[#eef4ed] p-4 text-lg">좌</div>
              <div className="min-h-40 bg-[#eef4ed] p-4 text-lg">우</div>
            </div>
          </div>
        ))}
      </section>
    </main>
  );
}
