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
        return sh(label: "Determine if doc-only change (manual mode)",
                  script: "set -uex" + \
                    "if ! git fetch origin ${target_branch}; then" + \
                        "echo 'Hrm.  Got an error fetching the target branch'" + \
                        "exit 0" + \
                    "fi" + \
                    "if ! merge_base=\$(git merge-base FETCH_HEAD HEAD); then" + \
                        "echo 'Hrm.  Got an error finding the merge base'" + \
                        "exit 0" + \
                    "fi" + \
                    "git diff --no-commit-id --name-only \$merge_base HEAD | " +\
                       "grep -v -e '^docs/' -e '\.md$' -e '(?i)^.*LICENSE.*$'",
                  returnStatus: true) == 1
                }

    return sh(label: "Determine if doc-only change",
              script: ["CHANGE_ID=${env.CHANGE_ID}",
                       "TARGET_BRANCH=${target_branch}",
                       'ci/doc_only_change.sh'].join(' '),
              returnStatus: true) == 1
}