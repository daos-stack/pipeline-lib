// vars/buildRpmPost.groovy

  /**
   * buildRpmPost step method
   *
   * @param config Map of parameters passed
   *
   * config['condition']           Name of the post step to run. 
   *                               Rrequired to be one of
   *                               'success', 'unstable', 'failure',
   *                               'unsuccessful', or 'cleanup'.
   *
   * config['context']             Context name for SCM to identify the
   *                               specific stage to update status for.
   *                               Default is 'build/' + env.STAGE_NAME.
   *  Important:
   *     The SCM status checking for passing may expect a specific name.
   *
   *     Matrix stages must override this setting to include matrix axes
   *     names to ensure a unique name is generated.
   *
   *     Or the default name has to be changed in a way that is compatible
   *     with a future Matrix implementation.
   *
   * config['description']         Description to report for SCM status.
   *                               Default env.STAGE_NAME.
   *
   * config['flow_name']           Flow name to use for looking up the log URL
   *                               for reporting to the SCM.
   *                               Default is to use config['context'] or its
   *                               default value.
   *
   * config['ignore_failure']      Whether a FAILURE result should post a
   *                               failed step.  Default false.
   *
   * config['product']             Product name for repository.
   *                               Default 'daos'.
   *
   * config['success_script']      Script to run on successful build.
   *                               Default 'ci/rpm/build_success.sh'.
   *
   * config['target']              Target distribution, such as 'centos7',
   *                               'leap15'.
   *                               Default based on parsing environment
   *                               variables for the stage.
   *
   * config['tech']                Tech value for repository storage.
   *                               Default based on parsing target.
   *
   * config['unsuccessful_script'] Script to run if build is not successful.
   *                               Default 'ci/rpm/build_unsuccessful.sh'
   */

def call(Map config = [:]) {

  def context = config.get('context', 'build/' + env.STAGE_NAME)
  def description = config.get('description', env.STAGE_NAME)
  def ignore_failure = config.get('ignore_failure', false)

  Map stage_info = parseStageInfo(config)

  def env_vars = ' TARGET=' + stage_info['target']

  if (config['condition'] == 'success') {
    
    def success_script = config.get('success_script',
                                    'ci/rpm/build_success.sh')
    sh label: 'Build Log',
       script: "${env_vars} " + success_script

    stash name: stage_info['target'] + '-rpm-version',
          includes: stage_info['target'] + '-rpm-version'

    def product = config.get('product', 'daos')
    publishToRepository product: product,
                        format: 'yum',
                        maturity: 'stable',
                        tech: stage_info['target'],
                        repo_dir: 'artifacts/' + stage_info['target']
  }

  if ((config['condition'] == 'success') ||
      (config['condition'] == 'unstable') ||
      (config['condition'] == 'failure')) {
    stepResult name: description,
               context: context,
               flow_name: config.get('flow_name', context),
               result: config['condition'].toUpperCase(),
               ignore_failure: ignore_failure
    return
  }

  if (config['condition'] == 'unsuccessful') {
    def unsuccessful_script = config.get('unsuccessful_script',
                                         'ci/rpm/build_unsuccessful.sh')

    sh label: 'Build Log',
       script: "${env_vars} " + unsuccessful_script
    return
  }

  if (config['condition'] == 'cleanup') {
    archiveArtifacts artifacts: "artifacts/" + stage_info['target'] + '/**'
    return
  }
  error 'Invalid value for condition parameter'
}
