"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Eye, EyeOff, CheckCircle2 } from "lucide-react";
import Link from "next/link";
import { getUserLocation, putUserLocation, getUserSettings, putUserSettings } from "@/lib/api";
import { cn } from "@/lib/utils";

// ── Shared field helpers ──────────────────────────────────────────────────────

function SectionCard({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-xl border border-stone-200 bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-stone-100">
        <h2 className="text-sm font-semibold text-stone-800">{title}</h2>
        {description && (
          <p className="text-xs text-stone-400 mt-0.5 leading-relaxed">{description}</p>
        )}
      </div>
      <div className="px-5 py-4 space-y-4">{children}</div>
    </section>
  );
}

function FieldLabel({ htmlFor, children }: { htmlFor: string; children: React.ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="block text-sm font-medium text-stone-700 mb-1">
      {children}
    </label>
  );
}

function SavedFlash({ visible }: { visible: boolean }) {
  if (!visible) return null;
  return (
    <span className="inline-flex items-center gap-1 text-xs text-stone-500" role="status">
      <CheckCircle2 size={12} className="text-green-600" />
      Saved
    </span>
  );
}

function ErrorMsg({ message }: { message: string | null }) {
  if (!message) return null;
  return <p className="text-xs text-amber-700">{message}</p>;
}

// ── Display name field ────────────────────────────────────────────────────────

function DisplayNameField() {
  const queryClient = useQueryClient();
  const { data: settings, isLoading } = useQuery({
    queryKey: ["userSettings"],
    queryFn: getUserSettings,
    staleTime: 60_000,
  });

  const [draft, setDraft] = useState<string | null>(null);
  const [savedFlash, setSavedFlash] = useState(false);
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (flashTimer.current) clearTimeout(flashTimer.current); }, []);

  const effective = draft ?? (settings?.displayName ?? "");

  const mutation = useMutation({
    mutationFn: (displayName: string) =>
      putUserSettings({ displayName }),
    onSuccess: (res) => {
      queryClient.setQueryData(["userSettings"], res);
      if (flashTimer.current) clearTimeout(flashTimer.current);
      setSavedFlash(true);
      flashTimer.current = setTimeout(() => setSavedFlash(false), 2000);
    },
  });

  const handleBlur = () => {
    const trimmed = effective.trim();
    const saved = (settings?.displayName ?? "").trim();
    if (trimmed === saved) return;
    // Send "" to clear (backend: "" = clear, null = leave unchanged)
    mutation.mutate(trimmed);
  };

  return (
    <div className="space-y-1">
      <FieldLabel htmlFor="display-name">Display name</FieldLabel>
      {isLoading ? (
        <div className="h-8 bg-stone-100 rounded-lg animate-pulse w-full" />
      ) : (
        <input
          id="display-name"
          type="text"
          placeholder="e.g. Alex"
          value={effective}
          onChange={(e) => setDraft(e.target.value)}
          onBlur={handleBlur}
          disabled={mutation.isPending}
          maxLength={100}
          className={cn(
            "w-full rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-sm text-stone-800",
            "placeholder:text-stone-400 outline-none transition-colors",
            "focus-visible:border-stone-400 focus-visible:ring-2 focus-visible:ring-stone-200/80",
            "disabled:opacity-60"
          )}
        />
      )}
      <div className="flex items-center gap-2 min-h-[18px]">
        <p className="text-xs text-stone-400">
          How you want to be referred to in the app.
        </p>
        <SavedFlash visible={savedFlash} />
        <ErrorMsg message={mutation.isError ? "Could not save. Try again." : null} />
      </div>
    </div>
  );
}

// ── Location field ────────────────────────────────────────────────────────────

