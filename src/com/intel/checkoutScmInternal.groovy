// src/com/intel/checkoutScmInternal.groovy

package com.intel

/**
 * checkoutScmInternal.groovy
 *
 * Simplified Scm checkout routine.
 */


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

  scm_name = 'GitSCM'
  if (config['scm']) {
    scm_name = config['scm']
  }
  branches = scm.branches
  if (config['url']) {
    userRemoteConfig = [url: config['url']]
    if (config['branch']) {
      branches = [[name: config['branch']]]
    } else {
      branches = [[name: '*/master']]
    }
    if (config['credentialsId']) {
      userRemoteConfig.add([credentialsId: config['credentialsId']])
    }
    UserRemoteConfigs = [userRemoteConfig]
  } else {
    userRemoteConfigs = scm.userRemoteConfigs
  }
  params = [$class: scm_name,
            branches: branches,
            extensions: [],
            submoduleCfg: [],
            userRemoteConfigs: userRemoteConfigs]
  if (config['CleanAfterCheckout']) {
    params['extensions'].add([$class: 'CleanCheckout'])
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
  }

  return checkout(params)
}
