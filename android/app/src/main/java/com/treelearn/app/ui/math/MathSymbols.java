package com.treelearn.app.ui.math;

import java.util.HashMap;
import java.util.Map;

/** Command name (without backslash) to Unicode glyph mapping for the supported LaTeX subset. */
public final class MathSymbols {

    private static final String[] TABLE = {
        "alpha", "α", "beta", "β", "gamma", "γ", "delta", "δ", "epsilon", "ε",
        "zeta", "ζ", "eta", "η", "theta", "θ", "iota", "ι", "kappa", "κ",
        "lambda", "λ", "mu", "μ", "nu", "ν", "xi", "ξ", "pi", "π", "rho", "ρ",
        "sigma", "σ", "tau", "τ", "upsilon", "υ", "phi", "φ", "chi", "χ",
        "psi", "ψ", "omega", "ω", "varepsilon", "ϵ", "vartheta", "ϑ", "varphi", "ϕ",
        "Gamma", "Γ", "Delta", "Δ", "Theta", "Θ", "Lambda", "Λ", "Xi", "Ξ",
        "Pi", "Π", "Sigma", "Σ", "Upsilon", "Υ", "Phi", "Φ", "Psi", "Ψ", "Omega", "Ω",
        "infty", "∞", "partial", "∂", "nabla", "∇", "ell", "ℓ", "hbar", "ℏ",
        "approx", "≈", "neq", "≠", "equiv", "≡", "sim", "∼", "simeq", "≃",
        "leq", "≤", "geq", "≥", "ll", "≪", "gg", "≫", "propto", "∝",
        "times", "×", "cdot", "·", "pm", "±", "mp", "∓", "div", "÷", "ast", "∗",
        "to", "→", "rightarrow", "→", "leftarrow", "←", "Rightarrow", "⇒",
        "Leftarrow", "⇐", "leftrightarrow", "↔", "Leftrightarrow", "⇔", "mapsto", "↦",
        "in", "∈", "notin", "∉", "ni", "∋", "subset", "⊂", "supset", "⊃",
        "subseteq", "⊆", "supseteq", "⊇", "cup", "∪", "cap", "∩", "setminus", "∖",
        "emptyset", "∅", "forall", "∀", "exists", "∃", "nexists", "∄", "neg", "¬",
        "ldots", "…", "cdots", "⋯", "vdots", "⋮", "dots", "…", "prime", "′",
        "angle", "∠", "deg", "°", "perp", "⊥", "parallel", "∥", "star", "⋆",
        "circ", "∘", "oplus", "⊕", "otimes", "⊗", "lfloor", "⌊", "rfloor", "⌋",
    };

    private static final Map<String, String> MAP = new HashMap<String, String>();

    static {
        for (int i = 0; i < TABLE.length; i += 2) {
            MAP.put(TABLE[i], TABLE[i + 1]);
        }
    }

    private MathSymbols() {
    }

    /** Unicode glyph for a command name, or null when the command is unknown. */
    public static String lookup(String command) {
        return command == null ? null : MAP.get(command);
    }
}
