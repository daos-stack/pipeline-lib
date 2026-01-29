// vars/checkoutScm.groovy

import com.intel.checkoutScmInternal

/**
 * checkoutScm.groovy
 *
 * checkoutScm pipeline step
 */

Map call(Map config = [:]) {
  /**
   * Simplified SCM checkout method.
   *
   * @param config Map of parameters passed
   * @return Map of any variables the SCM plugin would set in a
   * Freestyle job.
   *
   * config['scm'] SCM name, defaults to 'GitSCM'.
   * config['url'] Url for Git repository.  If not specified,
   *  defaults will be used from the scm global variable.
   * config['cleanAfterCheckout'] True for clean after checkout.
   * config['checkoutDir'] Optional directory to checkout into.
   * config['branch'] Optional branch to checkout, defaults to master.
   * config['withSubmodules'] Optional checkout submodules if true.
   * config['credentialsId'] Optional credentials ID.
   */
    // Jenkins requires us to use 'def' here.
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def c = new checkoutScmInternal()
    return c.checkoutScmInternal(config)
}
