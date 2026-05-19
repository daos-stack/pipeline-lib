/*
 * Global helper available to pipeline scripts (placed in vars so no import needed).
 * Exposes ensureList and safeTrim to normalize values safely.
 */

def call() { return this }

/**
 * Ensure the value is returned as a List<String>.
 */
def ensureList(def v) {
    if (v == null) return []
    if (v instanceof List) {
        return v.collect { it == null ? '' : it.toString().trim() }
    }
    if (v instanceof String) {
        String s = v.trim()
        if (s == '') return []
        if (s.contains(',')) {
            return s.split(/\s*,\s*/).collect { it.trim() }
        }
        return [s]
    }
    return [v.toString().trim()]
}

/**
 * Return a trimmed String if input is String-like, otherwise null.
 */
def safeTrim(def v) {
    if (v == null) return null
    if (v instanceof String) return v.trim()
    return v.toString().trim()
}
