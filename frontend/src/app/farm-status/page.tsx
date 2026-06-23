export default function FarmStatusPage() {
  return (
    <main className="space-y-6">
      <section>
        <p className="text-sm font-semibold text-[#3d6f91]">농장 현황</p>
        <h1 className="mt-2 text-3xl font-semibold">전체 농장맵 조회</h1>
        <p className="mt-3 max-w-3xl text-lg text-[#5c6a60]">
          다음 단계에서 15개 동 전체 상태, 줌 단계, 선택 범위의 난 묶음 목록을 연결합니다.
        </p>
      </section>
      <section className="grid grid-cols-3 gap-3 md:grid-cols-5">
        {Array.from({ length: 15 }, (_, index) => (
          <div key={index} className="border border-[#d7ddd4] bg-white p-5 text-center text-xl font-semibold shadow-sm">
            {index + 1}동
          </div>
        ))}
      </section>
    </main>
  );
}
