package com.richbeatty.phnlnk.shell;

/** Stateful 16 kHz mono PCM16 to 48 kHz stereo adapter; no Android APIs.
 * A 97-tap Blackman-windowed sinc suppresses upsampling images. Each phase
 * has unity DC gain. State survives socket-frame boundaries; delay is 1 ms.
 */
public final class NativeUplinkPcm {
    private static final int TAPS = 33;
    private static final double[][] COEFFICIENTS = coefficients();
    private final short[] history = new short[TAPS];
    private int newest;

    public short[] convert(short[] input) {
        short[] output = new short[Math.multiplyExact(input.length, 6)];
        int at = 0;
        for (short sample : input) {
            newest = (newest + 1) % TAPS;
            history[newest] = sample;
            for (int phase = 0; phase < 3; phase++) {
                double value = 0;
                for (int tap = 0; tap < TAPS; tap++)
                    value += COEFFICIENTS[phase][tap]
                            * history[(newest - tap + TAPS) % TAPS];
                short clipped = (short)Math.max(Short.MIN_VALUE,
                        Math.min(Short.MAX_VALUE, Math.round(value)));
                output[at++] = clipped;
                output[at++] = clipped;
            }
        }
        return output;
    }

    private static double[][] coefficients() {
        double[][] phases = new double[3][TAPS];
        double cutoff = 7000.0 / 48000;
        for (int k = 0; k <= 96; k++) {
            double x = 2 * cutoff * (k - 48);
            double sinc = x == 0 ? 1 : Math.sin(Math.PI * x) / (Math.PI * x);
            double window = 0.42 - 0.5 * Math.cos(2 * Math.PI * k / 96)
                    + 0.08 * Math.cos(4 * Math.PI * k / 96);
            phases[k % 3][k / 3] = 6 * cutoff * sinc * window;
        }
        for (double[] phase : phases) {
            double sum = 0;
            for (double c : phase) sum += c;
            for (int i = 0; i < phase.length; i++) phase[i] /= sum;
        }
        return phases;
    }
}
