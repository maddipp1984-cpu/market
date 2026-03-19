import { useMemo, useRef, useEffect } from 'react';
import {
  Chart as ChartJS,
  LineElement, PointElement, LinearScale,
  CategoryScale, Tooltip, Legend, Filler,
  type ChartOptions,
} from 'chart.js';
import zoomPlugin from 'chartjs-plugin-zoom';
import { Line } from 'react-chartjs-2';
import { formatTimestamp } from '../data/timestampCalculator';
import type { ChartProps } from './chartTypes';
import { SERIES_COLORS } from './chartTypes';
import { downsampleForChart } from './chartUtils';

ChartJS.register(
  LineElement, PointElement, LinearScale, CategoryScale,
  Tooltip, Legend, Filler, zoomPlugin,
);

export function ChartJsChart({ rows, headers, dimension }: ChartProps) {
  const chartRef = useRef<ChartJS<'line'>>(null);

  // Reset zoom when data changes
  useEffect(() => {
    chartRef.current?.resetZoom();
  }, [rows]);

  const { data, options } = useMemo(() => {
    const points = downsampleForChart(rows, headers);

    const labels = points.map(p => formatTimestamp(p.timestampMs, dimension));
    const datasets = headers.map((h, i) => ({
      label: h.tsKey,
      data: points.map(p => p.values[i]),
      borderColor: SERIES_COLORS[i % SERIES_COLORS.length],
      backgroundColor: SERIES_COLORS[i % SERIES_COLORS.length],
      borderWidth: 1.5,
      pointRadius: 0,
      tension: 0,
      spanGaps: false,
    }));

    const opts: ChartOptions<'line'> = {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },
      scales: {
        x: {
          ticks: { font: { size: 11 }, maxTicksLimit: 15 },
          grid: { color: 'rgba(128,128,128,0.15)' },
        },
        y: {
          ticks: { font: { size: 11 } },
          grid: { color: 'rgba(128,128,128,0.15)' },
        },
      },
      plugins: {
        legend: { position: 'top' as const },
        zoom: {
          pan: { enabled: true, mode: 'x' as const },
          zoom: {
            wheel: { enabled: true },
            pinch: { enabled: true },
            drag: { enabled: true, backgroundColor: 'rgba(37,99,235,0.1)' },
            mode: 'x' as const,
          },
        },
      },
    };

    return { data: { labels, datasets }, options: opts };
  }, [rows, headers, dimension]);

  return (
    <div style={{ position: 'relative', height: 400 }}>
      <button
        onClick={() => chartRef.current?.resetZoom()}
        style={{
          position: 'absolute', top: 4, right: 4, zIndex: 1,
          padding: '2px 8px', fontSize: 11, cursor: 'pointer',
          background: 'var(--color-surface)', border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-sm)',
        }}
      >
        Zoom zuruecksetzen
      </button>
      <Line ref={chartRef} data={data} options={options} />
    </div>
  );
}
