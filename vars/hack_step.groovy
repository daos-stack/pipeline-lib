// vars/hackstep.groovy

// import static hudson.model.Result.NOT_BUILT

/**
 * run.groovy
 *
 * Wrapper for junit step to allow for skipped tests.
 *
 */

def call(Map config = [:]) {
    println "shape = ${env.SHAPE}, Color = ${env.COLOR}"
    boolean skip_me = false
    config['skip_shapes'].each {
        if (env.SHAPE == it) {
            println "  Skipping ${it}"
            skip_me = true
            return
        }
    }
    boolean good_color = false
    config['do_colors'].each {
        if (env.COLOR == it) {
            println "  Runnining ${it}"
            good_color = true
            return
        }
    }
    println "   skip_me = ${skip_me}, good_color = ${good_color}"
    if (skip_me || !good_color) {
        println "old currentBuild.result = ${currentBuild.result}"
        setStageNotBuilt
        // currentBuild.rawBuild.result = hudson.model.Result.NOT_BUILT
/*        catchError(buildResult: 'SUCCESS', stageResult: 'NOT_BUILT') {
            error "Skipping..." // Force an error so we can set the stageResult
        } */
        println "new currentBuild.result = ${currentBuild.result}"
        return
    }
    //println "   running ${config['script'}"
    sh label: config['label'],
       script: config['script']
    println "   Passed ${env.SHAPE} + ${env.COLOR}"
}
