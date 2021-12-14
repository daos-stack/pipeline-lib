// vars/releaseCandidate.groovy

/**
 * releaseCandidate.groovy
 *
 * releaseCandidate variable
 */

/**
 * Method to return true/false if buildin a release candidate
 */

boolean call() {
    return !sh(label: "Determine if building (a PR of) an RC",
               script: "git diff-index --name-only HEAD^ | grep -q TAG && " +
                       "grep -i '[0-9]-rc[0-9]' TAG",
               returnStatus: true)
}