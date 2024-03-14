// vars/docOnlyChange.groovy

/**
 * docOnlyChange.groovy
 *
 * docOnlyChange variable
 */

/**
 * Method to return true/false if a change is for documentation only^
 */

boolean call(String target_branch) {
    if (cachedCommitPragma('Doc-only').toLowerCase() == 'true') {
        return true
    }
    if (cachedCommitPragma('Doc-only').toLowerCase() == 'false' ||
        !fileExists('ci/doc_only_change.sh')) {
        return false
    }

    return sh(label: "[${env.STAGE_NAME}] Determine if doc-only change",
              script: ["CHANGE_ID=${env.CHANGE_ID}",
                       "TARGET_BRANCH=${target_branch}",
                       'ci/doc_only_change.sh'].join(' '),
              returnStatus: true) == 1
}