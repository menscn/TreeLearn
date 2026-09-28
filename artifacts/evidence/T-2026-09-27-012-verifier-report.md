# T-2026-09-27-012 verifier report

- **Role:** Independent Android read-only verifier.
- **Scope:** Current-month 7x6 presenter mapping, metrics/empty-state screen composition, `MonthCalendarView`, aggregation/drawer isolation, README alignment, and available evidence.
- **Run policy:** No new tests, builds, or device runs were started. Existing JVM XML and screenshots were inspected only.

## Conclusion

**PARTIAL**

The production implementation is statically consistent with the core month-calendar behavior, but the task is not ready for ACCEPT because the current README still contains the superseded `详情页 7×70` claim and the visual evidence does not cover a populated calendar or a `sw600dp`/tablet layout. The detailed static record is in `artifacts/evidence/T-2026-09-27-012-verifier-static.txt`.

## Verified

- `StatsDetailPresenter.java:22-23` fixes the detail display at 42 cells (7 columns × 6 rows).
- `StatsDetailPresenter.java:130-147` uses `getDayOfWeek().getValue() - 1` for Monday-first placement and emits `-1.0f` for leading cells, month-end padding, and future dates. The active-day lookup still consumes `StatsAggregator` output rather than recounting.
- `StatsDetailPresenterTest.java:40-52` covers the September offset, active adjacent days, future transparency, and month-end padding; `StatsDetailPresenterTest.java:72-90` covers leap February 2028-02-29 and excludes March 1.
- `screen_stats_detail.xml:40-83` places the three metric cards before the heat card, in the order streak → questions → messages.
- `screen_stats_detail.xml:85-105` keeps the heat card and month label in the empty state. `StatsDetailScreen.java:65-80` hides only the weekday labels/grid/legend for empty data; it hides the entire heat block only for failed reads.
- `MonthCalendarView.java:14-16` defines the 7×6 grid; `MonthCalendarView.java:32-37` uses existing color resources; `MonthCalendarView.java:50-63` derives square-cell measurement from available width; `MonthCalendarView.java:69-78` skips blank cells and draws token colors.
- `android/data/src/main/java/com/treelearn/data/stats/StatsAggregator.java` has no current git diff. The detail presenter continues to call it directly.

## Findings

- **Documentation defect:** `android/README.md:454` still says the detail page supports `7×70`, contradicting `android/README.md:455` and the current presenter/view implementation. This fails the task's README acceptance criterion.
- **Evidence gap:** `artifacts/evidence/android-stats-detail-empty-2026-09-27-v2.png` proves the phone empty-state composition only. There is no isolated non-empty screenshot showing active colors, future transparency, and end-of-month blanks.
- **Evidence gap:** There is no real `sw600dp`/tablet-landscape statistics screenshot.
- **Coverage gap:** No direct `MonthCalendarView` test asserts its measured dimensions or canvas color/blank behavior. `HeatmapLayoutTest` covers shared intensity math, not this view's measurement and drawing path.
- **Drawer attribution:** `StatsAggregator.java` is unchanged. Drawer files are modified in the shared worktree by the parallel drawer slice; this verifier did not reset, overwrite, or attribute those changes to T-012. T-012's detail implementation remains on the shared aggregator path.

## Existing evidence inspected

- `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.StatsDetailPresenterTest.xml`: 5 tests, 0 failures, 0 errors, 0 skipped; parsed without rerunning.
- `artifacts/evidence/android-stats-detail-empty-2026-09-27-v2.png`: phone empty-state screenshot.
- `artifacts/evidence/android-stats-detail-2026-09-27.png`: older empty statistics screenshot; not accepted as non-empty proof.
- Full static evidence and command/result record: `artifacts/evidence/T-2026-09-27-012-verifier-static.txt`.

## Required follow-up for ACCEPT

- Remove the stale `7×70` detail claim from `android/README.md:454` and rerun the documentation reverse-search.
- Capture one populated phone statistics screenshot and one real `sw600dp`/tablet-landscape statistics screenshot.
- Add or provide focused evidence for `MonthCalendarView` measured size and blank/token drawing behavior.
