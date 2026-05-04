"use client";

import { useRef, useState } from "react";
import { Camera, Send } from "lucide-react";

export type CareObservationInputProps = {
  onAddNote: (text: string) => void;
  addNotePending: boolean;
  noteError: string | null;
  /** When omitted, the journal photo control is hidden (e.g. mobile plant sheet). */
  onAddImage?: (image: File, noteText?: string) => void;
  addImagePending?: boolean;
  imageError?: string | null;
};

export function CareObservationInput({
  onAddNote,
  addNotePending,
  noteError,
  onAddImage,
  addImagePending = false,
  imageError = null,
}: CareObservationInputProps) {
  const [noteText, setNoteText] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const MAX_CHARS = 180;

  function submitNote() {
    const trimmed = noteText.trim();
    if (!trimmed) return;
    onAddNote(trimmed);
    setNoteText("");
  }

  function handleImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (!onAddImage) return;
    const file = e.target.files?.[0];
    if (!file) return;
    const caption = noteText.trim() || undefined;
    onAddImage(file, caption);
    setNoteText("");
    e.target.value = "";
  }

  const journalError = noteError ?? imageError;

  return (
    <div className="rounded-2xl border border-stone-200 bg-white p-3 flex flex-col gap-2">
      <textarea
        value={noteText}
        onChange={(e) => setNoteText(e.target.value.slice(0, MAX_CHARS))}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            submitNote();
          }
        }}
        placeholder="Add an observation or note about your plant…"
        rows={2}
        className="w-full resize-none text-sm text-stone-700 placeholder:text-stone-300 bg-transparent outline-none leading-snug"
      />
      {journalError && (
        <p className="text-xs text-red-600 leading-snug" role="alert">
          {journalError}
        </p>
      )}
      <div className="flex items-center justify-between">
        <span
          className={`text-xs tabular-nums ${noteText.length >= MAX_CHARS ? "text-red-400" : "text-stone-300"}`}
        >
          {noteText.length}/{MAX_CHARS}
        </span>
        <div className="flex items-center gap-2">
          {onAddImage && (
            <>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp,image/heic"
                className="hidden"
                onChange={handleImageChange}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={addImagePending}
                className="p-1.5 rounded-full text-stone-400 hover:text-stone-600 hover:bg-stone-100 transition-colors disabled:opacity-40"
                title={noteText.trim() ? "Add photo with note as caption" : "Add photo"}
              >
                <Camera size={16} />
              </button>
            </>
          )}
          <button
            type="button"
            onClick={submitNote}
            disabled={!noteText.trim() || addNotePending}
            className="p-1.5 rounded-full text-green-600 hover:bg-green-50 transition-colors disabled:opacity-30"
            title="Save note"
          >
            <Send size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
