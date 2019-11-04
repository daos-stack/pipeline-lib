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

  if (jobName() == "daos") {
      if (sh(script: "git log | grep \"CORCI-818 build: Skip hardware testing option\""
              label: "Check required commits",
              returnStatus: true) != 0) {
        error "Your PR needs to be merged with latest master before it will build in CI."
      }
  }

  def c = new com.intel.cancelPreviousBuildsInternal()
  return c.cancelPreviousBuildsInternal(config)
}
