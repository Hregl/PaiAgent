import '@testing-library/jest-dom';

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { store = {}; },
    get length() { return Object.keys(store).length; },
    key: (i: number) => Object.keys(store)[i] ?? null,
  };
})();
Object.defineProperty(window, 'localStorage', { value: localStorageMock });

// Mock EventSource
class MockEventSource {
  onerror: (() => void) | null = null;
  private listeners: Record<string, Array<(event: { data: string }) => void>> = {};

  addEventListener(type: string, handler: (event: { data: string }) => void) {
    (this.listeners[type] ??= []).push(handler);
  }

  removeEventListener(type: string, handler: (event: { data: string }) => void) {
    const handlers = this.listeners[type];
    if (handlers) {
      this.listeners[type] = handlers.filter((h) => h !== handler);
    }
  }

  close() {}

  _emit(type: string, data: object) {
    (this.listeners[type] ?? []).forEach((h) =>
      h({ data: JSON.stringify(data) })
    );
  }

  _emitError() {
    this.onerror?.();
  }
}
(window as unknown as Record<string, unknown>).EventSource = MockEventSource;

// Suppress window.location.href assignment errors
delete (window as unknown as Record<string, unknown>).location;
window.location = { href: '' } as Location;
