export default function WorkRecordsPage() {
  return <PlaceholderPage title="작업 이력" description="농약, 비료, 분갈이, 정리 작업 이력은 이후 단계에서 구현합니다." />;
}

function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <main>
      <p className="text-sm font-semibold text-[#3d6f91]">{title}</p>
      <h1 className="mt-2 text-3xl font-semibold">{title}</h1>
      <p className="mt-3 text-lg text-[#5c6a60]">{description}</p>
    </main>
  );
}
