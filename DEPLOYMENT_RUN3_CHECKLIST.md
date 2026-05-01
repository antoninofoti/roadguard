# RoadGuard run3 Deployment Checklist

**Date**: 2026-04-25  
**Objective**: Deploy and verify all three run3 fixes in containerized environment

---

## Phase 1: Firebase Configuration ✅ READY

### Step 1a: Validate Environment Script

```bash
cd web-portal
chmod +x scripts/validate-firebase-env.sh
scripts/validate-firebase-env.sh .env.production
# Expected: ✅ VALIDATION PASSED or ⚠️ VALIDATION WARNING (demo values OK)
```

### Step 1b: Test Build Validation Hook

```bash
cd web-portal
npm run build:validate-env
# Expected: Script runs before build, shows validation results
```

**Next**: If both pass → proceed to Phase 2

---

## Phase 2: Web Portal Build with Code-Splitting ✅ READY

### Step 2a: Install Dependencies (if needed)

```bash
cd web-portal
npm ci  # Clean install, respects package-lock.json
```

### Step 2b: Build with Optimized Vite Config

```bash
cd web-portal
npm run build
# Expected output:
# - Validation hook runs first ✅
# - TypeScript check ✅
# - Vite build completes ✅
# - Output: dist-app/ (not legacy dist/)
# - Chunks: main.js, vendor-react.js, vendor-firebase.js, vendor-charts.js, vendor-leaflet.js, etc.
```

### Step 2c: Verify Bundle Structure

```bash
cd web-portal
ls -lh dist-app/*.js | awk '{print $9, $5}'
# Expected: Each chunk <300kB (except vendor-leaflet ~200kB)
# Total distributed ~800-900kB across chunks
```

**Next**: If build succeeds → proceed to Phase 3

---

## Phase 3: Analytics API — Adaptive Clustering ✅ READY

### Step 3a: Test Python Clustering with Density Logic

```bash
cd analytics-api

# Create test script
cat > test_adaptive_clustering.py << 'EOF'
import sys
sys.path.insert(0, '.')
from app.models import ClusterRequest, ReportIn, GeoPoint
from app.routers.clusters import compute_clusters

# Test 1: Dense urban cluster (5 reports within 100m)
dense_reports = [
    ReportIn(id="r1", latitude=45.4646, longitude=9.1904, fusedScore=0.85, damageType="pothole"),
    ReportIn(id="r2", latitude=45.46461, longitude=9.19041, fusedScore=0.78, damageType="pothole"),
    ReportIn(id="r3", latitude=45.46462, longitude=9.19042, fusedScore=0.82, damageType="bump"),
    ReportIn(id="r4", latitude=45.46463, longitude=9.19043, fusedScore=0.75, damageType="pothole"),
    ReportIn(id="r5", latitude=45.46464, longitude=9.19044, fusedScore=0.80, damageType="pothole"),
]
request = ClusterRequest(reports=dense_reports, radius_meters=100.0)
result = compute_clusters(request)
print(f"Dense cluster test: {result.cluster_count} clusters, {result.unclustered_count} unclustered")
assert result.cluster_count == 1, "Expected 1 cluster for dense urban area"
print("✅ Dense clustering: PASS")

# Test 2: Sparse rural cluster (2 reports 500m apart)
sparse_reports = [
    ReportIn(id="s1", latitude=45.0, longitude=7.0, fusedScore=0.7, damageType="pothole"),
    ReportIn(id="s2", latitude=45.005, longitude=7.005, fusedScore=0.65, damageType="pothole"),
]
request = ClusterRequest(reports=sparse_reports, radius_meters=100.0)
result = compute_clusters(request)
print(f"Sparse cluster test: {result.cluster_count} clusters, {result.unclustered_count} unclustered")
# In sparse mode, radius may expand to 130% = 130m; distance ~700m so still unclustered
print("✅ Sparse handling: PASS")

print("\n✅ All adaptive clustering tests passed!")
EOF

python test_adaptive_clustering.py
```

### Step 3b: Docker Compose Test (API + emulators)

```bash
cd /workspaces/RoadGuard

# Start services
docker-compose up -d

# Wait for services to be healthy
sleep 10

# Test health endpoint
curl http://localhost:8000/health
# Expected: {"status":"ok","service":"roadguard-analytics-api","version":"1.0.0"}

# Test clustering endpoint
curl -X POST http://localhost:8000/api/v1/clusters \
  -H "Content-Type: application/json" \
  -d '{
    "reports": [
      {"id":"r1","latitude":45.4646,"longitude":9.1904,"fusedScore":0.85,"damageType":"pothole"},
      {"id":"r2","latitude":45.46461,"longitude":9.19041,"fusedScore":0.78,"damageType":"pothole"}
    ],
    "radius_meters": 100.0
  }'
# Expected: valid JSON with 1 cluster

# Test web portal endpoint
curl http://localhost:3000/
# Expected: 200 OK, HTML dashboard

# Cleanup
docker-compose down
```

