import { Bike, Zap } from "lucide-react";
import type { BikeId } from "@/game/model";
import { cn } from "@/lib/cn";

const skin: Record<BikeId, string> = {
  classic: "bg-primary text-on-primary",
  neon: "bg-primary text-on-primary ring-4 ring-tertiary",
  gold: "bg-tertiary text-on-tertiary",
  midnight: "bg-fg text-bg ring-2 ring-outline",
  cherry: "bg-danger text-on-danger",
  arctic: "bg-secondary-container text-on-secondary-container",
};

export function BikeMark({ bike, className }: { bike: BikeId; className?: string }) {
  return (
    <div className={cn("relative grid h-12 w-12 place-items-center rounded-2xl", skin[bike], className)}>
      <Bike className="h-6 w-6" strokeWidth={2.25} />
      <span className="absolute -top-1 -right-1 grid h-5 w-5 place-items-center rounded-full bg-tertiary text-on-tertiary">
        <Zap className="h-3 w-3" strokeWidth={2.5} />
      </span>
    </div>
  );
}
