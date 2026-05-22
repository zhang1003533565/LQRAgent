"use client";

import { useTranslation } from "react-i18next";
import type { IndexVersion } from "@/lib/knowledge-helpers";

interface IndexVersionChipProps {
  version: IndexVersion;
  activeSignature?: string | null;
}

export default function IndexVersionChip({
  version,
  activeSignature,
}: IndexVersionChipProps) {
  const { t } = useTranslation();
  const matchesActive =
    !!version.signature && version.signature === activeSignature;
  const isActive = matchesActive && version.ready === true;
  const isPhantomActive = matchesActive && version.ready !== true;

  const dimensionLabel =
    typeof version.dimension === "number"
      ? ` · ${version.dimension}${t("d")}`
      : "";
  const label = version.legacy
    ? t("Legacy")
    : version.model
      ? `${version.model}${dimensionLabel}`
      : (version.signature ?? t("Unknown"));

  const className = isActive
    ? "border-emerald-300 bg-emerald-50 text-emerald-700"
    : isPhantomActive
      ? "border-amber-300 bg-amber-50 text-amber-700 line-through decoration-amber-400/70"
      : "border-[var(--border)] text-[var(--muted-foreground)]";

  const title = isPhantomActive
    ? t(
        "Active embedding's index version exists but is empty — last re-index probably failed.",
      )
    : version.created_at
      ? `${t("Created")}: ${version.created_at}`
      : undefined;

  return (
    <span
      className={`rounded-full border px-2 py-0.5 text-[11px] ${className}`}
      title={title}
    >
      {isActive ? "★ " : ""}
      {label}
    </span>
  );
}
