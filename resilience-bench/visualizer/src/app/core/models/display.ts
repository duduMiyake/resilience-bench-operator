export function parameterLabel(path: string): string {
  const name = (path.split('.').pop() ?? path)
    .replace(/^(source|destination)_env_/i, '').replace(/^GRPC_/i, '')
    .replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').toLowerCase();
  return name.charAt(0).toUpperCase() + name.slice(1);
}

export function successPercent(ratio: number | undefined): string {
  return ratio === undefined || !Number.isFinite(ratio) ? '—' : `${(ratio * 100).toFixed(2)}%`;
}

export function displayNumber(value: number | undefined, digits = 2): string {
  return value === undefined || !Number.isFinite(value) ? '—' : new Intl.NumberFormat('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value);
}
