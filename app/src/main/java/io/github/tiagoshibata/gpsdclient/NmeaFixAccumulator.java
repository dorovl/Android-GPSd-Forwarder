package io.github.tiagoshibata.gpsdclient;

import java.util.Locale;

public final class NmeaFixAccumulator {

    // ---- current fix state ----
    private Double lat;
    private Double lon;
    private Double alt;
    private Double speed;
    private Double course;

    private long fixTimeMs = -1;          // GNSS time (ms since midnight)
    private long lastEmittedTimeMs = -1;

    private int fixMode = 1;              // 1=no fix, 2=2D, 3=3D
    private int satellitesUsed = 0;

    private boolean hasRMC;
    private boolean hasGGA;

    // ---- public entry point ----
    public String onNmea(String nmea) {

        if (nmea == null || nmea.length() < 6 || nmea.charAt(0) != '$')
            return null;

        String type = nmea.substring(3, 6); // RMC, GGA, GSA

        switch (type) {
            case "RMC":
                parseRMC(nmea);
                break;
            case "GGA":
                parseGGA(nmea);
                break;
            case "GSA":
                parseGSA(nmea);
                break;
            default:
                return null;
        }

        if (readyToEmit()) {
            lastEmittedTimeMs = fixTimeMs;
            return toXgps();
        }

        return null;
    }

    // ---- readiness ----
    private boolean readyToEmit() {
        return hasRMC
                && hasGGA
                && fixMode >= 2
                && satellitesUsed >= 3
                && lat != null
                && lon != null
                && fixTimeMs > lastEmittedTimeMs;
    }

    // ---- parsers ----
    private void parseRMC(String s) {
        String[] f = s.split(",");
        if (f.length < 9) return;
        if (!"A".equals(f[2])) return;

        fixTimeMs = parseUtcTimeMs(f[1]);
        lat = parseLatLon(f[3], f[4]);
        lon = parseLatLon(f[5], f[6]);
        speed = knotsToMps(parseDouble(f[7]));
        course = parseDouble(f[8]);
        hasRMC = true;
    }

    private void parseGGA(String s) {
        String[] f = s.split(",");
        if (f.length < 10) return;

        fixTimeMs = parseUtcTimeMs(f[1]);
        lat = parseLatLon(f[2], f[3]);
        lon = parseLatLon(f[4], f[5]);
        alt = parseDouble(f[9]);
        hasGGA = true;
    }

    private void parseGSA(String s) {
        String[] f = s.split(",");
        if (f.length < 3) return;

        fixMode = parseFixMode(f[2]);

        if (fixMode < 2) {
            satellitesUsed = 0;
            return;
        }

        int count = 0;
        for (int i = 3; i <= 14 && i < f.length; i++) {
            if (!f[i].isEmpty())
                count++;
        }

        satellitesUsed = Math.max(satellitesUsed, count);
    }

    // ---- helpers ----
    private static long parseUtcTimeMs(String t) {
        if (t == null || t.isEmpty()) return -1;

        double v = Double.parseDouble(t); // hhmmss.sss
        int hh = (int)(v / 10000);
        int mm = (int)((v - hh * 10000) / 100);
        double ss = v - hh * 10000 - mm * 100;

        return ((long) hh * 3600L + (long) mm * 60L + (long) ss) * 1000L;
    }

    private static Double parseLatLon(String v, String hemi) {
        if (v == null || v.isEmpty()) return null;
        int p = v.indexOf('.') - 2;
        double deg = Double.parseDouble(v.substring(0, p));
        double min = Double.parseDouble(v.substring(p));
        double val = deg + min / 60.0;
        return ("S".equals(hemi) || "W".equals(hemi)) ? -val : val;
    }

    private static Double parseDouble(String v) {
        return (v == null || v.isEmpty()) ? null : Double.parseDouble(v);
    }

    private static int parseFixMode(String v) {
        return (v == null || v.isEmpty()) ? 1 : Integer.parseInt(v);
    }

    private static Double knotsToMps(Double k) {
        return k == null ? null : k * 0.514444;
    }

    // ---- output ----
    private String toXgps() {
        return String.format(
                Locale.US,
                "XGPS,%.7f,%.7f,%.1f,%.2f,%.1f,%d,%d",
                lat,
                lon,
                alt != null ? alt : 0.0,
                speed != null ? speed : 0.0,
                course != null ? course : 0.0,
                fixMode,
                fixTimeMs / 1000
        );
    }
}
