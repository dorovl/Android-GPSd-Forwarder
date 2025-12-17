package io.github.tiagoshibata.gpsdclient;

import java.util.Locale;
import android.util.Log;

public final class NmeaFixAccumulator {

    private static final long EPOCH_TIMEOUT_MS = 1500;

    private final Fix fix = new Fix();
    private long lastUpdateMs;
    private static final String TAG = "FixAccumulator";

    public static final class Fix {
        Double lat;
        Double lon;
        Double alt;
        Double speed;
        Double course;
        Integer fix;
        String time;

        boolean hasRMC;
        boolean hasGGA;
        boolean emitted;
    }

    public String onNmea(String nmea) {
        long now = System.currentTimeMillis();

        if (now - lastUpdateMs > EPOCH_TIMEOUT_MS) {
            resetEpoch();
        }
        lastUpdateMs = now;

        if (nmea.startsWith("$GPRMC") || nmea.startsWith("$GNRMC")) {
            parseRMC(nmea);
        } else if (nmea.startsWith("$GPGGA") || nmea.startsWith("$GNGGA")) {
            parseGGA(nmea);
        } else {
            return null;
        }

        if (readyToEmit()) {
            fix.emitted = true;
            return toXgps();
        }

        return null;
    }

    private boolean readyToEmit() {
        Log.d(TAG,
                "READY? RMC=" + fix.hasRMC +
                        " GGA=" + fix.hasGGA +
                        " fix=" + fix.fix +
                        " lat=" + fix.lat +
                        " lon=" + fix.lon);
        return !fix.emitted
                && fix.hasRMC
                && fix.hasGGA
                && fix.fix != null
                && fix.fix > 0
                && fix.lat != null
                && fix.lon != null;
    }

    private void resetEpoch() {
        fix.hasRMC = false;
        fix.hasGGA = false;
        fix.emitted = false;
    }

    private void parseRMC(String s) {
        String[] f = s.split(",");
        if (f.length < 9) return;
        if (!"A".equals(f[2])) return;

        fix.time = f[1];
        fix.lat = parseLatLon(f[3], f[4]);
        fix.lon = parseLatLon(f[5], f[6]);
        fix.speed = knotsToMps(parseDouble(f[7]));
        fix.course = parseDouble(f[8]);
        fix.hasRMC = true;
    }

    private void parseGGA(String s) {
        String[] f = s.split(",");
        if (f.length < 10) return;

        fix.time = f[1];
        fix.lat = parseLatLon(f[2], f[3]);
        fix.lon = parseLatLon(f[4], f[5]);
        fix.fix = parseInt(f[6]);
        fix.alt = parseDouble(f[9]);
        fix.hasGGA = true;
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

    private static Integer parseInt(String v) {
        return (v == null || v.isEmpty()) ? null : Integer.parseInt(v);
    }

    private static Double knotsToMps(Double k) {
        return k == null ? null : k * 0.514444;
    }

    private String toXgps() {
        return String.format(
                Locale.US,
                "XGPS,%.7f,%.7f,%.1f,%.2f,%.1f,%d,%s",
                fix.lat,
                fix.lon,
                fix.alt != null ? fix.alt : 0.0,
                fix.speed != null ? fix.speed : 0.0,
                fix.course != null ? fix.course : 0.0,
                fix.fix,
                fix.time != null ? fix.time : ""
        );
    }
}
