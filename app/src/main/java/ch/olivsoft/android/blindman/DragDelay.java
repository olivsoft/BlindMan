package ch.olivsoft.android.blindman;

/**
 * Just an enumeration for delay settings.
 */
public enum DragDelay {
    D0(200),
    D1(400),
    D2(700),
    D3(1000);

    public int millis;

    DragDelay(int millis) {
        this.millis = millis;
    }

    // We leave this as an on-the-fly method assuming it is fast enough
    public static String[] getDelays() {
        int nv = values().length;
        String[] sd = new String[nv];
        for (int i = 0; i < nv; i++) {
            sd[i] = String.format("%.1f %s", 0.001f * values()[i].millis, "s");
        }
        return sd;
    }
}
