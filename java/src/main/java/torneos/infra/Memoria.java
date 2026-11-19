package torneos.infra;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;

/** Instrumentos de memoria de la JVM (Runtime + MemoryPoolMXBean) y del proceso (/proc en Linux). */
public final class Memoria {
    private Memoria() { }

    public static long heapUsadoBytes() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    public static void reiniciarPicos() {
        ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
    }

    /** Suma de los picos de cada pool del heap desde el último reinicio. */
    public static long picoHeapPoolsBytes() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(p -> p.getType() == MemoryType.HEAP)
                .mapToLong(p -> p.getPeakUsage().getUsed())
                .sum();
    }

    /** Pico de memoria residente del proceso (VmHWM). En Windows/macOS devuelve -1 (lo mide el orquestador). */
    public static long picoProcesoBytes() {
        try {
            return Files.readAllLines(Path.of("/proc/self/status")).stream()
                    .filter(l -> l.startsWith("VmHWM:"))
                    .map(l -> l.replaceAll("[^0-9]", ""))
                    .mapToLong(Long::parseLong)
                    .map(kb -> kb * 1024)
                    .findFirst().orElse(-1);
        } catch (Exception e) {
            return -1;
        }
    }
}
