import './Chip.css';

interface ChipProps {
  label: string;
  value: React.ReactNode;
}

export function Chip({ label, value }: ChipProps) {
  return (
    <span className="chip">
      <span className="chip-label">{label}</span> {value}
    </span>
  );
}

export function ChipGroup({ children }: { children: React.ReactNode }) {
  return <div className="chip-group">{children}</div>;
}
