import { useState, type ReactNode } from "react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/shared/ui/primitives/tooltip";
import { components, type GroupBase, type InputProps } from "react-select";

export type InputReview = { reason: string; onConfirm: () => void };

export function ReviewSelectInput<
  Option,
  Multi extends boolean,
  Group extends GroupBase<Option>,
>(props: InputProps<Option, Multi, Group>) {
  const description = (
    props.selectProps as typeof props.selectProps & {
      "aria-describedby"?: string;
    }
  )["aria-describedby"];
  return (
    <components.Input
      {...props}
      aria-describedby={
        [props["aria-describedby"], description].filter(Boolean).join(" ") ||
        undefined
      }
    />
  );
}

export function ReviewBadge() {
  return (
    <span
      aria-hidden="true"
      className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold text-amber-900"
    >
      확인 필요
    </span>
  );
}

export function ReviewField({
  id,
  review,
  disabled,
  className,
  children,
}: {
  id: string;
  review?: InputReview;
  disabled?: boolean;
  className?: string;
  children: ReactNode;
}) {
  // Keep the reason visible for this focus session after clearing the draft mark.
  const [reason, setReason] = useState<string>();
  const [open, setOpen] = useState(false);
  return (
    <TooltipProvider>
      <Tooltip open={open} onOpenChange={() => {}}>
        <TooltipTrigger asChild>
          <div
            className={className}
            onFocusCapture={(event) => {
              if (disabled || event.currentTarget.contains(event.relatedTarget))
                return;
              const input = event.currentTarget.querySelector(
                "input, select, textarea",
              );
              if (input && event.target !== input) return;
              if (review) {
                setReason(review.reason);
                setOpen(true);
                review.onConfirm();
              }
            }}
            onBlurCapture={(event) => {
              if (!event.currentTarget.contains(event.relatedTarget)) {
                setOpen(false);
                setReason(undefined);
              }
            }}
          >
            {children}
            <span id={id} className="sr-only">
              {reason ?? review?.reason}
            </span>
          </div>
        </TooltipTrigger>
        {reason ? (
          <TooltipContent
            onEscapeKeyDown={() => setOpen(false)}
            side="top"
            sideOffset={6}
            className="pointer-events-none z-[1500] max-w-64 border border-amber-200 bg-amber-50 text-amber-950 shadow-sm"
          >
            {reason}
          </TooltipContent>
        ) : null}
      </Tooltip>
    </TooltipProvider>
  );
}
