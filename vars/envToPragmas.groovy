/* groovylint-disable ParameterName */
// vars/envToPragmas.groovy

/**
 * envToPragmas.groovy
 *
 * envToPragmas variable
 */

Map call() {
    // Work on a local copy to avoid storing non-serializable objects in env
    if (!env.pragmas) {
        return [:]
    }

    def raw = env.pragmas

    // If someone accidentally put a Matcher into env.pragmas, convert it immediately
    if (raw instanceof java.util.regex.Matcher) {
        raw = raw.find() ? raw.group(0) : raw.toString()
    }

    // If already a Map, normalize keys and ensure list types for test-tag
    if (raw instanceof Map) {
        Map m = [:]
        ((Map) raw).each { k, v ->
            m[k.toString().toLowerCase()] = v
        }
        if (m['test-tag'] != null && !(m['test-tag'] instanceof List)) {
            m['test-tag'] = safeUtils.ensureList(m['test-tag'])
        }
        return m
    }

    // Convert raw to a trimmed String representation for parsing
    String s = (raw instanceof String) ? raw.trim() :
               (raw instanceof List)   ? raw.collect { it?.toString()?.trim() }.join(',') :
               raw?.toString()?.trim()

    if (!s) {
        return [:]
    }

    try {
        // 1) Form like: [test-tag:[line1, line2, line3]]
        def m1 = (s =~ /^\s*
\[([^\:
\[\]
]+)\s*:\s*
\[([^\]
]*)\]
\s*\]
$/)

        if (m1.matches()) {
            def rawKey = m1[0][1]
            String key = rawKey?.toString()?.trim()?.toLowerCase() ?: ''
            def innerVal = m1[0][2]
            String inner = innerVal?.toString()?.trim() ?: ''
            List values = inner ? inner.split(/\s*,\s*/).collect { it?.toString()?.trim() } : []
            return [(key): values]
        }

        // 2) Generic map-like forms: {k=v, k2=[a,b], k3:val}
        String body = s
        if ((body.startsWith('{') && body.endsWith('}')) || (body.startsWith('[') && body.endsWith(']'))) {
            body = (body.length() > 2) ? body[1..-2].trim() : ''
        }

        Map pragmasMap = [:]
        def parts = []
        int depth = 0
        StringBuilder cur = new StringBuilder()
        body.each { ch ->
            if (ch == '[' || ch == '{') { depth++ }
            if (ch == ']' || ch == '}') { depth-- }
            if (ch == ',' && depth == 0) {
                parts << cur.toString()
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
        }
        if (cur.length() > 0) { parts << cur.toString() }

        parts.each { entry ->
            if (!entry) return
            def kv = entry.split(/[:=]/, 2)
            if (kv.length == 0) return
            String key = kv[0]?.toString()?.trim()?.toLowerCase() ?: ''
            String rawVal = (kv.length > 1) ? kv[1] : ''

            if (rawVal instanceof List) {
                pragmasMap[key] = rawVal.collect { it?.toString()?.trim() }
            } else {
                String rawValStr = rawVal?.toString()?.trim() ?: ''
                String rv = rawValStr
                if (rv.startsWith('[') && rv.endsWith(']')) {
                    String inner = (rv.length() > 2) ? rv[1..-2].trim() : ''
                    List values = inner ? inner.split(/\s*,\s*/).collect { it?.toString()?.trim() } : []
                    pragmasMap[key] = values
                } else {
                    String scalar = rv
                    if ((scalar.startsWith('"') && scalar.endsWith('"')) || (scalar.startsWith("'") && scalar.endsWith("'"))) {
                        scalar = scalar[1..-2]
                    }
                    pragmasMap[key] = scalar.trim()
                }
            }
        }

        // Ensure test-tag is a list
        if (pragmasMap['test-tag'] != null && !(pragmasMap['test-tag'] instanceof List)) {
            pragmasMap['test-tag'] = safeUtils.ensureList(pragmasMap['test-tag'])
        }

        return pragmasMap
    } catch (Exception e) {
        return [:]
    }
}
