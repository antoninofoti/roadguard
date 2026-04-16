/**
 * Main Leaflet map component.
 *
 * Uses CartoDB DarkMatter tiles to match the dark theme.
 * Centers on Rome (41.9028, 12.4964) by default.
 */

import { useEffect, useRef, useMemo } from 'react';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import L from 'leaflet';
import { ReportMarker } from './ReportMarker';
import type { Report } from '../../types/report';

// Default center: Rome, Italy
const DEFAULT_CENTER: [number, number] = [41.9028, 12.4964];
const DEFAULT_ZOOM = 12;

// CartoDB Dark Matter tiles (free, dark themed)
const TILE_URL =
  'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';
const TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> contributors &copy; <a href="https://carto.com/">CARTO</a>';

interface ReportMapProps {
  reports: Report[];
  selectedId: string | null;
  onSelect: (report: Report) => void;
}

/** Helper component to fit map bounds to reports */
function FitBounds({ reports }: { reports: Report[] }) {
  const map = useMap();
  const prevLengthRef = useRef(0);

  useEffect(() => {
    if (reports.length > 0 && reports.length !== prevLengthRef.current) {
      const geoReports = reports.filter((r) => r.location !== null);
      if (geoReports.length > 0) {
        const bounds = L.latLngBounds(
          geoReports.map((r) => [
            r.location!.latitude,
            r.location!.longitude,
          ])
        );
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
      }
      prevLengthRef.current = reports.length;
    }
  }, [reports, map]);

  return null;
}

/** Helper component to fly to selected report */
function FlyToSelected({
  report,
}: {
  report: Report | undefined;
}) {
  const map = useMap();

  useEffect(() => {
    if (report?.location) {
      map.flyTo(
        [report.location.latitude, report.location.longitude],
        16,
        { duration: 0.8 }
      );
    }
  }, [report, map]);

  return null;
}

export function ReportMap({ reports, selectedId, onSelect }: ReportMapProps) {
  const selectedReport = useMemo(
    () => reports.find((r) => r.id === selectedId),
    [reports, selectedId]
  );

  // Custom cluster icon creator
  const createClusterCustomIcon = (cluster: { getChildCount: () => number }) => {
    const count = cluster.getChildCount();
    let size = 'small';
    let diameter = 36;

    if (count >= 50) {
      size = 'large';
      diameter = 48;
    } else if (count >= 10) {
      size = 'medium';
      diameter = 42;
    }

    return L.divIcon({
      html: `<div style="
        width: ${diameter}px;
        height: ${diameter}px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 50%;
        background: rgba(34, 211, 238, 0.15);
        border: 2px solid rgba(34, 211, 238, 0.4);
        color: #22d3ee;
        font-weight: 700;
        font-size: ${size === 'large' ? '14px' : '12px'};
        font-family: 'Inter', sans-serif;
        backdrop-filter: blur(8px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
      ">${count}</div>`,
      className: '',
      iconSize: L.point(diameter, diameter),
    });
  };

  return (
    <MapContainer
      center={DEFAULT_CENTER}
      zoom={DEFAULT_ZOOM}
      className="w-full h-full rounded-xl"
      zoomControl={true}
      attributionControl={true}
    >
      <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
      <FitBounds reports={reports} />
      <FlyToSelected report={selectedReport} />

      <MarkerClusterGroup
        iconCreateFunction={createClusterCustomIcon}
        maxClusterRadius={60}
        spiderfyOnMaxZoom={true}
        showCoverageOnHover={false}
        zoomToBoundsOnClick={true}
      >
        {reports
          .filter((r) => r.location !== null)
          .map((report) => (
            <ReportMarker
              key={report.id}
              report={report}
              isSelected={report.id === selectedId}
              onClick={() => onSelect(report)}
            />
          ))}
      </MarkerClusterGroup>
    </MapContainer>
  );
}
