declare module '@goongmaps/goong-js' {
  export class Map {
    constructor(options: {
      container: HTMLElement | string;
      style: string;
      center?: [number, number];
      zoom?: number;
      attributionControl?: boolean;
    });
    setCenter(center: [number, number]): this;
    flyTo(options: { center: [number, number]; zoom?: number }): this;
    remove(): void;
    resize(): void;
    getCanvas(): HTMLCanvasElement;
    on(type: string, listener: (...args: unknown[]) => void): this;
    on(type: 'click', listener: (e: { lngLat: { lng: number; lat: number } }) => void): this;
  }

  export class Marker {
    constructor(options?: { draggable?: boolean; color?: string });
    setLngLat(lngLat: [number, number]): this;
    getLngLat(): { lng: number; lat: number };
    addTo(map: Map): this;
    remove(): void;
    on(type: string, listener: (...args: unknown[]) => void): this;
  }

  const goongjs: {
    accessToken: string;
    Map: typeof Map;
    Marker: typeof Marker;
  };
  export default goongjs;
}
