import { useEffect, useRef } from 'react';
import { createDraggableMarker, createGoongMap, goongMaptilesKey, type LngLat } from './goong';

type Props = {
  open: boolean;
  latitude: number | null;
  longitude: number | null;
  fallbackCenter: LngLat;
  onChange: (pos: LngLat) => void;
  className?: string;
};

/**
 * Bản đồ Goong: click để chọn vị trí + kéo ghim.
 */
export function GoongMapPicker({ open, latitude, longitude, fallbackCenter, onChange, className }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<ReturnType<typeof createGoongMap> | null>(null);
  const markerRef = useRef<ReturnType<typeof createDraggableMarker> | null>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const hasKey = Boolean(goongMaptilesKey());
  const center: LngLat =
    latitude != null && longitude != null ? { lat: latitude, lng: longitude } : fallbackCenter;

  useEffect(() => {
    if (!open || !hasKey || !containerRef.current) return;

    const map = createGoongMap(containerRef.current, center, 14);
    const marker = createDraggableMarker(map, center, (pos) => onChangeRef.current(pos));
    mapRef.current = map;
    markerRef.current = marker;

    const canvas = map.getCanvas();
    let panning = false;
    const setSelectCursor = () => {
      if (!panning) canvas.style.cursor = 'crosshair';
    };
    const setGrabbingCursor = () => {
      panning = true;
      canvas.style.cursor = 'grabbing';
    };
    const endPan = () => {
      panning = false;
      canvas.style.cursor = 'crosshair';
    };
    setSelectCursor();

    const onClick = (e: { lngLat: { lng: number; lat: number } }) => {
      const pos = { lng: e.lngLat.lng, lat: e.lngLat.lat };
      marker.setLngLat([pos.lng, pos.lat]);
      onChangeRef.current(pos);
      setSelectCursor();
    };
    map.on('click', onClick);
    map.on('dragstart', setGrabbingCursor);
    map.on('dragend', endPan);
    map.on('mousemove', setSelectCursor);

    const onLoad = () => {
      map.resize();
      setSelectCursor();
    };
    map.on('load', onLoad);
    const t = window.setTimeout(() => map.resize(), 80);

    return () => {
      window.clearTimeout(t);
      marker.remove();
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional mount lifecycle
  }, [open, hasKey]);

  useEffect(() => {
    if (!open || !mapRef.current || !markerRef.current) return;
    if (latitude == null || longitude == null) return;
    const next: [number, number] = [longitude, latitude];
    markerRef.current.setLngLat(next);
    mapRef.current.flyTo({ center: next, zoom: 15 });
  }, [open, latitude, longitude]);

  if (!hasKey) {
    return (
      <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-small text-amber-900">
        Chưa cấu hình <code>VITE_GOONG_MAPTILES_KEY</code> — map tương tác tắt. Lưu địa chỉ vẫn geocode phía API.
      </p>
    );
  }

  return (
    <div className={className}>
      <div ref={containerRef} className="h-56 w-full overflow-hidden rounded-md border border-ink-border" />
    </div>
  );
}
