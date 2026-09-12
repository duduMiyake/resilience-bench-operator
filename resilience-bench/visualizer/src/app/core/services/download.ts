export function downloadJson(filename: string, value: unknown): void {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }));
  downloadUrl(filename, url);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function downloadUrl(filename: string, url: string): void {
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
}
