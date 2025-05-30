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
    if (cachedCommitPragma('Doc-only').toLowerCase() == 'false') {
        return false
    }
    String script
    if (fileExists('ci/doc_only_change.sh')) {
        script = 'CHANGE_ID=' + env.CHANGE_ID +
                 ' TARGET_BRANCH=' + target_branch +
                 ' ci/doc_only_change.sh'
    } else {
        script = 'TARGET_BRANCH=' + target_branch + '''
                  set -uex
                  if ! git fetch origin ${TARGET_BRANCH}; then
                      echo "Hrm.  Got an error fetching the target branch"
                      exit 0
                  fi
                  if ! git diff --no-commit-id --name-only origin/${TARGET_BRANCH} HEAD | grep -q -e ".*"; then
                    echo "Empty commit diff"
                    exit 0
                  fi
                  git diff --no-commit-id --name-only origin/${TARGET_BRANCH} HEAD |
                     grep -v -e "^docs/" -e "\\.md$" -e "^.*LICENSE.*$"'''
    }
    return sh(label: "Determine if doc-only change",
              script: script,
              returnStatus: true) == 1
}
