import { useMemo } from 'react';
import {
  LineChart, Line, XAxis, YAxis, Tooltip, Legend, Brush,
  ResponsiveContainer, CartesianGrid,
} from 'recharts';
import { formatTimestamp } from '../data/timestampCalculator';
import type { ChartProps } from './chartTypes';
import { SERIES_COLORS } from './chartTypes';
import { downsampleForChart } from './chartUtils';

export function RechartsChart({ rows, headers, dimension, maxPoints }: ChartProps) {
  const data = useMemo(() => {
    const points = downsampleForChart(rows, headers, maxPoints);
    return points.map(p => {
      const entry: Record<string, unknown> = {
        ts: p.timestampMs,
        label: formatTimestamp(p.timestampMs, dimension),
      };
      for (let s = 0; s < headers.length; s++) {
        entry[`s${s}`] = p.values[s];
      }
      return entry;
    });
  }, [rows, headers, dimension]);

  return (
    <ResponsiveContainer width="100%" height={400}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
        <XAxis
          dataKey="label"
          tick={{ fontSize: 11 }}
          interval="preserveStartEnd"
        />
        <YAxis tick={{ fontSize: 11 }} />
        <Tooltip />
        <Legend />
        {headers.map((h, i) => (
          <Line
            key={h.tsId}
            type="monotone"
            dataKey={`s${i}`}
            name={h.tsKey}
            stroke={SERIES_COLORS[i % SERIES_COLORS.length]}
            dot={false}
            strokeWidth={1.5}
            connectNulls={false}
            isAnimationActive={false}
          />
        ))}
        <Brush
          dataKey="label"
          height={30}
          stroke="var(--color-primary)"
          fill="var(--color-surface)"
          tickFormatter={() => ''}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
