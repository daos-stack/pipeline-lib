// vars/parallelBuild.groovy

/**
 * parallelBuild.groovy
 *
 * parallelBuild variable
 */

/**
 * Method to return true/false if a parallel build is desired
 */

boolean call() {
    // defaults to false
    // true if Quick-build: true unless Parallel-build: false
    String pb = cachedCommitPragma('Parallel-build').toLowerCase()
    if (pb == "true" ||
        (quickBuild() && pb != "false")) {
        return true
    }

    return false
}