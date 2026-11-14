import goongjs from '@goongmaps/goong-js';
import '@goongmaps/goong-js/dist/goong-js.css';
import './goong-map.css';

export type LngLat = { lng: number; lat: number };

export function goongMaptilesKey(): string {
  return (import.meta.env.VITE_GOONG_MAPTILES_KEY as string | undefined)?.trim() ?? '';
}

export function createGoongMap(
  container: HTMLElement,
  center: LngLat,
  zoom = 14,
): InstanceType<typeof goongjs.Map> {
  const token = goongMaptilesKey();
  if (!token) {
    throw new Error('Thiếu VITE_GOONG_MAPTILES_KEY');
  }
  goongjs.accessToken = token;
  return new goongjs.Map({
    container,
    style: 'https://tiles.goong.io/assets/goong_map_web.json',
    center: [center.lng, center.lat],
    zoom,
    attributionControl: false,
  });
}

export function createDraggableMarker(
  map: InstanceType<typeof goongjs.Map>,
  center: LngLat,
  onDragEnd: (pos: LngLat) => void,
): InstanceType<typeof goongjs.Marker> {
  const marker = new goongjs.Marker({ draggable: true }).setLngLat([center.lng, center.lat]).addTo(map);
  marker.on('dragend', () => {
    const ll = marker.getLngLat();
    onDragEnd({ lng: ll.lng, lat: ll.lat });
  });
  return marker;
}

export { goongjs };
