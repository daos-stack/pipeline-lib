// vars/setupDownstreamTesting.groovy

String test_branch(String target) {
    return 'ci-' + JOB_NAME.replaceAll('/', '-') +
            '-' + target.replaceAll('/', '-')
}

void cleanup(String project, String branch) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding',
                    credentialsId: 'daos_jenkins_project_github_access',
                    usernameVariable: 'GH_USER',
                    passwordVariable: 'GH_PASS']]) {
        sh(label: 'Delete test branch',
           script: 'if ! git push https://$GH_USER:$GH_PASS@github.com/' + project +
                      ' --delete ' + test_branch(branch) + '''; then
                        echo "Error trying to delete branch ''' +
                        test_branch(branch) + '''"
                        git remote -v
                        env
                        exit 1
                    fi''')
    }
}

/**
 * setupDownstreamTesting.groovy
 *
 * Set up a branch to perform dowstream testing on
 */

/* groovylint-disable-next-line NoDef, ParameterName */
void call(String project, String branch, String commit_pragmas = '') {
    // TODO: find out what the / escape is.  I've already tried both %2F and %252F
    //       https://issues.jenkins.io/browse/JENKINS-68857
    // Instead, we will create a branch specifically to test on
    withCredentials([[$class: 'UsernamePasswordMultiBinding',
                    credentialsId: 'daos_jenkins_project_github_access',
                    usernameVariable: 'GH_USER',
                    passwordVariable: 'GH_PASS']]) {
        sh label: 'Create or update test branch',
           /* groovylint-disable-next-line GStringExpressionWithinString */
           script: 'branch_name=' + test_branch(branch) + '''
                   # To override downstream test branch:
                   # i.e. Test-master-branch: foo/bar
                   source_branch=origin/''' +
                   cachedCommitPragma("Test-${branch}-branch",
                                      branch) + '''
                    # dir for checkout since all runs in the matrix share the same
                    # workspace
                    dir="''' + project.split('/')[1] + '-' + branch.replaceAll('/', '-') + '''"
                    #rm -rf "$dir"
                    if cd $dir; then
                        git remote update
                        git fetch origin --prune
                    else
                        git clone https://''' + env.GH_USER + ':' +
                                                env.GH_PASS +
                                      '''@github.com/''' + project + '''.git $dir
                        cd $dir
                    fi
                    # delete the branch if it exists
                    if git checkout $branch_name; then
                        if ! git checkout origin/master || \
                           ! git branch -D $branch_name; then
                          git status
                          git branch -a
                          exit 1
                        fi
                    fi
                    # create the branch
                    if ! git checkout -b $branch_name $source_branch; then
                        echo "Error trying to create branch $branch_name"
                        exit 1
                    fi
                    # remove any triggers so that this test branch doesn't run weekly, etc.
                    if grep triggers Jenkinsfile; then
                        sed -i -e '/triggers/,/^$/d' Jenkinsfile
                        msg=' and remove triggers'
                    fi
                    if [ -n "$CHANGE_BRANCH" ] &&
                       [[ $JOB_NAME = daos-stack/pipeline-lib/* ]]; then
                        pipeline_libs="pipeline-lib@''' +
                        (env.CHANGE_BRANCH ?: '').replaceAll('\\/', '\\\\/') + '''"
                        msg="Update pipeline-lib branch to self${msg:-}"
                    else
                        pipeline_libs="''' + cachedCommitPragma('Test-libs') + '''"
                        msg="Add any requested pipeline-lib PRs${msg:-}"
                    fi
                    if [ -n "$pipeline_libs" ]; then
                        sed -i -e "/\\/\\/@Library/c\\
                            @Library(value='$pipeline_libs') _" Jenkinsfile
                    else
                        msg="Clear any commit pragmas"$'\n\n'"${msg:-}"
                    fi
                    if [ -n "''' + commit_pragmas + '''" ]; then
                        msg+=$'\n\n'"''' + commit_pragmas + '''"
                    fi
                    git commit --allow-empty -m "${msg}" Jenkinsfile
                    git show
                    git push -f origin $branch_name:$branch_name
                    sleep 10'''
    } // withCredentials
    sh label: 'Delete local test branch',
       script: '''dir="''' + project.split('/')[1] + '-' + branch.replaceAll('/', '-') + '''"
                  if ! cd $dir; then
                      echo "$dir does not exist"
                      exit 1
                  fi
                  git checkout origin/master
                  if ! git branch -D ''' + test_branch(branch) + '''; then
                      git status
                      git branch -a
                      exit 1
                  fi'''
} // call
