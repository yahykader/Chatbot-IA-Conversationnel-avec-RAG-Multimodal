// src/polyfills.ts
import { Buffer } from 'buffer';

// Polyfill global pour SockJS
(window as any).global = window;
(window as any).process = {
  env: { DEBUG: undefined },
  version: '',
  nextTick: (fn: Function) => setTimeout(fn, 0)
};
(window as any).Buffer = Buffer;

// ✅ Supprimer warnings extension Chrome
const originalConsoleWarn = console.warn;
const originalConsoleError = console.error;

console.warn = function(...args: any[]) {
  // Ignorer warnings extensions Chrome
  const message = args[0]?.toString() || '';
  if (
    message.includes('chrome-extension://') ||
    message.includes('web_accessible_resources') ||
    message.includes('gpc') ||
    message.includes('ERR_FAILED')
  ) {
    return;
  }
  originalConsoleWarn.apply(console, args);
};

console.error = function(...args: any[]) {
  // Ignorer erreurs extensions Chrome
  const message = args[0]?.toString() || '';
  if (
    message.includes('chrome-extension://') ||
    message.includes('runtime.lastError')
  ) {
    return;
  }
  originalConsoleError.apply(console, args);
};

console.log('✅ Polyfills loaded');