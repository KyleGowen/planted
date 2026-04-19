"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Upload, ImagePlus, ArrowLeft } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { registerPlant } from "@/lib/api";
import type { PlantGrowingContext } from "@/types/plant";

export default function UploadPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [goalsText, setGoalsText] = useState("");
  const [lastWateredAt, setLastWateredAt] = useState("");
  const [geoCity, setGeoCity] = useState("");
  const [geoState, setGeoState] = useState("");
  const [geoCountry, setGeoCountry] = useState("");
  const [growingContext, setGrowingContext] = useState<PlantGrowingContext>("INDOOR");
  const [isDragging, setIsDragging] = useState(false);

  const mutation = useMutation({
    mutationFn: () => {
      if (!imageFile) throw new Error("Please select an image");
      const formData = new FormData();
      formData.append("image", imageFile);
      if (name) formData.append("name", name);
      if (goalsText) formData.append("goalsText", goalsText);
      if (lastWateredAt) {
        formData.append("lastWateredAt", new Date(lastWateredAt).toISOString());
      }
      if (geoCountry) formData.append("geoCountry", geoCountry);
      if (geoState) formData.append("geoState", geoState);
      if (geoCity) formData.append("geoCity", geoCity);
      formData.append("growingContext", growingContext);
      return registerPlant(formData);
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      router.push(`/plants/${data.plantId}`);
    },
  });

  function acceptImageFile(file: File | null | undefined) {
    if (!file) return;
    if (!file.type.startsWith("image/")) return;
    setImageFile(file);
    const reader = new FileReader();
    reader.onload = (ev) => setImagePreview(ev.target?.result as string);
    reader.readAsDataURL(file);
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    acceptImageFile(e.target.files?.[0]);
  }

  useEffect(() => {
    function onPaste(e: ClipboardEvent) {
      const item = Array.from(e.clipboardData?.items ?? []).find((i) =>
        i.type.startsWith("image/"),
      );
      if (!item) return;
      const file = item.getAsFile();
      if (file) acceptImageFile(file);
    }
    window.addEventListener("paste", onPaste);
    return () => window.removeEventListener("paste", onPaste);
  }, []);

  return (
    <main className="mx-auto max-w-lg px-4 py-8">
      {/* Back */}
      <Link
        href="/plants"
        className="inline-flex items-center gap-1.5 text-sm text-stone-400 hover:text-stone-600 mb-8"
      >
        <ArrowLeft size={14} />
        Back to plants
      </Link>

      <h1 className="text-2xl font-semibold text-stone-800 tracking-tight mb-1">
        Add a plant
      </h1>
      <p className="text-sm text-stone-400 mb-8">
        Upload a photo and we&apos;ll identify the species and build a care profile.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate();
        }}
        className="space-y-6"
      >
        {/* Image picker */}
        <div>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            onDragEnter={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setIsDragging(true);
            }}
            onDragOver={(e) => {
              e.preventDefault();
              e.stopPropagation();
              if (!isDragging) setIsDragging(true);
            }}
            onDragLeave={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setIsDragging(false);
            }}
            onDrop={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setIsDragging(false);
              acceptImageFile(e.dataTransfer.files?.[0]);
            }}
            className={`relative w-full aspect-[4/3] rounded-xl border-2 border-dashed flex flex-col items-center justify-center transition-colors overflow-hidden ${
              isDragging
                ? "border-stone-400 bg-stone-100"
                : "border-stone-200 bg-stone-50 hover:border-stone-300 hover:bg-stone-100"
            }`}
          >
            {imagePreview ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={imagePreview}
                alt="Preview"
                className="absolute inset-0 w-full h-full object-cover rounded-xl pointer-events-none"
              />
            ) : (
              <>
                <ImagePlus size={32} className="text-stone-300 mb-2" />
                <p className="text-sm text-stone-400">Tap, drop, or paste a photo</p>
                <p className="text-xs text-stone-300 mt-0.5">JPG, PNG, WebP, HEIC</p>
              </>
            )}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif,image/heic"
            onChange={handleFileChange}
            className="hidden"
          />
        </div>

        {/* Fields */}
        <div className="space-y-4">
          <div>
            <Label htmlFor="name" className="text-stone-600">
              Name <span className="text-stone-300 font-normal">(optional)</span>
            </Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. My fiddle leaf, Bathroom fern…"
              className="mt-1.5"
            />
          </div>

          <div>
            <Label className="text-stone-600">
              Geographic location <span className="text-stone-300 font-normal">(optional — improves watering advice)</span>
            </Label>
            <p className="text-xs text-stone-400 mt-0.5 mb-2">
              Used to factor in local climate when calculating how often to water.
            </p>
            <div className="grid grid-cols-3 gap-2">
              <Input
                value={geoCity}
                onChange={(e) => setGeoCity(e.target.value)}
                placeholder="City"
              />
              <Input
                value={geoState}
                onChange={(e) => setGeoState(e.target.value)}
                placeholder="State / Region"
              />
              <Input
                value={geoCountry}
                onChange={(e) => setGeoCountry(e.target.value)}
                placeholder="Country"
              />
            </div>
          </div>

          <div>
            <Label htmlFor="growingContext" className="text-stone-600">
              Growing environment
            </Label>
            <p className="text-xs text-stone-400 mt-0.5 mb-2">
              Outdoor plants use local weather (rain, heat) in care reminders.
              We&apos;ll derive the location from the city/state/country above &mdash; no coordinates required.
            </p>
            <select
              id="growingContext"
              value={growingContext}
              onChange={(e) => setGrowingContext(e.target.value as PlantGrowingContext)}
              className="mt-1.5 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm text-stone-800 focus:outline-none focus:ring-1 focus:ring-stone-400"
            >
              <option value="INDOOR">Indoor</option>
              <option value="OUTDOOR">Outdoor</option>
            </select>
            {growingContext === "OUTDOOR" && !geoCity.trim() && (
              <p className="mt-2 text-xs text-amber-700">
                Add a city above so we can pull local weather for reminders.
              </p>
            )}
          </div>

          <div>
            <Label htmlFor="initialNotes" className="text-stone-600">
              Initial Notes <span className="text-stone-300 font-normal">(optional)</span>
            </Label>
            <p className="text-xs text-stone-400 mt-0.5 mb-2">
              Included when we run the first identification and care prompts. Feel free to add any species or origin hints you already know — e.g. common name, nursery label, where you bought or collected it.
            </p>
            <textarea
              id="initialNotes"
              value={goalsText}
              onChange={(e) => setGoalsText(e.target.value)}
              placeholder="e.g. Labeled 'Raven ZZ' at the nursery, brought back from my mom's garden in Florida, hoping it fills in on the right…"
              className="mt-1.5 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm text-stone-800 placeholder:text-stone-300 focus:outline-none focus:ring-1 focus:ring-stone-400 resize-none min-h-[80px]"
            />
          </div>

          <div>
            <Label htmlFor="lastWatered" className="text-stone-600">
              Last watered <span className="text-stone-300 font-normal">(optional)</span>
            </Label>
            <Input
              id="lastWatered"
              type="date"
              value={lastWateredAt}
              onChange={(e) => setLastWateredAt(e.target.value)}
              className="mt-1.5"
            />
          </div>
        </div>

        {/* Error */}
        {mutation.isError && (
          <div className="rounded-lg bg-amber-50 border border-amber-100 px-3 py-2 text-sm text-amber-700">
            {(mutation.error as Error).message}
          </div>
        )}

        {/* Submit */}
        <Button
          type="submit"
          className="w-full"
          disabled={!imageFile || mutation.isPending}
        >
          {mutation.isPending ? (
            "Uploading…"
          ) : (
            <>
              <Upload size={15} className="mr-2" />
              Add plant
            </>
          )}
        </Button>
      </form>
    </main>
  );
}
