/* groovylint-disable DuplicateStringLiteral, ParameterName, VariableName */
// vars/cachedCommitPragmaV2.groovy

/**
 * cachedCommitPragmaV2.groovy
 *
 * cachedCommitPragmaV2 variable
 */

/**
 * Method to cache a commit pragma value
 */

import groovy.transform.Field

/* groovylint-disable-next-line CompileStatic */
@Field static Map commit_pragma_cache = [:]

String call(Map config = [:]) {
    echo "DEBUG: cachedCommitPragmaV2 call(Map config = [:])"
    if (config['clear']) {
        commit_pragma_cache.clear()
        return
    } else if (config['dump']) {
        return commit_pragma_cache
    }

    // convert the map for compat
    return cachedCommitPragma(config['pragma'], config['def_val'])
}

String call(String name, String def_val = null) {
    echo "DEBUG: cachedCommitPragmaV2 call(String name, String def_val = null)"
    String _name = name.toLowerCase()
    String _def_val
    if (_name == "Skip-build")
        return "true"

    if (def_val) {
        _def_val = def_val
    }

    /* groovylint-disable-next-line CouldBeElvis */
    if (!commit_pragma_cache[_name]) {
        commit_pragma_cache[_name] = commitPragma(_name)
    }

    if (commit_pragma_cache[_name]) {
        return commit_pragma_cache[_name]
    } else if (_def_val) {
        return _def_val
    }

    return ''
}
