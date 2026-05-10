import React, { useEffect, useRef } from 'react';
import usePosStore from '../store';

export default function Divider({ theme }) {
  const setCartWidth = usePosStore((s) => s.setCartWidth);
  const dragging = useRef(false);

  useEffect(() => {
    const onMove = (e) => {
      if (!dragging.current) return;
      const w = window.innerWidth - e.clientX;
      setCartWidth(w);
    };
    const onUp = () => {
      if (dragging.current) {
        dragging.current = false;
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      }
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, [setCartWidth]);

  return (
    <div
      onMouseDown={(e) => {
        e.preventDefault();
        dragging.current = true;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
      }}
      style={{
        width: 4,
        flexShrink: 0,
        cursor: 'col-resize',
        background: 'transparent',
        position: 'relative',
        zIndex: 5,
      }}
      title="Drag to resize"
    >
      <div
        style={{
          position: 'absolute',
          left: 1,
          top: 0,
          bottom: 0,
          width: 2,
          background: theme.border,
        }}
      />
    </div>
  );
}
