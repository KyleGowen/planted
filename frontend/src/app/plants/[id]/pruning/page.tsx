"use client";

import { useState, useRef } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Camera, X, Scissors } from "lucide-react";
import Link from "next/link";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { ButtonLink } from "@/components/ui/button-link";
import { PlantStatusCard } from "@/components/plant/PlantStatusCard";
import { getPlant, requestPruningAnalysis } from "@/lib/api";
import type { AnalysisStatus } from "@/types/plant";

export default function PruningPage() {
  const { id } = useParams<{ id: string }>();
  const plantId = Number(id);
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [images, setImages] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [submittedAnalysisStatus, setSubmittedAnalysisStatus] =
    useState<AnalysisStatus | null>(null);

  const { data: plant } = useQuery({
    queryKey: ["plant", plantId],
    queryFn: () => getPlant(plantId),
    refetchInterval: submittedAnalysisStatus === "PENDING" || submittedAnalysisStatus === "PROCESSING"
      ? 3000
      : false,
  });

  const mutation = useMutation({
    mutationFn: () => requestPruningAnalysis(plantId, images),
    onSuccess: (data) => {
      setSubmittedAnalysisStatus(data.status);
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
    },
  });

  function addImages(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    const remaining = 3 - images.length;
    const newFiles = files.slice(0, remaining);
    setImages((prev) => [...prev, ...newFiles]);
    newFiles.forEach((file) => {
      const reader = new FileReader();
      reader.onload = (ev) =>
        setPreviews((prev) => [...prev, ev.target?.result as string]);
      reader.readAsDataURL(file);
    });
  }

  function removeImage(index: number) {
    setImages((prev) => prev.filter((_, i) => i !== index));
    setPreviews((prev) => prev.filter((_, i) => i !== index));
  }

  // Find latest pruning analysis result
  const latestPruningAnalysis = plant?.latestAnalysis?.analysisType === "PRUNING"
    ? plant.latestAnalysis
    : null;

  return (
    <main className="mx-auto max-w-lg px-4 py-8">
      <Link
        href={`/plants/${plantId}`}
        className="inline-flex items-center gap-1.5 text-sm text-stone-400 hover:text-stone-600 mb-8"
      >
        <ArrowLeft size={14} />
        Back to plant
      </Link>

      <div className="flex items-center gap-2 mb-2">
        <Scissors size={18} className="text-green-600" />
        <h1 className="text-xl font-semibold text-stone-800">Pruning analysis</h1>
      </div>
      <p className="text-sm text-stone-400 mb-6">
        Upload 1–3 photos from different angles. We&apos;ll give you conservative,
        goal-aligned pruning guidance — including &ldquo;no pruning needed&rdquo; if that&apos;s the right answer.
      </p>

      {/* Image upload area */}
      {!submittedAnalysisStatus && (
        <>
          <div className="flex gap-3 mb-4 flex-wrap">
            {previews.map((src, i) => (
              <div key={i} className="relative h-24 w-24 rounded-xl overflow-hidden bg-stone-100">
                <Image src={src} alt={`Photo ${i + 1}`} fill className="object-cover" />
                <button
                  type="button"
                  onClick={() => removeImage(i)}
                  className="absolute top-1 right-1 bg-white rounded-full p-0.5 shadow"
                >
                  <X size={12} className="text-stone-500" />
                </button>
              </div>
            ))}
            {images.length < 3 && (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="h-24 w-24 rounded-xl border-2 border-dashed border-stone-200 bg-stone-50 flex flex-col items-center justify-center hover:border-stone-300 transition-colors"
              >
                <Camera size={20} className="text-stone-300 mb-1" />
                <span className="text-xs text-stone-300">
                  {images.length === 0 ? "Add photo" : "Add more"}
                </span>
              </button>
            )}
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/heic"
            multiple
            onChange={addImages}
            className="hidden"
          />

          <p className="text-xs text-stone-400 mb-6">
            {images.length} of 3 photos selected
          </p>

          {mutation.isError && (
            <div className="rounded-lg bg-amber-50 border border-amber-100 px-3 py-2 text-sm text-amber-700 mb-4">
              {(mutation.error as Error).message}
            </div>
          )}

          <Button
            onClick={() => mutation.mutate()}
            disabled={images.length === 0 || mutation.isPending}
            className="w-full"
          >
            {mutation.isPending ? "Submitting…" : "Request pruning analysis"}
          </Button>
        </>
      )}

      {/* Status after submission */}
      {submittedAnalysisStatus && (
        <div className="space-y-4">
          <PlantStatusCard status={submittedAnalysisStatus} />

          {/* Show result when completed */}
          {latestPruningAnalysis?.status === "COMPLETED" && (
            <div className="rounded-xl border border-stone-200 bg-white p-4 space-y-3">
              <h2 className="font-medium text-stone-700">Pruning recommendation</h2>
              {latestPruningAnalysis.pruningGuidance && (
                <p className="text-sm text-stone-600">{latestPruningAnalysis.pruningGuidance}</p>
              )}
              {latestPruningAnalysis.goalSuggestions && (
                <div className="rounded-md bg-green-50 px-3 py-2 text-xs text-green-700 border border-green-100">
                  <span className="font-medium">Goal alignment: </span>
                  {latestPruningAnalysis.goalSuggestions}
                </div>
              )}
            </div>
          )}

          <ButtonLink href={`/plants/${plantId}`} variant="outline" size="sm">Return to plant</ButtonLink>
        </div>
      )}
    </main>
  );
}