function LocationField() {
  const queryClient = useQueryClient();
  const {
    data: locationData,
    isLoading: locationLoading,
    error: locationError,
  } = useQuery({
    queryKey: ["userLocation"],
    queryFn: getUserLocation,
    staleTime: 60_000,
  });

  const [locationDraft, setLocationDraft] = useState<string | null>(null);
  const [savedFlash, setSavedFlash] = useState(false);
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (flashTimer.current) clearTimeout(flashTimer.current); }, []);

  const effectiveDraft = locationDraft ?? (locationData?.address ?? "");

  const mutation = useMutation({
    mutationFn: putUserLocation,
    onSuccess: (res) => {
      queryClient.setQueryData(["userLocation"], res);
      if (flashTimer.current) clearTimeout(flashTimer.current);
      setSavedFlash(true);
      flashTimer.current = setTimeout(() => setSavedFlash(false), 2000);
    },
  });

  const handleBlur = () => {
    const trimmed = effectiveDraft.trim();
    const saved = (locationData?.address ?? "").trim();
    if (trimmed === saved) return;
    mutation.mutate({ address: trimmed.length > 0 ? trimmed : null });
  };

  return (
    <div className="space-y-1">
      <FieldLabel htmlFor="user-location">Your location</FieldLabel>
      {locationLoading ? (
        <div className="h-16 bg-stone-100 rounded-lg animate-pulse w-full" />
      ) : (
        <textarea
          id="user-location"
          rows={2}
          placeholder="e.g. 123 Oak St, Portland, OR"
          value={effectiveDraft}
          onChange={(e) => setLocationDraft(e.target.value)}
          onBlur={handleBlur}
          disabled={mutation.isPending}
          className={cn(
            "w-full resize-y rounded-xl border border-stone-200 bg-white px-3 py-2 text-sm text-stone-800",
            "placeholder:text-stone-400 outline-none transition-colors",
            "focus-visible:border-stone-400 focus-visible:ring-2 focus-visible:ring-stone-200/80",
            "disabled:opacity-60"
          )}
        />
      )}
      <p className="text-xs text-stone-400 leading-relaxed">
        Optional. Sent with care and history prompts so advice can reflect your typical climate and
        seasons. Leave blank to omit.
      </p>
      <div className="flex items-center gap-2 min-h-[18px]">
        <SavedFlash visible={savedFlash} />
        <ErrorMsg
          message={
            locationError
              ? "Could not load saved location."
              : mutation.isError
              ? "Could not save location. Check your connection and try again."
              : null
          }
        />
      </div>
    </div>
  );
}

// ── OpenAI API key field ──────────────────────────────────────────────────────

