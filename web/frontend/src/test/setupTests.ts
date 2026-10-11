import '@testing-library/jest-dom/vitest';

// jsdom (30.x) does not implement <dialog>.showModal()/close() yet. The Dialog
// component deliberately uses the native API (real modality, focus trapping and
// Esc handling in browsers), so the test environment gets the minimal methods
// it needs instead of the component carrying a jsdom-only fallback.
type DialogPolyfillHost = { showModal?: () => void; close?: () => void };

const dialogPrototype = HTMLDialogElement.prototype as unknown as DialogPolyfillHost;
if (typeof dialogPrototype.showModal !== 'function') {
  dialogPrototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  dialogPrototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
}