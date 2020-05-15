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
    rc = cancelPreviousBuildsSystem()
  } catch (java.lang.NoSuchMethodError e) {

    // Rely on Jenkins whitelisting a groovy method.
    try {
      def c = new com.intel.cancelPreviousBuildsInternal()
      return c.cancelPreviousBuildsInternal(config)
    } catch (err) {
      println ('Unable to cancel previous builds.')
    }
  }
}
