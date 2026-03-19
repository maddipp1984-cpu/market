import { useEffect, useRef, useMemo } from 'react';
import { createChart, type IChartApi, type ISeriesApi, LineSeries, type UTCTimestamp } from 'lightweight-charts';
import type { ChartProps } from './chartTypes';
import { SERIES_COLORS } from './chartTypes';

export function LightweightChart({ rows, headers, dimension: _dimension }: ChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Line'>[]>([]);

  // Prepare data once
  const seriesData = useMemo(() => {
    return headers.map((_, s) => {
      const points: { time: UTCTimestamp; value: number }[] = [];
      for (const r of rows) {
        const v = r.values[s];
        if (!isNaN(v)) {
          points.push({ time: (r.timestampMs / 1000) as UTCTimestamp, value: v });
        }
      }
      return points;
    });
  }, [rows, headers]);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      width: containerRef.current.clientWidth,
      height: 400,
      layout: {
        background: { color: 'transparent' },
        textColor: '#999',
        fontSize: 11,
      },
      grid: {
        vertLines: { color: 'rgba(128,128,128,0.15)' },
        horzLines: { color: 'rgba(128,128,128,0.15)' },
      },
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
      },
      crosshair: { mode: 0 },
    });
    chartRef.current = chart;

    const series: ISeriesApi<'Line'>[] = [];
    for (let s = 0; s < headers.length; s++) {
      const line = chart.addSeries(LineSeries, {
        color: SERIES_COLORS[s % SERIES_COLORS.length],
        lineWidth: 2,
        title: headers[s].tsKey,
        priceLineVisible: false,
      });
      line.setData(seriesData[s]);
      series.push(line);
    }
    seriesRef.current = series;

    chart.timeScale().fitContent();

    // Resize observer
    const ro = new ResizeObserver(entries => {
      const { width } = entries[0].contentRect;
      chart.applyOptions({ width });
    });
    ro.observe(containerRef.current);

    return () => {
      ro.disconnect();
      chart.remove();
      chartRef.current = null;
      seriesRef.current = [];
    };
  }, [headers, seriesData]);

  return (
    <div>
      <div ref={containerRef} style={{ width: '100%', height: 400 }} />
      {headers.length > 1 && (
        <div style={{ display: 'flex', gap: 12, padding: '8px 0', flexWrap: 'wrap', fontSize: 12 }}>
          {headers.map((h, i) => (
            <div key={h.tsId} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <span style={{
                width: 12, height: 3, borderRadius: 1,
                background: SERIES_COLORS[i % SERIES_COLORS.length],
                display: 'inline-block',
              }} />
              {h.tsKey}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
