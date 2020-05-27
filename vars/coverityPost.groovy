// vars/coverityPost.groovy

  /**
   * coverityPost step method
   *
   * @param config Map of parameters passed
   *
   * config['arch']                CPU architecture.  Default is blank.
   *
   * config['condition']           Name of the post step to run. 
   *                               Rrequired to be one of
   *                               'success', or 'unsuccessful'.
   *
   * config['success_script']      Script to run on successful build.
   *                               Default 'ci/coverity_success.sh'.
   *
   * config['unsuccessful_script'] Script to run if build is not successful.
   *                               Default 'ci/coverity_unsuccessful.sh'
   *
   * config['coverity_tarball']    Tarball to archive for coverity.
   *                               default: 'coverity/*.tgz'
   */

def call(Map config = [:]) {

  def arch = 'arch='
  if (config['arch']) {
    arch='arch=' + config['arch'] + ' '
  }

  if (config['condition'] == 'success') {

    def success_script = config.get('success_script',
                                    'ci/coverity_success.sh')
    sh label: 'Coverity success',
       script: arch + success_script

    // Coverity actually being built is controlled by the Jenkins
    // configuration, so there may not be artifacts.
    def coverity_tarball = config.get('coverity_tarball',
                                      'coverity/*.tgz')
    archiveArtifacts artifacts: coverity_tarball,
                     allowEmptyArchive: true
    return
  }

  if (config['condition'] == 'unsuccessful') {
    def unsuccessful_script = config.get('unsuccessful_script',
                                         'ci/coverity_unsuccessful.sh')

    sh label: 'Coverity Unsuccessful',
       script: arch + unsuccessful_script

    archiveArtifacts artifacts: 'coverity/*log*',
                    allowEmptyArchive: true
    return
  }

  error 'Invalid value for condition parameter'
}
