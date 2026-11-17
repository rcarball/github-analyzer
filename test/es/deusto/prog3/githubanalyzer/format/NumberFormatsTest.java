package es.deusto.prog3.githubanalyzer.format;

import static org.junit.jupiter.api.Assertions.*;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.LOCALE)
class NumberFormatsTest {
    @Test void percentagesUseDecimalCommaTwoPlacesAndOneSpace() {
        assertEquals("0,00 %", NumberFormats.percentage(0));
        assertEquals("25,00 %", NumberFormats.percentage(.25));
        assertEquals("33,33 %", NumberFormats.percentage(1.0 / 3));
        assertEquals("62,50 %", NumberFormats.percentage(.625));
        assertEquals("100,00 %", NumberFormats.percentage(1));
    }

    @Test void roundingIsForPresentationOnlyAndCsvRetainsSixDecimalFractions() {
        double fraction = .123456789;
        assertEquals("12,35 %", NumberFormats.percentage(fraction));
        assertEquals("0,123457", NumberFormats.csvShare(fraction));
        assertEquals("1,000000", NumberFormats.csvShare(1));
        assertEquals("0,000000", NumberFormats.csvShare(0));
    }

    @Test void undefinedValuesNeverExposeNanOrInfinity() {
        for (double fraction : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals("—", NumberFormats.percentage(fraction));
            assertEquals("-", NumberFormats.csvShare(fraction));
        }
        assertEquals("—", NumberFormats.percentage(Double.MAX_VALUE));
    }

    @Test void machineLocaleCannotChangeTheSpanishPresentation() {
        Locale original = Locale.getDefault();
        Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
        Locale originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        try {
            for (Locale locale : new Locale[] {Locale.US, Locale.JAPAN, Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("es-ES")}) {
                Locale.setDefault(locale);
                assertEquals("25,00 %", NumberFormats.percentage(.25));
                String csv = NumberFormats.csvShare(.25);
                assertEquals("0,250000", csv);
                ParsePosition position = new ParsePosition(0);
                Number parsed = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).parse(csv, position);
                assertNotNull(parsed); assertEquals(csv.length(), position.getIndex());
                assertEquals(.25, parsed.doubleValue());
            }
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.FORMAT, originalFormat);
            Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
        }
    }
}
