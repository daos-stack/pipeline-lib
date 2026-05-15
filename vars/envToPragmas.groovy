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

    String s = env.pragmas.toString().trim()

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
        String key = m1[0][1].trim().toLowerCase()
        String inner = m1[0][2].trim()
        List values = inner ? inner.split(/\s*,\s*/).collect { it.trim() } : []
        return [(key): values]
    }

    // 2) Form like: {test-tag=[line1, line2], other=val}
    //    or {test-tag:[line1, line2], other:val}
    // Normalize braces and separators, then parse top-level entries
    try {
        String body = s
        if (body.startsWith('{') && body.endsWith('}')) {
            body = body[1..-2].trim()
        } else if (body.startsWith('[') && body.endsWith(']')) {
            // already handled the nested-list case above; fall back to inner content
            body = body[1..-2].trim()
        }

        Map pragmas = [:]
        // split top-level entries on comma that are not inside brackets
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
            String key = kv[0].trim().toLowerCase()
            String rawVal = (kv.length > 1) ? kv[1].trim() : ''
            // strip surrounding braces/brackets
            if (rawVal.startsWith('[') && rawVal.endsWith(']')) {
                String inner = rawVal[1..-2].trim()
                List vals = inner ? inner.split(/\s*,\s*/).collect { it.trim() } : []
                pragmas[key] = vals
            } else if (rawVal.startsWith('{') && rawVal.endsWith('}')) {
                // nested map — keep as string representation
                pragmas[key] = rawVal
            } else {
                // plain scalar
                pragmas[key] = rawVal
            }
        }

        // Ensure test-tag is a list
        if (pragmas['test-tag'] != null && !(pragmas['test-tag'] instanceof List)) {
            pragmas['test-tag'] = [pragmas['test-tag'].toString()]
        }

        return pragmas
    } catch (Exception e) {
        // fallback: return empty map to avoid NPEs downstream
        return [:]
    }
}
