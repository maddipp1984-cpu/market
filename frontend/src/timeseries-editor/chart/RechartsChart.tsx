import { useMemo } from 'react';
import {
  LineChart, Line, XAxis, YAxis, Tooltip, Legend, Brush,
  ResponsiveContainer, CartesianGrid,
} from 'recharts';
import { formatTimestamp } from '../data/timestampCalculator';
import type { ChartProps } from './chartTypes';
import { SERIES_COLORS } from './chartTypes';

export function RechartsChart({ rows, headers, dimension }: ChartProps) {
  const data = useMemo(() => {
    // Downsample if too many points
    const maxPoints = 5000;
    const step = rows.length > maxPoints ? Math.ceil(rows.length / maxPoints) : 1;
    const result: Record<string, unknown>[] = [];
    for (let i = 0; i < rows.length; i += step) {
      const r = rows[i];
      const entry: Record<string, unknown> = {
        ts: r.timestampMs,
        label: formatTimestamp(r.timestampMs, dimension),
      };
      for (let s = 0; s < headers.length; s++) {
        const v = r.values[s];
        entry[`s${s}`] = isNaN(v) ? null : v;
      }
      result.push(entry);
    }
    return result;
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
