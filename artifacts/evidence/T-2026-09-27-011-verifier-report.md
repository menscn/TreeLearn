# T-2026-09-27-011 verifier report

- **Role:** Independent Android read-only verifier.
- **Scope:** `drawer_heatmap.xml` empty card, `HomeDrawerScreen` visibility, `HomeStats` future/month-end sentinels, `HeatmapView` blank skipping, `HomeDrawerScreenInstrumentedTest`, and available visual/device evidence.
- **Run policy:** No new tests, builds, or device runs were started. Existing result XML and screenshots were inspected only.

## Conclusion

**REJECT**

The empty-state structure and blank-cell sentinel path are present, but the populated drawer calendar has a production coordinate mismatch: `HomeStats` stores month cells row-major while `HeatmapView` renders them column-major through `HeatmapLayout`. With seven horizontal weekday labels, active dates will appear under the wrong weekday/week position. The detailed static proof is in `artifacts/evidence/T-2026-09-27-011-verifier-static.txt`.

## Verified

- `drawer_heatmap.xml:4-14` defines the clickable heat card, existing card styling, accessibility description, and `touch_min` minimum height.
- `drawer_heatmap.xml:45-53` provides the in-card empty message; `drawer_heatmap.xml:55-85` provides Monday-to-Sunday labels and a 7×6 `HeatmapView`.
- `HomeDrawerScreen.java:109-120` keeps the heat card visible when a stats object exists, shows the empty message and hides weekday/cells for `HomeStats.empty`, and supplies `monthIntensities` for non-empty data.
- `HomeStats.java:58-61` initializes month cells to `-1.0f`; `HomeStats.java:41-50` fills only current-month dates through today, so future and month-end padding remain transparent sentinels.
- `HeatmapView.java:62-70` records negative values as blank; `HeatmapView.java:80-90` skips blank cells before drawing.
- `HomeDrawerScreenInstrumentedTest.java:24-35` has the intended empty-state assertions: card visible, empty text visible, cells gone.

## Blocking finding

- **P1 coordinate mismatch:** `HomeStats.of()` uses `cell = firstDayOffset + day`, which is row-major across seven weekday columns. `HeatmapLayout.cellX/cellY()` use `column = index / rows` and `row = index % rows`, which is column-major. For the existing fixture date `2026-09-23`, the producer stores index `23`; the expected calendar position is Wednesday column `2`, week row `3`, but the renderer places index `23` at column `3`, row `5`. This is a visible data-state defect, not merely missing evidence.

## Evidence gaps

- The current connected-result directory has no inspectable result for `HomeDrawerScreenInstrumentedTest`; the available XML is for `HomeBackdropInstrumentedTest`.
- `HomeStatsTest` has an existing result of 3 tests, 0 failures, 0 errors, 0 skipped, but it validates array indices and sentinels only; it does not validate the producer-to-view coordinate transform.
- `artifacts/evidence/android-home-drawer-2026-09-27-v2.png` and `artifacts/evidence/android-home-drawer-2026-09-27.png` are phone screenshots only. They do not establish a known active-date fixture, the empty-message visibility contract, or tablet behavior.
- No isolated non-empty drawer screenshot or real `sw600dp`/tablet-landscape screenshot is available.

## Required follow-up before ACCEPT

- Align the month-cell producer and renderer to one coordinate convention, then add a regression assertion that checks a known date's rendered weekday/week position rather than only its array index.
- Produce an inspectable `HomeDrawerScreenInstrumentedTest` result for the empty-card contract.
- Capture isolated non-empty phone and `sw600dp`/tablet drawer screenshots with known fixture dates.
