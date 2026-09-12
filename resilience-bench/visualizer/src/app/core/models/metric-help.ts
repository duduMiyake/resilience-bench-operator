export const metricHelp = {
  absoluteGap: 'Best reference score minus the best score found by this run, in score points. Example: 0.80 − 0.75 = 0.05. Zero means equal scores; a positive gap means the run is below the reference. Available only with a compatible reference.',
  relativeGap: 'Absolute gap divided by the best reference score, multiplied by 100. Example: 0.05 / 0.80 × 100 = 6.25%. Unavailable when the reference score is zero or negative.',
  observedScore: 'Performance measured after evaluating a configuration across its required contexts, using the objective recorded in the trace. Higher is better. This is not a prediction or selection priority.',
  bestSoFar: 'Highest observed score among configurations evaluated up to this decision. It stays unchanged when a later configuration performs worse.',
  evaluations: 'Number of resilience configurations with an observed score. Each configuration may require several scenarios across workload and fault contexts. Cached results also count as evaluations.',
  reference: 'Results from an exhaustive search used as a comparison baseline. Quantitative score comparisons require compatible objectives and operational contexts.',
  bestFoundAt: 'First decision at which this run found its final best observed score. This is an evaluation number, not elapsed time.',
  convergence: 'How the best observed score evolves as more configurations are evaluated. Earlier improvements indicate better results with fewer evaluations; they do not necessarily mean less execution time.',
  pareto: 'Configurations for which no other reference point has both at least as much success and at most as much response time, with one strictly better. Only meaningful within the same operational context.',
  p95: '95th percentile of iteration duration: approximately 95% of recorded iterations finished within this duration. Lower is better. It is not the average duration.',
  context: 'The fixed workload and fault conditions under which a configuration is tested. Context filters change scenario metrics, not the configuration score aggregated over required contexts.',
};
