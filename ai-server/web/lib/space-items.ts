"use client";

import {
  Brain,
  ClipboardList,
  History,
  NotebookPen,
  Wand2,
  type LucideIcon,
} from "lucide-react";

export type SpaceItemKey =
  | "chat_history"
  | "notebooks"
  | "question_bank"
  | "skills"
  | "memory";

export type SpaceMemoryFile = "summary" | "profile";

export interface SpaceItem {
  key: SpaceItemKey;
  href: string;
  label: string;
  description: string;
  icon: LucideIcon;
}

export const SPACE_ITEMS: SpaceItem[] = [
  {
    key: "chat_history",
    href: "/space/chat-history",
    label: "聊天记录",
    description: "查看并重新打开之前的对话。",
    icon: History,
  },
  {
    key: "notebooks",
    href: "/space/notebooks",
    label: "笔记本",
    description:
      "整理聊天、研究、协作写作等已保存内容。",
    icon: NotebookPen,
  },
  {
    key: "question_bank",
    href: "/space/questions",
    label: "题库",
    description: "查看并整理跨会话保存的题目。",
    icon: ClipboardList,
  },
  {
    key: "skills",
    href: "/space/skills",
    label: "技能",
    description: "用于引导聊天回复行为的策略模板。",
    icon: Wand2,
  },
  {
    key: "memory",
    href: "/space/memory",
    label: "记忆",
    description: "助手在跨会话中持续携带的长期记忆。",
    icon: Brain,
  },
];
