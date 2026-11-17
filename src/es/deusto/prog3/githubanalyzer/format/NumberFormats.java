package es.deusto.prog3.githubanalyzer.format;

import java.util.Locale;

/** Spanish numeric presentation, independent of the machine's language settings. */
public final class NumberFormats {
    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");

    private NumberFormats() { }

    /** Display a fraction as a percentage; rounding never changes the underlying metric. */
    public static String percentage(double fraction) {
        double percent = fraction * 100;
        return Double.isFinite(percent) ? String.format(SPANISH, "%.2f %%", percent) : "—";
    }

    /** Unquoted numeric fraction for a semicolon-separated Spanish CSV. */
    public static String csvShare(double fraction) {
        return Double.isFinite(fraction) ? String.format(SPANISH, "%.6f", fraction) : "-";
    }
}
