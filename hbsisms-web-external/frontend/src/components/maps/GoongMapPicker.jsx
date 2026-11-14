import { useEffect, useRef } from 'react'
import goongjs from '@goongmaps/goong-js'
import '@goongmaps/goong-js/dist/goong-js.css'
import './GoongMapPicker.css'

function maptilesKey() {
  return (import.meta.env.VITE_GOONG_MAPTILES_KEY || '').trim()
}

/**
 * Goong map: click để chọn vị trí + kéo ghim.
 */
export default function GoongMapPicker({ open, latitude, longitude, fallbackCenter, onChange }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange

  const hasKey = Boolean(maptilesKey())
  const center =
    latitude != null && longitude != null
      ? { lat: Number(latitude), lng: Number(longitude) }
      : fallbackCenter

  useEffect(() => {
    if (!open || !hasKey || !containerRef.current) return

    goongjs.accessToken = maptilesKey()
    const map = new goongjs.Map({
      container: containerRef.current,
      style: 'https://tiles.goong.io/assets/goong_map_web.json',
      center: [center.lng, center.lat],
      zoom: 14,
      attributionControl: false,
    })
    const marker = new goongjs.Marker({ draggable: true }).setLngLat([center.lng, center.lat]).addTo(map)
    const canvas = map.getCanvas()
    let panning = false
    const setSelectCursor = () => {
      if (!panning) canvas.style.cursor = 'crosshair'
    }
    const setGrabbingCursor = () => {
      panning = true
      canvas.style.cursor = 'grabbing'
    }
    const endPan = () => {
      panning = false
      canvas.style.cursor = 'crosshair'
    }
    setSelectCursor()

    marker.on('dragend', () => {
      const ll = marker.getLngLat()
      onChangeRef.current?.({ lat: ll.lat, lng: ll.lng })
      setSelectCursor()
    })
    map.on('click', (e) => {
      const pos = { lat: e.lngLat.lat, lng: e.lngLat.lng }
      marker.setLngLat([pos.lng, pos.lat])
      onChangeRef.current?.(pos)
      setSelectCursor()
    })
    map.on('dragstart', setGrabbingCursor)
    map.on('dragend', endPan)
    map.on('mousemove', setSelectCursor)

    mapRef.current = map
    markerRef.current = marker
    map.on('load', () => {
      map.resize()
      setSelectCursor()
    })
    const t = window.setTimeout(() => map.resize(), 80)

    return () => {
      window.clearTimeout(t)
      marker.remove()
      map.remove()
      mapRef.current = null
      markerRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount when modal opens
  }, [open, hasKey])

  useEffect(() => {
    if (!open || !mapRef.current || !markerRef.current) return
    if (latitude == null || longitude == null) return
    const next = [Number(longitude), Number(latitude)]
    markerRef.current.setLngLat(next)
    mapRef.current.flyTo({ center: next, zoom: 15 })
  }, [open, latitude, longitude])

  if (!hasKey) {
    return (
      <p className="goong-map-fallback">
        Chưa cấu hình VITE_GOONG_MAPTILES_KEY — map tắt. Lưu địa chỉ vẫn geocode phía API.
      </p>
    )
  }

  return (
    <div className="goong-map-picker">
      <div ref={containerRef} className="goong-map-picker__canvas" />
    </div>
  )
}
