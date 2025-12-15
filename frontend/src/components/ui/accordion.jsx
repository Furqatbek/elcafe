import * as React from "react";
import { ChevronDown } from "lucide-react";

const AccordionContext = React.createContext({});

const Accordion = React.forwardRef(({ children, type = "single", collapsible = false, defaultValue, value, onValueChange, className, ...props }, ref) => {
  const [internalValue, setInternalValue] = React.useState(defaultValue || (type === "multiple" ? [] : ""));

  const currentValue = value !== undefined ? value : internalValue;

  const handleValueChange = React.useCallback((itemValue) => {
    let newValue;

    if (type === "multiple") {
      newValue = currentValue.includes(itemValue)
        ? currentValue.filter(v => v !== itemValue)
        : [...currentValue, itemValue];
    } else {
      newValue = currentValue === itemValue && collapsible ? "" : itemValue;
    }

    if (value === undefined) {
      setInternalValue(newValue);
    }

    onValueChange?.(newValue);
  }, [currentValue, type, collapsible, value, onValueChange]);

  const isItemOpen = React.useCallback((itemValue) => {
    if (type === "multiple") {
      return currentValue.includes(itemValue);
    }
    return currentValue === itemValue;
  }, [currentValue, type]);

  return (
    <AccordionContext.Provider value={{ isItemOpen, handleValueChange }}>
      <div ref={ref} className={className} {...props}>
        {children}
      </div>
    </AccordionContext.Provider>
  );
});
Accordion.displayName = "Accordion";

const AccordionItem = React.forwardRef(({ children, value, className, ...props }, ref) => {
  return (
    <div ref={ref} className={className} data-value={value} {...props}>
      {children}
    </div>
  );
});
AccordionItem.displayName = "AccordionItem";

const AccordionTrigger = React.forwardRef(({ children, className, ...props }, ref) => {
  const { isItemOpen, handleValueChange } = React.useContext(AccordionContext);
  const itemElement = React.useRef(null);

  React.useEffect(() => {
    itemElement.current = ref?.current || null;
    if (!itemElement.current) {
      itemElement.current = document.activeElement?.closest('[data-value]');
    }
  }, [ref]);

  const value = itemElement.current?.closest('[data-value]')?.getAttribute('data-value');
  const isOpen = value ? isItemOpen(value) : false;

  const handleClick = () => {
    const element = ref?.current || document.activeElement;
    const itemValue = element?.closest('[data-value]')?.getAttribute('data-value');
    if (itemValue) {
      handleValueChange(itemValue);
    }
  };

  return (
    <button
      ref={ref}
      type="button"
      className={`flex w-full items-center justify-between py-4 font-medium transition-all ${className || ''}`}
      onClick={handleClick}
      {...props}
    >
      {children}
      <ChevronDown
        className={`h-4 w-4 shrink-0 transition-transform duration-200 ${
          isOpen ? 'rotate-180' : ''
        }`}
      />
    </button>
  );
});
AccordionTrigger.displayName = "AccordionTrigger";

const AccordionContent = React.forwardRef(({ children, className, ...props }, ref) => {
  const { isItemOpen } = React.useContext(AccordionContext);
  const [value, setValue] = React.useState(null);

  React.useEffect(() => {
    const element = ref?.current;
    if (element) {
      const itemValue = element.closest('[data-value]')?.getAttribute('data-value');
      setValue(itemValue);
    }
  }, [ref]);

  const isOpen = value ? isItemOpen(value) : false;

  return (
    <div
      ref={ref}
      className={`overflow-hidden transition-all duration-200 ${
        isOpen ? 'max-h-[5000px] opacity-100' : 'max-h-0 opacity-0'
      } ${className || ''}`}
      {...props}
    >
      <div className="pb-4">{children}</div>
    </div>
  );
});
AccordionContent.displayName = "AccordionContent";

export { Accordion, AccordionItem, AccordionTrigger, AccordionContent };
