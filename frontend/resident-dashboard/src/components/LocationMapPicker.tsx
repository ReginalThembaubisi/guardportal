import L from "leaflet";
import "leaflet/dist/leaflet.css";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import { useEffect, useRef } from "react";

// Leaflet's default marker icon references relative image paths that don't
// survive bundling — the standard fix is pointing it at the bundler-resolved
// URLs directly instead.
const defaultIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

// No property is actually here — just a reasonable default view (central
// Cape Town) so the map isn't showing the middle of the ocean before a
// guard's first checkpoint gets placed anywhere.
const FALLBACK_CENTER: [number, number] = [-33.9249, 18.4241];

interface LocationMapPickerProps {
  latitude: number | null;
  longitude: number | null;
  onPick: (lat: number, lng: number) => void;
}

/** Click-to-drop-a-pin location picker — the alternative to typing raw coordinates when you're not standing at the spot with "Use my current location". */
export default function LocationMapPicker({ latitude, longitude, onPick }: LocationMapPickerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const onPickRef = useRef(onPick);
  onPickRef.current = onPick;

  // Mount the map once.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const initialCenter: [number, number] =
      latitude !== null && longitude !== null ? [latitude, longitude] : FALLBACK_CENTER;

    const map = L.map(containerRef.current).setView(initialCenter, 17);
    mapRef.current = map;

    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(map);

    if (latitude !== null && longitude !== null) {
      markerRef.current = L.marker(initialCenter, { icon: defaultIcon }).addTo(map);
    }

    map.on("click", (e: L.LeafletMouseEvent) => {
      onPickRef.current(e.latlng.lat, e.latlng.lng);
    });

    return () => {
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
    // Mount once — external lat/lng changes are handled by the effect below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Keep the marker (and, the first time a location appears, the view) in
  // sync with lat/lng that changed some other way — typed directly into the
  // number fields, or "Use my current location".
  useEffect(() => {
    const map = mapRef.current;
    if (!map || latitude === null || longitude === null) return;
    const position: [number, number] = [latitude, longitude];

    if (!markerRef.current) {
      markerRef.current = L.marker(position, { icon: defaultIcon }).addTo(map);
      map.setView(position, 17);
    } else {
      markerRef.current.setLatLng(position);
      // Re-center on every change, not just the first: typing a coordinate
      // (or "Use my current location" firing after the map's already
      // showing somewhere else) should bring the pin back into view rather
      // than silently moving it off-screen.
      if (!map.getBounds().contains(position)) {
        map.panTo(position);
      }
    }
  }, [latitude, longitude]);

  return <div ref={containerRef} className="location-map-picker" />;
}
