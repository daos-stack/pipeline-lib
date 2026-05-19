/* groovylint-disable ParameterName */
// vars/envToPragmas.groovy

/**
 * envToPragmas.groovy
 *
 * envToPragmas variable
 */

Map call() {
    if (!env.pragmas) {
        return [:]
    }

    if (env.pragmas instanceof java.util.regex.Matcher) {
        def m = (java.util.regex.Matcher) env.pragmas
        def pragmasString = m.find() ? m.group(0) : m.toString()
        env.pragmas = pragmasString
    }

    // If already a Map, return as-is (normalize keys to lowercase)
    if (env.pragmas instanceof Map) {
        Map m = [:]
        ((Map) env.pragmas).each { k, v ->
            m[k.toString().toLowerCase()] = v
        }
        // ensure test-tag is a list if present
        if (m['test-tag'] != null && !(m['test-tag'] instanceof List)) {
            m['test-tag'] = (m['test-tag'] instanceof String) ? [m['test-tag']] : [m['test-tag'].toString()]
        }
        return m
    }

    def pragmas = env.pragmas
    String s = (pragmas instanceof String) ? pragmas.trim() :
                (pragmas instanceof List)   ? pragmas.collect { it?.toString()?.trim() }.join(',') :
                pragmas?.toString()?.trim()

    // Try several common serialized forms and parse them robustly

    // 1) Form like: [test-tag:[line1, line2, line3]]
    def m1 = s =~ /^\s*

\[([^\:

\[\]

]+)\:\s*

\[([^\]

]*)\]

\]

\s*$/
    if (m1.matches()) {
        def rawKey = m1[0][1]
        String key = (rawKey instanceof String) ? rawKey.trim().toLowerCase() : rawKey?.toString()?.trim()?.toLowerCase()
        def rawInner = m1[0][2]
        String inner = (rawInner instanceof String) ? rawInner.trim() : rawInner?.toString()?.trim()
        List values = inner ? inner.split(/\s*,\s*/).collect { it.trim() } : []
        return [(key): values]
    }

    // 2) Form like: {test-tag=[line1, line2], other=val}
    //    or {test-tag:[line1, line2], other:val}
    // Normalize braces and separators, then parse top-level entries
    try {
        String body = s ?: ''
        if (body.startsWith('{') && body.endsWith('}')) {
            body = (body.length() > 2) ? body[1..-2].trim() : ''
        } else if (body.startsWith('[') && body.endsWith(']')) {
            // already handled the nested-list case above; fall back to inner content
            body = (body.length() > 2) ? body[1..-2].trim() : ''
        }

        Map pragmasMap = [:]
        // split top-level entries on comma that are not inside brackets/braces
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
            // accept both key=value, key: value, key=[...], key:[...]
            def kv = entry.split(/[:=]/, 2)
            if (kv.length == 0) return
            String key = kv[0]?.toString()?.trim()?.toLowerCase()
            String rawVal = (kv.length > 1) ? kv[1]?.toString()?.trim() : ''

            if (rawVal) {
                String rv = rawVal
                // strip surrounding whitespace
                rv = rv.trim()
                if (rv.startsWith('[') && rv.endsWith(']')) {
                    String inner = (rv.length() > 2) ? rv[1..-2].trim() : ''
                    List values = []
                    if (inner) {
                        def arr = inner.split(/\s*,\s*/) as List
                        values = arr.collect { it?.toString()?.trim() }
                    }
                    pragmasMap[key] = values
                } else if (rv.startsWith('{') && rv.endsWith('}')) {
                    // nested map — keep as string representation (trimmed)
                    pragmasMap[key] = rv
                } else {
                    // plain scalar (strip optional surrounding quotes)
                    String scalar = rv
                    if ((scalar.startsWith('"') && scalar.endsWith('"')) || (scalar.startsWith("'") && scalar.endsWith("'"))) {
                        scalar = scalar[1..-2]
                    }
                    pragmasMap[key] = scalar
                }
            } else {
                pragmasMap[key] = ''
            }
        }

        // Ensure test-tag is a list
        if (pragmasMap['test-tag'] != null && !(pragmasMap['test-tag'] instanceof List)) {
            pragmasMap['test-tag'] = [pragmasMap['test-tag'].toString()]
        }

        return pragmasMap
    } catch (Exception e) {
        // fallback: return empty map to avoid NPEs downstream
        return [:]
    }
}
