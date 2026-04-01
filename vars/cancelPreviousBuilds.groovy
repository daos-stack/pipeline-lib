// vars/cancelPreviousBuilds.groovy

/**
 * cancelPreviousBuilds.groovy
 *
 * cancelPreviousBuilds pipeline step
 */

// having an unused parameter actually helps in preventing problems
// caused by typographical errors in the call to this method.
/* groovylint-disable-next-line UnusedMethodParameter */
void call(Map config = [:]) {
  /**
   * Cancel previous builds of the current job
   *
   * @param config Map of parameters passed (none currently)
   * @return Nothing
   */
    try {
        cancelPreviousBuildsTrusted()
    } catch (java.lang.NoSuchMethodError e) {
        println('Unable to cancel previous builds.')
    }
}
