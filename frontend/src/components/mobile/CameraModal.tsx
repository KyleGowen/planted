"use client";

import {
  useRef, useState, useEffect, useCallback,
} from "react";
import {
  useMutation, useQuery, useQueryClient,
} from "@tanstack/react-query";
import Image from "next/image";
import {
  X, Camera, Upload, ArrowLeft, Check, Droplets, Leaf, Scissors, Sprout,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { plantImageSrc } from "@/lib/plantMediaUrl";
import {
  registerPlant, listPlants, recordWatering, recordFertilizer, recordPrune,
  uploadPlantPhoto, addHistoryNote,
} from "@/lib/api";
import type { PlantGrowingContext, PlantListItemResponse } from "@/types/plant";

// ── Types ────────────────────────────────────────────────────────────────────

type Step =
  | "capture"            // live camera / file picker
  | "route"             // choose New Plant vs Update Existing
  | "new-plant-form"    // fill in name, growing context → registerPlant
  | "update-pick-plant" // pick which existing plant
  | "update-pick-action"// pick care action
  | "success";          // done confirmation

type CareAction = "water" | "fertilize" | "prune" | "photo" | "note";

interface Props {
  onClose: () => void;
  onComplete: () => void;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

async function captureFrameAsFile(video: HTMLVideoElement): Promise<File> {
  const canvas = document.createElement("canvas");
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  const ctx = canvas.getContext("2d")!;
  ctx.drawImage(video, 0, 0);
  return new Promise<File>((resolve) => {
    canvas.toBlob((blob) => {
      resolve(new File([blob!], "capture.jpg", { type: "image/jpeg" }));
    }, "image/jpeg", 0.92);
  });
}

// ── Sub-components ───────────────────────────────────────────────────────────

function StepHeader({
  onBack,
  onClose,
  title,
}: {
  onBack?: () => void;
  onClose: () => void;
  title: string;
}) {
  return (
    <div className="flex items-center px-4 py-3 border-b border-stone-100">
      {onBack ? (
        <button
          type="button"
          onClick={onBack}
          className="min-h-[44px] min-w-[44px] flex items-center text-stone-500"
          aria-label="Back"
        >
          <ArrowLeft size={20} />
        </button>
      ) : (
        <div className="w-11" />
      )}
      <h2 className="flex-1 text-center text-base font-semibold text-stone-800">{title}</h2>
      <button
        type="button"
        onClick={onClose}
        className="min-h-[44px] min-w-[44px] flex items-center justify-end text-stone-400"
        aria-label="Close"
      >
        <X size={20} />
      </button>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function CameraModal({ onClose, onComplete }: Props) {
  const queryClient = useQueryClient();

  const [step, setStep] = useState<Step>("capture");
  const [capturedFile, setCapturedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [selectedPlant, setSelectedPlant] = useState<PlantListItemResponse | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);

  // New plant form
  const [plantName, setPlantName] = useState("");
  const [growingContext, setGrowingContext] = useState<PlantGrowingContext>("INDOOR");

  // Camera stream
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Existing plants query (shared cache)
  const { data: plants } = useQuery({
    queryKey: ["plants"],
    queryFn: listPlants,
    staleTime: 30_000,
  });
  const activePlants = (plants ?? []).filter((p) => p.status === "ACTIVE");

  // ── Camera lifecycle ────────────────────────────────────────────────────

  const startCamera = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment", width: { ideal: 1920 }, height: { ideal: 1080 } },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
    } catch {
      setCameraError("Camera permission denied. Select a photo from your library instead.");
    }
  }, []);

  function stopCamera() {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  }

  useEffect(() => {
    if (step === "capture") {
      const id = window.setTimeout(() => {
        void startCamera();
      }, 0);
      return () => {
        clearTimeout(id);
        stopCamera();
      };
    }
    stopCamera();
    return () => stopCamera();
  }, [step, startCamera]);

  // ── Capture handlers ────────────────────────────────────────────────────

  async function handleShutter() {
    if (!videoRef.current) return;
    const file = await captureFrameAsFile(videoRef.current);
    acceptFile(file);
  }

  function acceptFile(file: File) {
    if (!file.type.startsWith("image/")) return;
    setCapturedFile(file);
    const reader = new FileReader();
    reader.onload = (e) => setPreviewUrl(e.target?.result as string);
    reader.readAsDataURL(file);
    setStep("route");
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) acceptFile(file);
    e.target.value = "";
  }

  // ── New plant mutation ──────────────────────────────────────────────────

  const registerMutation = useMutation({
    mutationFn: () => {
      const fd = new FormData();
      fd.append("image", capturedFile!);
      if (plantName.trim()) fd.append("name", plantName.trim());
      fd.append("growingContext", growingContext);
      return registerPlant(fd);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      setStep("success");
    },
  });

  // ── Update existing mutations ──────────────────────────────────────────

  const waterMutation = useMutation({
    mutationFn: (id: number) =>
      recordWatering(id, { wateredAt: new Date().toISOString() }),
    onSuccess: finishUpdate,
  });
  const fertilizeMutation = useMutation({
    mutationFn: (id: number) =>
      recordFertilizer(id, { fertilizedAt: new Date().toISOString() }),
    onSuccess: finishUpdate,
  });
  const pruneMutation = useMutation({
    mutationFn: (id: number) =>
      recordPrune(id, new Date().toISOString(), undefined, capturedFile ?? undefined),
    onSuccess: finishUpdate,
  });
  const photoMutation = useMutation({
    mutationFn: (id: number) => uploadPlantPhoto(id, capturedFile!),
    onSuccess: finishUpdate,
  });
  const noteMutation = useMutation({
    mutationFn: (id: number) => addHistoryNote(id, "Photo observation"),
    onSuccess: finishUpdate,
  });

  function finishUpdate() {
    if (selectedPlant) {
      queryClient.invalidateQueries({ queryKey: ["plant", selectedPlant.id] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
    }
    setStep("success");
  }

  function runCareAction(action: CareAction) {
    if (!selectedPlant) return;
    const id = selectedPlant.id;
    switch (action) {
      case "water": waterMutation.mutate(id); break;
      case "fertilize": fertilizeMutation.mutate(id); break;
      case "prune": pruneMutation.mutate(id); break;
      case "photo": photoMutation.mutate(id); break;
      case "note": noteMutation.mutate(id); break;
    }
  }

  const anyPending =
    registerMutation.isPending ||
    waterMutation.isPending ||
    fertilizeMutation.isPending ||
    pruneMutation.isPending ||
    photoMutation.isPending ||
    noteMutation.isPending;

  const mutationError =
    registerMutation.error ??
    waterMutation.error ??
    fertilizeMutation.error ??
    pruneMutation.error ??
    photoMutation.error ??
    noteMutation.error;

  // ── Render steps ────────────────────────────────────────────────────────

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col bg-black"
      style={{ paddingBottom: "env(safe-area-inset-bottom, 0px)" }}
    >
      {/* ── Step: Capture ── */}
      {step === "capture" && (
        <div className="flex flex-col h-full">
          {/* Close */}
          <div className="absolute top-0 left-0 right-0 z-10 flex justify-end p-4">
            <button
              type="button"
              onClick={onClose}
              className="min-h-[44px] min-w-[44px] flex items-center justify-center text-white bg-black/40 rounded-full"
              aria-label="Close camera"
            >
              <X size={22} />
            </button>
          </div>

          {/* Video preview */}
          {!cameraError ? (
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className="flex-1 w-full object-cover"
            />
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center gap-4 px-8 text-center">
              <Camera size={40} className="text-stone-500" />
              <p className="text-stone-300 text-sm">{cameraError}</p>
            </div>
          )}

          {/* Controls row */}
          <div className="bg-black flex items-center justify-center gap-8 py-8">
            {/* File picker fallback */}
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="min-h-[44px] min-w-[44px] flex flex-col items-center gap-1 text-stone-400"
              aria-label="Choose from library"
            >
              <Upload size={22} />
              <span className="text-[10px]">Library</span>
            </button>

            {/* Shutter */}
            {!cameraError && (
              <button
                type="button"
                onClick={handleShutter}
                className="w-16 h-16 rounded-full border-4 border-white bg-white/20 active:bg-white/40 transition-colors min-h-[44px]"
                aria-label="Take photo"
              />
            )}

            <div className="w-11" />
          </div>

          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={handleFileChange}
          />
        </div>
      )}

      {/* ── Steps with white background ── */}
      {step !== "capture" && (
        <div className="flex flex-col h-full bg-white">

          {/* ── Step: Route ── */}
          {step === "route" && (
            <>
              <StepHeader
                title="What's the photo for?"
                onBack={() => setStep("capture")}
                onClose={onClose}
              />
              {/* Preview */}
              {previewUrl && (
                <div className="relative w-full aspect-[4/3] bg-stone-100">
                  <Image src={previewUrl} alt="Captured" fill className="object-cover" />
                </div>
              )}
              <div className="flex flex-col gap-3 p-6">
                <button
                  type="button"
                  onClick={() => setStep("new-plant-form")}
                  className={cn(
                    "w-full py-4 rounded-2xl border-2 border-[hsl(var(--primary))] text-[hsl(var(--primary))]",
                    "font-semibold text-base active:bg-stone-50 min-h-[44px]"
                  )}
                >
                  New Plant
                </button>
                <button
                  type="button"
                  onClick={() => setStep("update-pick-plant")}
                  className={cn(
                    "w-full py-4 rounded-2xl border-2 border-stone-200 text-stone-700",
                    "font-semibold text-base active:bg-stone-50 min-h-[44px]"
                  )}
                >
                  Update Existing Plant
                </button>
              </div>
            </>
          )}

          {/* ── Step: New plant form ── */}
          {step === "new-plant-form" && (
            <>
              <StepHeader
                title="Add New Plant"
                onBack={() => setStep("route")}
                onClose={onClose}
              />
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {/* Preview thumbnail */}
                {previewUrl && (
                  <div className="relative w-full aspect-[4/3] rounded-xl overflow-hidden bg-stone-100">
                    <Image src={previewUrl} alt="New plant" fill className="object-cover" />
                  </div>
                )}

                {/* Name (optional) */}
                <div className="space-y-1.5">
                  <label htmlFor="new-plant-name" className="text-sm font-medium text-stone-700">
                    Name <span className="text-stone-400 font-normal">(optional)</span>
                  </label>
                  <input
                    id="new-plant-name"
                    type="text"
                    value={plantName}
                    onChange={(e) => setPlantName(e.target.value)}
                    placeholder="e.g. Bathroom fern"
                    className={cn(
                      "w-full rounded-xl border border-stone-200 px-3 py-2.5 text-sm text-stone-800",
                      "placeholder:text-stone-400 outline-none min-h-[44px]",
                      "focus-visible:border-stone-400 focus-visible:ring-2 focus-visible:ring-stone-200/80"
                    )}
                  />
                </div>

                {/* Growing context */}
                <div className="space-y-1.5">
                  <span className="text-sm font-medium text-stone-700">Where does it grow?</span>
                  <div className="flex gap-3">
                    {(["INDOOR", "OUTDOOR"] as PlantGrowingContext[]).map((ctx) => (
                      <button
                        key={ctx}
                        type="button"
                        onClick={() => setGrowingContext(ctx)}
                        className={cn(
                          "flex-1 py-2.5 rounded-xl border text-sm font-medium min-h-[44px] transition-colors",
                          growingContext === ctx
                            ? "border-[hsl(var(--primary))] bg-[hsl(var(--accent))] text-[hsl(var(--accent-foreground))]"
                            : "border-stone-200 text-stone-600 active:bg-stone-50"
                        )}
                      >
                        {ctx === "INDOOR" ? "Indoor" : "Outdoor"}
                      </button>
                    ))}
                  </div>
                </div>

                <p className="text-xs text-stone-400 leading-relaxed">
                  We&apos;ll use the photo to identify your plant and generate care guidance.
                </p>

                {mutationError && (
                  <p className="text-sm text-red-600">
                    {(mutationError as Error).message ?? "Something went wrong. Please try again."}
                  </p>
                )}
              </div>

              <div className="p-4 border-t border-stone-100">
                <button
                  type="button"
                  onClick={() => registerMutation.mutate()}
                  disabled={anyPending || !capturedFile}
                  className={cn(
                    "w-full py-3.5 rounded-2xl font-semibold text-white min-h-[44px]",
                    "bg-[hsl(var(--primary))] active:opacity-90 disabled:opacity-50 transition-opacity"
                  )}
                >
                  {anyPending ? "Saving…" : "Save Plant"}
                </button>
              </div>
            </>
          )}

          {/* ── Step: Update — pick plant ── */}
          {step === "update-pick-plant" && (
            <>
              <StepHeader
                title="Which plant?"
                onBack={() => setStep("route")}
                onClose={onClose}
              />
              <div className="flex-1 overflow-y-auto">
                {activePlants.length === 0 && (
                  <p className="text-center text-stone-400 text-sm mt-8 px-4">
                    No plants yet. Add one first!
                  </p>
                )}
                {activePlants.map((plant) => {
                  const thumb =
                    plant.illustratedImage ??
                    (plant.originalImage?.mimeType?.startsWith("image/") ? plant.originalImage : null);
                  return (
                    <button
                      key={plant.id}
                      type="button"
                      onClick={() => {
                        setSelectedPlant(plant);
                        setStep("update-pick-action");
                      }}
                      className={cn(
                        "w-full flex items-center gap-3 px-4 py-3 min-h-[44px]",
                        "border-b border-stone-100 active:bg-stone-50 text-left"
                      )}
                    >
                      <div className="w-12 h-12 rounded-lg overflow-hidden flex-shrink-0 bg-stone-100">
                        {thumb ? (
                          <Image
                            src={plantImageSrc(thumb.url)}
                            alt={plant.displayLabel}
                            width={48}
                            height={48}
                            className="object-cover w-full h-full"
                            loading="lazy"
                          />
                        ) : (
                          <div className="flex h-full items-center justify-center">
                            <Sprout size={18} className="text-stone-300" />
                          </div>
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-stone-800 truncate text-sm">
                          {plant.displayLabel}
                        </p>
                        {plant.speciesLabel && plant.name && (
                          <p className="text-xs text-stone-400 italic truncate species-label">
                            {plant.speciesLabel}
                          </p>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            </>
          )}

          {/* ── Step: Update — pick action ── */}
          {step === "update-pick-action" && selectedPlant && (
            <>
              <StepHeader
                title={selectedPlant.name ?? selectedPlant.speciesLabel ?? "Update Plant"}
                onBack={() => setStep("update-pick-plant")}
                onClose={onClose}
              />
              {/* Preview */}
              {previewUrl && (
                <div className="relative w-full aspect-[4/3] bg-stone-100">
                  <Image src={previewUrl} alt="Captured" fill className="object-cover" />
                </div>
              )}
              <div className="flex-1 overflow-y-auto">
                <p className="text-xs text-stone-400 px-4 pt-3 pb-1">Log a care action with this photo</p>

                {mutationError && (
                  <p className="text-sm text-red-600 px-4 pb-2">
                    {(mutationError as Error).message ?? "Something went wrong."}
                  </p>
                )}

                {(
                  [
                    { action: "water" as CareAction, label: "Watered", icon: <Droplets size={20} className="text-[hsl(var(--watering))]" /> },
                    { action: "fertilize" as CareAction, label: "Fertilized", icon: <Leaf size={20} className="text-[hsl(var(--fertilizer))]" /> },
                    { action: "prune" as CareAction, label: "Pruned (with photo)", icon: <Scissors size={20} className="text-[hsl(var(--pruning))]" /> },
                    { action: "photo" as CareAction, label: "Add Photo Update", icon: <Camera size={20} className="text-stone-500" /> },
                    { action: "note" as CareAction, label: "Photo Observation", icon: <Sprout size={20} className="text-stone-400" /> },
                  ]
                ).map(({ action, label, icon }) => (
                  <button
                    key={action}
                    type="button"
                    onClick={() => runCareAction(action)}
                    disabled={anyPending}
                    className={cn(
                      "w-full flex items-center gap-4 px-4 py-4 min-h-[44px]",
                      "border-b border-stone-100 active:bg-stone-50 text-left disabled:opacity-50"
                    )}
                  >
                    <span className="flex-shrink-0">{icon}</span>
                    <span className="font-medium text-stone-700 text-sm">{label}</span>
                  </button>
                ))}
              </div>
            </>
          )}

          {/* ── Step: Success ── */}
          {step === "success" && (
            <>
              <StepHeader title="" onClose={onClose} />
              <div className="flex-1 flex flex-col items-center justify-center gap-5 px-8 text-center">
                <div className="w-16 h-16 rounded-full bg-[hsl(var(--accent))] flex items-center justify-center">
                  <Check size={32} className="text-[hsl(var(--primary))]" />
                </div>
                <div>
                  <h2 className="text-xl font-semibold text-stone-800 mb-1">All done!</h2>
                  <p className="text-sm text-stone-400">
                    {registerMutation.isSuccess
                      ? "Your plant is being identified. Check back shortly for care guidance."
                      : "Care action logged successfully."}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={onComplete}
                  className={cn(
                    "w-full max-w-xs py-3.5 rounded-2xl font-semibold text-white min-h-[44px]",
                    "bg-[hsl(var(--primary))] active:opacity-90 transition-opacity"
                  )}
                >
                  Back to Plants
                </button>
              </div>
            </>
          )}

        </div>
      )}
    </div>
  );
}
