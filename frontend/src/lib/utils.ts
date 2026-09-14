import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

/**
 * Merges Tailwind classes, resolving conflicts in favour of the last one.
 *
 * Needed because plain string concatenation loses: `"p-2" + "p-4"` emits both
 * and the winner depends on CSS source order rather than intent. twMerge makes
 * a component's own classes overridable by its caller, which is what lets a
 * shared Button accept a one-off tweak without a variant for every case.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
