// src/com/intel/cancelPreviousBuildsInternal.groovy

package com.intel

/**
 * cancelPreviousBuildsInternal.groovy
 *
 * Routine to cancel old builds in progress
 */


def cancelPreviousBuildsInternal(Map config = [:]) {
  /**
   * Cancel old builds method.
   *
   * @param config Map of parameters passed (currently none)
   * @return Nothing
   */

  def jobName = env.JOB_NAME
  def buildNumber = env.BUILD_NUMBER.toInteger()
  /* Get job name */
  def currentJob = Jenkins.instance.getItemByFullName(jobName)

  /* Iterating over the builds for specific job */
  for (def build : currentJob.builds) {
    /* If there is a build that is currently running and it's not current build */
    if (build.isBuilding() && build.number.toInteger() < buildNumber) {
      print "Stopping currently running build #${build.number}"
      /* Than stopping it */
      build.doStop()
    }
  }

}
