"use client";

export function SpeciesOverviewProse({ text }: { text: string }) {
  const paragraphs = text
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);
  return (
    <div className="space-y-2.5">
      {paragraphs.map((para, i) => (
        <p key={i} className="text-sm text-stone-600 leading-relaxed">
          {para}
        </p>
      ))}
    </div>
  );
}
