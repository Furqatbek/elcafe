import * as React from "react"
import { cn } from "@/lib/utils"

const Checkbox = React.forwardRef(({ className, checked, onCheckedChange, ...props }, ref) => (
  <input
    type="checkbox"
    ref={ref}
    checked={checked}
    onChange={(e) => onCheckedChange?.(e.target.checked)}
    className={cn(
      "h-4 w-4 shrink-0 rounded-sm border border-primary accent-primary focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
      className
    )}
    {...props}
  />
))
Checkbox.displayName = "Checkbox"

export { Checkbox }
