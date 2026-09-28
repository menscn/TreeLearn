package com.treelearn.app.ui.math;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of {@link MathScanner#scan(String)}: markdown with formulas replaced by
 * placeholders, plus the extracted formulas in placeholder index order.
 */
public final class MathScanResult {

    public final String masked;

    public final List<MathFormula> formulas;

    public MathScanResult(String masked, List<MathFormula> formulas) {
        this.masked = masked;
        this.formulas = Collections.unmodifiableList(formulas);
    }
}
