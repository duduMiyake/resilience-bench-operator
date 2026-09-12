import { displayNumber, parameterLabel, successPercent } from './display';

describe('Readable display values', () => {
  it('removes technical prefixes', () => {
    expect(parameterLabel('source_env_GRPC_BACKOFF_MULTIPLIER')).toBe('Backoff multiplier');
    expect(parameterLabel('checkout.destination_env_GRPC_MAX_ATTEMPTS')).toBe('Max attempts');
    expect(parameterLabel('retryTimeout')).toBe('Retry timeout');
  });
  it('rounds display values and handles missing values', () => {
    expect(successPercent(0.75566667)).toBe('75.57%');
    expect(successPercent(0)).toBe('0.00%');
    expect(successPercent(undefined)).toBe('—');
    expect(displayNumber(12345.678)).toBe('12,345.68');
    expect(displayNumber(NaN)).toBe('—');
  });
});
