package torneos.infra;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * BORDE de medición: mide cada fase con System.nanoTime() y muestrea la memoria al terminarla.
 * Es el único objeto con estado mutable fuera del generador LCG (la medición es un efecto).
 */
public final class Cronometro {
    private final Map<String, Double> tiemposMs = new LinkedHashMap<>();
    private long picoMuestreadoBytes = 0;

    public <T> T medir(String fase, Supplier<T> bloque) {
        long t0 = System.nanoTime();
        try {
            return bloque.get();
        } finally {
            tiemposMs.put(fase, (System.nanoTime() - t0) / 1e6);
            picoMuestreadoBytes = Math.max(picoMuestreadoBytes, Memoria.heapUsadoBytes());
        }
    }

    public Map<String, Double> tiemposMs() { return tiemposMs; }

    public long picoMuestreadoBytes() { return picoMuestreadoBytes; }
}
