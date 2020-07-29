// vars/cloverReportPublish.groovy

  /**
   * clover Report Publish step method
   *
   * Consolidiate clover data for publising.
   *
   * @param config Map of parameters passed
   *
   * config['coverage_healthy']    Map of a coverage healthy target.
   *                               Default is:
   *                                 [method:70, conditional:80, statement:80]
   *
   * config['coverage_script']     Script to run to get coverage results
   *                               Default is "ci/bullseye_generate_report.sh"
   *
   * config['coverage_stashes']    list of stashes for coverage reports.
   *                               Required.  Each stash must contain one
   *                               file named test.cov.
   *
   * Config['coverage_website']    Zip file to contain the resulting
   *                               coverage website, if it is a coverage
   *                               build.  Defaults to 'coverage_website.zip'
   *
   * config['ignore_failure']      Ignore test failures.  Default false.
   *
   * config['inst_rpms']           Additional rpms to install.  Optional
   *
   * config['NODELIST']            NODELIST of nodes to run tests on.
   *                               Default env.NODELIST
   *
   * config['node_count']          Count of nodes that will actually be used
   *                               the test.  Default will be based on the
   *                               enviroment variables for the stage.
   *
   * config['stash']               Stash name for the ".build-vars.*" files.
   */

def call(Map config = [:]) {

  Map stage_info = parseStageInfo(config)

  String inst_rpms = config.get('inst_rpms','')

  if (stage_info['java_pkg']) {
    inst_rpms += " ${stage_info['java_pkg']}"
  }

  provisionNodes NODELIST: nodelist,
                 node_count: stage_info['node_count'],
                 distro: stage_info['target'],
                 inst_repos: config.get('inst_repos',''),
                 inst_rpms: inst_rpms

  String coverage_website = config.get('coverage_website',
                                       'coverage_website.zip')

  String url_base = env.JENKINS_URL +
                    'job/daos-stack/job/tools/job/master' +
                    '/lastSuccessfulBuild/artifact/'

  httpRequest url:  url_base + 'bullseyecoverage-linux.tar',
              httpMode: 'GET',
              outputFile: 'bullseye.tar'

  httpRequest url: url_base + 'bullshtml.jar',
              httpMode: 'GET',
              outputFile: 'bullshtml.jar'

  def stashes = []
  if (config['coverage_stashes']) {
    stashes = config['coverage_stashes']
  } else {
    error "No coverage_stashes passed!"
  }

  String target_stash = "${stage_info['target']}-${stage_info['compiler']}"
  if (stage_info['build_type']) {
    target_stash += '-' + stage_info['build_type']
  }
  
  unstash config.get('stash', "${target_stash}-build-vars")

  int stash_cnt=0
  stashes.each {
    unstash it
    stash_cnt++
    String new_name = "test.cov_${stash_cnt}"
    // Need plugin Upgrade
    //fileOperations([fileRenameOperation(source: 'test.cov',
    //                                    destination: new_name)])
    sh label: 'Rename file',
       script: "mv test.cov ${new_name}"
  }

  sh label: 'Create Coverage Report',
     script: config.get('coverage_script', 'ci/bullseye_generate_report.sh')

  def cb_result = currentBuild.result
  step([$class: 'CloverPublisher',
        cloverReportDir: 'test_coverage',
        cloverReportFileName: 'clover.xml',
        healthyTarget: config.get('coverage_healthy',
                                   [methodCoverage: 70,
                                    conditionalCoverage: 80,
                                    statementCoverage: 80])])

  if (cb_result != currentBuild.result) {
    println "The CloverPublisher plugin changed result to " +
            "${currentBuild.result}."
  }

  sh label: 'Create test coverage Tarball',
      script: """rm -f ${coverage_website}
                 if [ -d 'test_coverage' ]; then
                   zip -q -r -9 ${coverage_website} test_coverage
                 fi"""
  archiveArtifacts artifacts: coverage_website,
                   allowEmptyArchive: true

}
