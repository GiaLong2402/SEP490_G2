import { useMemo } from 'react';
import { Field, Select } from '@/shared/ui';
import { listProvinces, listWardsByProvinceCode, provinceCodeOf } from './vnAdmin';

type Props = {
  province: string | null;
  ward: string | null;
  onChange: (next: { province: string | null; ward: string | null }) => void;
  className?: string;
};

/** Cascade địa giới hành chính 2025: Tỉnh/thành → Phường/xã (không còn cấp quận/huyện). */
export function AdminUnitFields({ province, ward, onChange, className = '' }: Props) {
  const provinces = useMemo(() => listProvinces(), []);
  const provinceCode = provinceCodeOf(province);
  const wardOptions = useMemo(() => listWardsByProvinceCode(provinceCode), [provinceCode]);

  return (
    <div className={`contents ${className}`}>
      <Field label="Tỉnh/thành" required>
        <Select
          value={provinceCode ?? ''}
          onChange={(e) => {
            const code = e.target.value;
            const p = provinces.find((x) => x.code === code);
            onChange({ province: p?.name ?? null, ward: null });
          }}
        >
          <option value="">— Chọn tỉnh/thành —</option>
          {provinces.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name}
            </option>
          ))}
        </Select>
      </Field>

      <Field
        label="Phường/xã"
        hint={!provinceCode ? 'Chọn tỉnh/thành trước' : undefined}
      >
        <Select
          value={wardOptions.find((w) => w.name === ward)?.code ?? ''}
          disabled={!provinceCode}
          onChange={(e) => {
            const code = e.target.value;
            const w = wardOptions.find((x) => x.code === code);
            onChange({ province, ward: w?.name ?? null });
          }}
        >
          <option value="">— Chọn phường/xã —</option>
          {wardOptions.map((w) => (
            <option key={w.code} value={w.code}>
              {w.name}
            </option>
          ))}
        </Select>
      </Field>
    </div>
  );
}
