"use client";

import { useTranslation } from "react-i18next";
import { AlertTriangle, CheckCircle2, Clock3 } from "lucide-react";
import {
  kbHasLiveProgress,
  kbNeedsReindex,
  resolveKbStatus,
  type KnowledgeBase,
} from "@/lib/knowledge-helpers";

interface KbStatusBadgeProps {
  kb: KnowledgeBase;
  isReindexingLocally?: boolean;
}

export default function KbStatusBadge({
  kb,
  isReindexingLocally = false,
}: KbStatusBadgeProps) {
  const { t } = useTranslation();
  const status = resolveKbStatus(kb);
  const needsReindex = kbNeedsReindex(kb);
  const isLive = kbHasLiveProgress(kb) || isReindexingLocally;
  const isError = status === "error";
  const isReady = status === "ready" && !needsReindex;

  const tone = needsReindex
    ? "bg-amber-100 text-amber-700"
    : isError
      ? "bg-red-100 text-red-700"
      : isLive
        ? "bg-sky-100 text-sky-700"
        : "bg-emerald-100 text-emerald-700";

  const Icon = isLive ? Clock3 : isReady ? CheckCircle2 : AlertTriangle;

  const label = needsReindex
    ? t("Needs reindex")
    : isError
      ? t("Error")
      : isLive
        ? t("Processing live")
        : isReady
          ? t("Ready")
          : status.replaceAll("_", " ");

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${tone}`}
    >
      <Icon className="h-3 w-3" />
      {label}
    </span>
  );
}
