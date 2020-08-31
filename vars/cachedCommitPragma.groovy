// vars/cachedCommitPragma.groovy

/**
 * cachedCommitPragma.groovy
 *
 * cachedCommitPragma variable
 */

/**
 * Method to cache a commit pragma value
 */

import groovy.transform.Field

@Field static commit_pragma_cache = [:]

def call(Map config = [:]) {
    // convert the map for compat
    return cachedCommitPragma(config['pragma'], config['def_val'])
}

def call(String name, String def_val = null) {
    name = name.toLowerCase()
    if (def_val) {
        def_val = def_val.toLowerCase()
    }

    if (!commit_pragma_cache[name]) {
        commit_pragma_cache[name] = commitPragma([name, def_val])
    }

    return commit_pragma_cache[name]

}