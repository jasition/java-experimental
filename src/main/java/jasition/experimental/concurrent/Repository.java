package jasition.experimental.concurrent;


import java.util.Comparator;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * In the domain composed of immutable data structures and functions only, all we need is a central repository of the
 * aggregates. As each update has to depend on a previous snapshot, versioning is enforced that only the update of
 * version (e.g. current + 1) will be accepted. Otherwise a rebase will occur that the update function is invoked again
 * but with the latest version as the parameter. There is a hard limit of how much times the update can be attempted,
 * in order to avoid any potential infinite loop and malicious hacking.
 *
 * @param <T>
 */
public class Repository<V, T extends Versioned<V>> {
    private final Function<V, V> nextVersionProducer;
    private final Comparator<V> versionComparator;

    /**
     * Use internal lock to avoid deadlock caused by external synchronization.
     */
    private final Object internalLock = new Object();
    private volatile T latestVersion;
    private volatile int maxTry = 3;

    public Repository(Function<V, V> nextVersionProducer,
                      Comparator<V> versionComparator,
                      T initialValue) {
        this.nextVersionProducer = requireNonNull(nextVersionProducer);
        this.versionComparator = requireNonNull(versionComparator);
        latestVersion = requireNonNull(initialValue);
    }

    public void setMaxTry(int maxTry) {
        if (maxTry < 0) {
            throw new IllegalArgumentException("maxTry must be non-negative");
        }
        this.maxTry = maxTry;
    }

    public T get() {
        return latestVersion;
    }

    /**
     * Continuously trying to set the updated value to the repo until succeeded. The
     * Objects are versioned that the repo only accept the object of version
     * (e.g. latest version + 1) and set it as the new official version.
     * <p>
     * There is a try-count and when it's reached the max-try value, this operation is terminated and
     * <code>RuntimeException</code> will be thrown. This is to avoid infinite loop.
     *
     * @param _updateFunction
     */
    public void set(Function<T, T> _updateFunction) {
        final int max = maxTry; // Get a snapshot of the value to avoid hacking an infinite loop
        int tryCount = 0;

        while (tryCount++ <= max) {
            T updated = requireNonNull(_updateFunction).apply(latestVersion);

            synchronized (internalLock) {
                V actual = updated.getVersion();
                V expected = nextVersionProducer.apply(latestVersion.getVersion());
                int comparison = versionComparator.compare(expected, actual);

                if (comparison == 0) {
                    // Only accept if the updated version is exactly the next version
                    latestVersion = updated;
                    return;
                } else if (comparison < 0) {
                    // To stop infinite loop if the updated version is greater than the expected next version
                    throw new IllegalArgumentException(format("Setting a updated version of the object " +
                            "requires the next version of exactly %s", expected));
                }
            }
        }

        throw new RuntimeException(format("Reached max retry %d and still unable to receive the exact next version. Given up.", max));
    }
}