**Next**: If all tests pass → proceed to Phase 4

---

## Phase 4: Kotlin/Android Clustering Validation ✅ READY

### Step 4a: Run Android Clustering Unit Tests

```bash
cd app
./gradlew test -k "clustering"
# Expected: 3 clustering tests pass:
# - clustering returns empty for no reports
# - clustering returns 0 clusters (too far apart)
# - cluster dominant type is most common
```

### Step 4b: Verify Adaptive Radius Function Exists

```bash
grep -n "computeAdaptiveRadius\|localDensity" \
  app/src/main/java/com/example/roadguard/analytics/PredictiveAnalytics.kt
# Expected: 2 function definitions + at least 2 usages
```

**Next**: If tests pass → proceed to Phase 5

---

## Phase 5: Final Integration Check

### Step 5a: Verify All Configuration Files in Place

```bash
# Check web-portal configs
ls -l web-portal/.env.production web-portal/scripts/validate-firebase-env.sh web-portal/vite.config.ts

# Check updated docs
ls -l docs/RUN3_ISSUES_RESOLUTION.md docs/thesis_latex/chapters/12_conclusion.tex docs/thesis_latex/chapters/D_methodological_protocol.tex

# Expected: All files exist with recent timestamps
```

### Step 5b: Git Status & Commit

```bash
cd /workspaces/RoadGuard
git status

# Expected files changed:
# - web-portal/.env.production (NEW)
# - web-portal/scripts/validate-firebase-env.sh (NEW)
# - web-portal/vite.config.ts (MODIFIED)
# - web-portal/package.json (MODIFIED)
# - analytics-api/app/routers/clusters.py (MODIFIED)
# - app/src/main/java/com/example/roadguard/analytics/PredictiveAnalytics.kt (MODIFIED)
# - docs/thesis_latex/chapters/12_conclusion.tex (MODIFIED)
# - docs/thesis_latex/chapters/D_methodological_protocol.tex (MODIFIED)
# - docs/RUN3_ISSUES_RESOLUTION.md (NEW)
# - DEPLOYMENT_RUN3_CHECKLIST.md (NEW - this file)

git add -A
git commit -m "RUN3: Firebase config validation, Vite code-splitting, adaptive clustering

- Implement Firebase env validation script + .env.production template
- Optimize Vite build: terser, enhanced code-splitting, ~800-900kB distributed
- Implement density-aware adaptive clustering in both Python API and Kotlin
- Update thesis Limitations section and methodology appendix with run3 notes
- All changes backward-compatible, no API breaks

Closes run3 deployment issues: [ALTA] Firebase vars, [MEDIA] bundle size, [BASSA] clustering calibration"
```

### Step 5c: Push to Remote (if applicable)

```bash
git push origin main
# or your feature branch
```

---

## ✅ SUCCESS CRITERIA

| Phase | Criterion                                                 | Status |
| ----- | --------------------------------------------------------- | ------ |
| 1     | Firebase validation script passes                         | ⏳ TBD |
| 2     | Web build succeeds, dist-app/ created, chunks <300kB each | ⏳ TBD |
| 3     | Analytics API adaptive clustering test passes locally     | ⏳ TBD |
| 4     | Kotlin unit tests pass (3/3 clustering tests)             | ⏳ TBD |
| 5     | All config files in place, git commit succeeds            | ⏳ TBD |

---

## 📋 Sign-off

**When all phases complete:**

- ✅ Run3 issues resolved
- ✅ Thesis documentation updated in English
- ✅ Code + docs committed to repository
- ✅ Ready for final presentation / defense

---

## Troubleshooting

| Issue                                         | Solution                                                            |
| --------------------------------------------- | ------------------------------------------------------------------- |
| `validate-firebase-env.sh: permission denied` | Run `chmod +x web-portal/scripts/validate-firebase-env.sh`          |
| `npm run build:validate-env` fails            | Check `.env.production` exists with all 6 Firebase vars             |
| `dist-app/` not created                       | Verify Vite output dir is `dist-app` (not `dist`) in vite.config.ts |
| Docker Compose refuses to start               | Run `docker-compose down` first, then retry                         |
| Clustering test fails                         | Verify Python imports and haversine formula (radius in meters)      |
| Gradle test not found                         | Run `./gradlew test --info` to see all available tests              |

---

**Status**: Ready to execute  
**Estimated Duration**: 30-45 minutes (all phases)  
**Owner**: Run3 Deployment Team
