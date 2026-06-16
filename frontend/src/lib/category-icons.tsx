import type { ComponentType } from "react";
import {
  Apple,
  Baby,
  Beef,
  Camera,
  CircleHelp,
  Cookie,
  CupSoda,
  Fish,
  Fuel,
  Hammer,
  HeartPulse,
  Home,
  Image,
  Milk,
  Package,
  PawPrint,
  Receipt,
  Salad,
  ShoppingBasket,
  Sparkles,
  Tag,
  Ticket,
  Utensils,
  Wheat
} from "lucide-react";

import { cn } from "@/lib/utils";

type IconComponent = ComponentType<{ className?: string }>;

const iconComponents: Record<string, IconComponent> = {
  apple: Apple,
  baby: Baby,
  beef: Beef,
  camera: Camera,
  "circle-help": CircleHelp,
  cookie: Cookie,
  "cup-soda": CupSoda,
  fish: Fish,
  fuel: Fuel,
  hammer: Hammer,
  "heart-pulse": HeartPulse,
  home: Home,
  image: Image,
  milk: Milk,
  package: Package,
  "paw-print": PawPrint,
  receipt: Receipt,
  salad: Salad,
  "shopping-basket": ShoppingBasket,
  sparkles: Sparkles,
  tag: Tag,
  ticket: Ticket,
  utensils: Utensils,
  wheat: Wheat
};

export function CategoryIcon({
  className,
  icon
}: {
  className?: string;
  icon: string | null | undefined;
}) {
  const Icon = icon ? iconComponents[icon] ?? Tag : Tag;
  return <Icon className={cn("h-4 w-4", className)} />;
}

export function isKnownCategoryIcon(icon: string | null | undefined): boolean {
  return Boolean(icon && iconComponents[icon]);
}