function ApiKeyField() {
  const queryClient = useQueryClient();
  const { data: settings, isLoading } = useQuery({
    queryKey: ["userSettings"],
    queryFn: getUserSettings,
    staleTime: 60_000,
  });

  const [keyDraft, setKeyDraft] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [savedFlash, setSavedFlash] = useState(false);
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (flashTimer.current) clearTimeout(flashTimer.current); }, []);

  const isConfigured = settings?.apiKeyConfigured ?? false;

  const mutation = useMutation({
    mutationFn: (openAiApiKeyOverride: string | null) =>
      putUserSettings({ openAiApiKeyOverride }),
    onSuccess: (res) => {
      queryClient.setQueryData(["userSettings"], res);
      setKeyDraft("");
      if (flashTimer.current) clearTimeout(flashTimer.current);
      setSavedFlash(true);
      flashTimer.current = setTimeout(() => setSavedFlash(false), 2000);
    },
  });

  const handleSave = () => {
    const trimmed = keyDraft.trim();
    if (!trimmed) return;
    mutation.mutate(trimmed);
  };

  const handleClear = () => {
    mutation.mutate("");
  };

  return (
    <div className="space-y-2">
      <FieldLabel htmlFor="openai-key">OpenAI API key</FieldLabel>
      {isLoading ? (
        <div className="h-8 bg-stone-100 rounded-lg animate-pulse w-full" />
      ) : (
        <>
          {isConfigured && keyDraft === "" && (
            <div className="flex items-center gap-2 mb-2">
              <span className="inline-flex items-center gap-1.5 text-xs font-medium text-green-700 bg-green-50 border border-green-200 rounded-full px-2.5 py-0.5">
                <CheckCircle2 size={11} />
                Key configured
              </span>
              <button
                type="button"
                onClick={handleClear}
                disabled={mutation.isPending}
                className="text-xs text-stone-400 hover:text-stone-600 transition-colors disabled:opacity-60"
              >
                Clear
              </button>
            </div>
          )}

          <div className="flex gap-2">
            <div className="relative flex-1">
              <input
                id="openai-key"
                type={showKey ? "text" : "password"}
                placeholder={isConfigured ? "Enter a new key to replace" : "sk-…"}
                value={keyDraft}
                onChange={(e) => setKeyDraft(e.target.value)}
                disabled={mutation.isPending}
                autoComplete="off"
                className={cn(
                  "w-full rounded-lg border border-stone-200 bg-white px-3 py-1.5 pr-9 text-sm text-stone-800",
                  "placeholder:text-stone-400 outline-none transition-colors font-mono",
                  "focus-visible:border-stone-400 focus-visible:ring-2 focus-visible:ring-stone-200/80",
                  "disabled:opacity-60"
                )}
              />
              <button
                type="button"
                onClick={() => setShowKey((v) => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-stone-400 hover:text-stone-600 transition-colors"
                aria-label={showKey ? "Hide key" : "Show key"}
              >
                {showKey ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
            </div>
            <button
              type="button"
              onClick={handleSave}
              disabled={mutation.isPending || !keyDraft.trim()}
              className={cn(
                "px-3 py-1.5 rounded-lg text-sm font-medium transition-colors",
                "bg-stone-800 text-white hover:bg-stone-700",
                "disabled:opacity-40 disabled:cursor-not-allowed"
              )}
            >
              {mutation.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </>
      )}
      <p className="text-xs text-stone-400 leading-relaxed">
        Used for all AI plant analysis and illustration. Set{" "}
        <code className="font-mono">OPENAI_API_KEY</code> in the environment as an alternative.
        The key is stored on the server and never returned to the browser.
      </p>
      <div className="min-h-[18px]">
        <SavedFlash visible={savedFlash} />
        <ErrorMsg message={mutation.isError ? "Could not save. Check your connection." : null} />
      </div>
    </div>
  );
}

// ── Screensaver duration field ────────────────────────────────────────────────

const MIN_DURATION = 5;
const MAX_DURATION = 300;

function ScreensaverDurationField() {
  const queryClient = useQueryClient();
  const { data: settings, isLoading } = useQuery({
    queryKey: ["userSettings"],
    queryFn: getUserSettings,
    staleTime: 60_000,
  });

  const [draft, setDraft] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [savedFlash, setSavedFlash] = useState(false);
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (flashTimer.current) clearTimeout(flashTimer.current); }, []);

  const serverValue = settings?.screensaverSlideDurationSeconds ?? 60;
  const effective = draft ?? String(serverValue);

  const mutation = useMutation({
    mutationFn: (seconds: number) =>
      putUserSettings({ screensaverSlideDurationSeconds: seconds }),
    onSuccess: (res) => {
      queryClient.setQueryData(["userSettings"], res);
      if (flashTimer.current) clearTimeout(flashTimer.current);
      setSavedFlash(true);
      flashTimer.current = setTimeout(() => setSavedFlash(false), 2000);
    },
  });

  const handleBlur = () => {
    setValidationError(null);
    const parsed = parseInt(effective, 10);
    if (isNaN(parsed)) {
      setValidationError("Please enter a number.");
      return;
    }
    if (parsed < MIN_DURATION || parsed > MAX_DURATION) {
      setValidationError(`Must be between ${MIN_DURATION} and ${MAX_DURATION} seconds.`);
      return;
    }
    if (parsed === serverValue) return;
    mutation.mutate(parsed);
  };

  return (
    <div className="space-y-1">
      <FieldLabel htmlFor="screensaver-duration">Screensaver slide duration</FieldLabel>
      {isLoading ? (
        <div className="h-8 bg-stone-100 rounded-lg animate-pulse w-24" />
      ) : (
        <div className="flex items-center gap-2">
          <input
            id="screensaver-duration"
            type="number"
            min={MIN_DURATION}
            max={MAX_DURATION}
            value={effective}
            onChange={(e) => { setDraft(e.target.value); setValidationError(null); }}
            onBlur={handleBlur}
            disabled={mutation.isPending}
            className={cn(
              "w-24 rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-sm text-stone-800",
              "outline-none transition-colors",
              "focus-visible:border-stone-400 focus-visible:ring-2 focus-visible:ring-stone-200/80",
              "disabled:opacity-60"
            )}
          />
          <span className="text-sm text-stone-500">seconds</span>
        </div>
      )}
      <p className="text-xs text-stone-400">
        How long each plant is shown before advancing. ({MIN_DURATION}–{MAX_DURATION}s)
      </p>
      <div className="min-h-[18px]">
        <SavedFlash visible={savedFlash} />
        <ErrorMsg message={validationError ?? (mutation.isError ? "Could not save." : null)} />
      </div>
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function SettingsPage() {
  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      {/* Header */}
      <div className="flex items-center gap-3 mb-8">
        <Link
          href="/plants"
          className="text-stone-400 hover:text-stone-600 transition-colors"
          aria-label="Back to plants"
        >
          <ArrowLeft size={18} />
        </Link>
        <div>
          <h1 className="text-2xl font-semibold text-stone-800 tracking-tight">Settings</h1>
          <p className="text-sm text-stone-400 mt-0.5">Manage your preferences and configuration</p>
        </div>
      </div>

      <div className="space-y-5">
        {/* Profile */}
        <SectionCard
          title="Profile"
          description="Your name as shown in the app."
        >
          <DisplayNameField />
        </SectionCard>

        {/* Location */}
        <SectionCard
          title="Location"
          description="Used for climate-aware care advice across all your plants."
        >
          <LocationField />
        </SectionCard>

        {/* App config */}
        <SectionCard
          title="App configuration"
          description="API keys and screensaver behavior."
        >
          <ApiKeyField />
          <hr className="border-stone-100" />
          <ScreensaverDurationField />
        </SectionCard>
      </div>
    </main>
  );
}
