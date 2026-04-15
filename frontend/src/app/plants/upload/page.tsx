"use client";

import { useState, useRef } from "react";
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
  const [location, setLocation] = useState("");
  const [goalsText, setGoalsText] = useState("");
  const [lastWateredAt, setLastWateredAt] = useState("");
  const [geoCity, setGeoCity] = useState("");
  const [geoState, setGeoState] = useState("");
  const [geoCountry, setGeoCountry] = useState("");
  const [growingContext, setGrowingContext] = useState<PlantGrowingContext>("INDOOR");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");

  const mutation = useMutation({
    mutationFn: () => {
      if (!imageFile) throw new Error("Please select an image");
      const formData = new FormData();
      formData.append("image", imageFile);
      if (name) formData.append("name", name);
      if (location) formData.append("location", location);
      if (goalsText) formData.append("goalsText", goalsText);
      if (lastWateredAt) {
        formData.append("lastWateredAt", new Date(lastWateredAt).toISOString());
      }
      if (geoCountry) formData.append("geoCountry", geoCountry);
      if (geoState) formData.append("geoState", geoState);
      if (geoCity) formData.append("geoCity", geoCity);
      formData.append("growingContext", growingContext);
      if (growingContext === "OUTDOOR") {
        if (latitude.trim()) formData.append("latitude", latitude.trim());
        if (longitude.trim()) formData.append("longitude", longitude.trim());
      }
      return registerPlant(formData);
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      router.push(`/plants/${data.plantId}`);
    },
  });

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setImageFile(file);
    const reader = new FileReader();
    reader.onload = (ev) => setImagePreview(ev.target?.result as string);
    reader.readAsDataURL(file);
  }

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
            className="relative w-full aspect-[4/3] rounded-xl border-2 border-dashed border-stone-200 bg-stone-50 flex flex-col items-center justify-center hover:border-stone-300 hover:bg-stone-100 transition-colors overflow-hidden"
          >
            {imagePreview ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={imagePreview}
                alt="Preview"
                className="absolute inset-0 w-full h-full object-cover rounded-xl"
              />
            ) : (
              <>
                <ImagePlus size={32} className="text-stone-300 mb-2" />
                <p className="text-sm text-stone-400">Tap to select a photo</p>
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
            <Label htmlFor="location" className="text-stone-600">
              Indoor spot <span className="text-stone-300 font-normal">(optional)</span>
            </Label>
            <Input
              id="location"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="e.g. Living room, South-facing window…"
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
              Outdoor plants can use local weather (rain, heat) in care reminders when you add coordinates below.
              Approximate latitude/longitude are stored only for weather lookups.
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
            {growingContext === "OUTDOOR" && (
              <div className="grid grid-cols-2 gap-2 mt-2">
                <div>
                  <Label htmlFor="latitude" className="text-xs text-stone-500">
                    Latitude (optional)
                  </Label>
                  <Input
                    id="latitude"
                    type="text"
                    inputMode="decimal"
                    value={latitude}
                    onChange={(e) => setLatitude(e.target.value)}
                    placeholder="e.g. 40.7128"
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label htmlFor="longitude" className="text-xs text-stone-500">
                    Longitude (optional)
                  </Label>
                  <Input
                    id="longitude"
                    type="text"
                    inputMode="decimal"
                    value={longitude}
                    onChange={(e) => setLongitude(e.target.value)}
                    placeholder="e.g. -74.0060"
                    className="mt-1"
                  />
                </div>
              </div>
            )}
          </div>

          <div>
            <Label htmlFor="goals" className="text-stone-600">
              Goals <span className="text-stone-300 font-normal">(optional)</span>
            </Label>
            <textarea
              id="goals"
              value={goalsText}
              onChange={(e) => setGoalsText(e.target.value)}
              placeholder="e.g. I want it to look bushier, fill in on the right side…"
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
