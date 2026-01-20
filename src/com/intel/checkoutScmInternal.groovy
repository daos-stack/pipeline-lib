// src/com/intel/checkoutScmInternal.groovy
package com.intel

/* groovylint-disable DuplicateStringLiteral, VariableName */

/**
 * checkoutScmInternal.groovy
 *
 * Simplified Scm checkout routine.
 */

/* groovylint-disable-next-line NoDef, MethodReturnTypeRequired */
def checkoutScmInternal(Map config = [:]) {
  /**
   * Simplified SCM checkout method.
   *
   * @param config Map of parameters passed
   * @return Map of any variables the SCM plugin would set in a
   * Freestyle job.
   *
   * config['scm'] SCM name, defaults to 'GitSCM'.
   * config['url'] Url for SCM repository.  If not specified,
   *  defaults will be used from the scm global variable.
   * config['cleanAfterCheckout'] True for clean after checkout.
   * config['checkoutDir'] Optional directory to checkout into.
   * config['branch'] Optional branch to checkout, defaults to master.
   * config['withSubmodules'] Optional checkout submodules if true.
   * config['credentialsId'] Optional credentials ID.
   */

    String scm_name = config['scm'] ?: 'GitSCM'
    // scm.branches contains a branch name, not a commit.  That means that
    // on Replay, where you want to re-build an older build, scm will build
    // the branch tip, not the commit being Replayed.
    // Also in the case quick successive commits to a branch, again since
    // scm.branches contains the branch name, by the time the first of some
    // successive commits gets to the point of doing the checkout, the branch
    // tip might be a newer commit than that being built, in which case the
    // build ends up building a subsequent commit, not the one it should be.
    // Use $GIT_COMMIT to resolve this issue, as it should always point at
    // the specific commit, no the branch tip.
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def branches = env.GIT_COMMIT ? [[name: env.GIT_COMMIT ]] : scm.branches

    Map userRemoteConfig
    List userRemoteConfigs
    if (config['url']) {
        userRemoteConfig = [url: config['url']]

        branches = config['branch'] ? [[name: config['branch']]] :
                                      [[name: '*/master']]

        if (config['credentialsId']) {
            userRemoteConfig << [credentialsId: config['credentialsId']]
        }
        userRemoteConfigs = [userRemoteConfig]
    } else {
        userRemoteConfigs = scm.userRemoteConfigs
    }
    Map params = [$class: scm_name,
                  branches: branches,
                  extensions: [[$class: 'CloneOption', noTags: true,
                                reference: '', shallow: false, depth: 100]],
                  submoduleCfg: [],
                  userRemoteConfigs: userRemoteConfigs]
    if (config['pruneStaleBranch']) {
        params['extensions'].add(pruneStaleBranch())
    }
    if (config['cleanAfterCheckout']) {
        params['extensions'].add([$class: 'cleanAfterCheckout'])
    }

    if (config['checkoutDir']) {
        params['extensions'].add(
          [$class: 'RelativeTargetDirectory',
           relativeTargetDir: config['checkoutDir']])
    }

    if (config['withSubmodules']) {
        params['extensions'].add(
          [$class: 'SubmoduleOption',
           disableSubmodules: false,
           parentCredentials: true,
           recursiveSubmodules: true,
           reference: '',
           trackingSubmodules: false])
    } else {
        params['extensions'].add(
          [$class: 'SubmoduleOption',
           disableSubmodules: true,
           parentCredentials: true,
           recursiveSubmodules: false,
           reference: '',
           trackingSubmodules: false])
    }

    return checkout(params)
}
