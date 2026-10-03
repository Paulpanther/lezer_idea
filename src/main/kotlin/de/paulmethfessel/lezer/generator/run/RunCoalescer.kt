package de.paulmethfessel.lezer.generator.run

/**
 * Runs at most one task per key at a time. A request while the key's task is running doesn't start another one, but
 * makes it run once more after it finished, so the last request is always handled.
 */
class RunCoalescer<K : Any> {
    /** The running keys, and whether they were requested again meanwhile. */
    private val rerun = HashMap<K, Boolean>()

    /** Whether the caller should start the task now, otherwise it is rerun after the running one. */
    @Synchronized
    fun request(key: K): Boolean {
        if (key in rerun) {
            rerun[key] = true
            return false
        }
        rerun[key] = false
        return true
    }

    /** Called when the task finished, returns whether the caller should run it again. */
    @Synchronized
    fun finished(key: K): Boolean {
        if (rerun[key] == true) {
            rerun[key] = false
            return true
        }
        rerun.remove(key)
        return false
    }
}
