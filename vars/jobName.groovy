// vars/jobName.groovy

/**
 * jobName.groovy
 *
 * jobName variable
 */

/**
 * Method to return the name of the running job
 */

def call() {

  def jobNameParts = env.JOB_NAME.tokenize('/') as String[]
  jobNameParts.length < 2 ? env.JOB_NAME : jobNameParts[jobNameParts.length - 2]

}
