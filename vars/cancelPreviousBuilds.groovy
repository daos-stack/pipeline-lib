// vars/cancelPreviousBuilds.groovy

import com.intel.cancelPreviousBuildsInternal

/**
 * cancelPreviousBuilds.groovy
 *
 * cancelPreviousBuilds pipeline step
 *
 */


def call(Map config = [:]) {
  /**
   * Cancel previous builds of the current job
   *
   * @param config Map of parameters passed (none currently)
   * @return Nothing
   */
  try {
    rc = cancelPreviousBuildsTrusted()
  } catch (java.lang.NoSuchMethodError e) {
    println ('Unable to cancel previous builds.')
  }
}
