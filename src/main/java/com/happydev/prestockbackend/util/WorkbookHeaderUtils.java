package com.happydev.prestockbackend.util;

import java.text.Normalizer;
import java.util.Locale;

public final class WorkbookHeaderUtils {

    private WorkbookHeaderUtils() {
    }

    public static String normalizeHeaderKey(String raw) {
        if (raw == null) {
            return "";
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        n = n.replace('\u00a0', ' ');
        n = n.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n");
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        n = n.replaceAll("[^a-z0-9]+", "");
        return n;
    }

    public static String normalizeSheetName(String raw) {
        if (raw == null) {
            return "";
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        n = n.replace('\u00a0', ' ');
        n = n.replaceAll("\\s+", "");
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n;
    }
}
