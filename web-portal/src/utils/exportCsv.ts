export function downloadReportsCsv(reports: any[]) {
  if (!reports || reports.length === 0) return;

  const headers = [
    "id",
    "timestamp",
    "latitude",
    "longitude",
    "damageType",
    "severity",
    "fusedScore",
    "cvConfidence",
    "sensorConfidence",
    "status",
    "userId",
    "operatorId",
    "notes",
  ];

  const rows = reports.map((r) => {
    const ts = r.timestamp
      ? r.timestamp.toDate
        ? r.timestamp.toDate().toISOString()
        : r.timestamp.toISOString()
      : "";
    const lat = r.location?.latitude ?? "";
    const lng = r.location?.longitude ?? "";
    return [
      r.id,
      ts,
      lat,
      lng,
      r.damageType ?? "",
      r.severity ?? "",
      r.fusedScore ?? "",
      r.cvConfidence ?? "",
      r.sensorConfidence ?? "",
      r.status ?? "",
      r.userId ?? "",
      r.operatorId ?? "",
      (r.notes ?? "").replace(/\n/g, " "),
    ];
  });

  const csv = [
    headers.join(","),
    ...rows.map((r) =>
      r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(","),
    ),
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `roadguard_reports_${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
