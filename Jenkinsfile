#!/usr/bin/env groovy

@Library(value=["pipeline-lib@corci-887b-bug",
                "trusted-pipeline-lib@corci-887b"]) _

pipeline {
    agent none
    stages {
        stage('Build') {
            matrix {
                axes {
                    axis {
                        name 'COLOR'
                        values 'red', 'green', 'blue'
                    }
                    axis {
                        name 'SHAPE'
                        values 'none','square', 'circle', 'triangle'
                    }
                }
                excludes {
                    exclude {
                        axis {
                            name 'SHAPE'
                            values 'triangle'
                        }
                        axis {
                            name 'COLOR'
                            values 'blue'
                        }
                    } //exclude
                } // excludes
                stages {
                    stage('Build_1') {
/*                        when {
                            beforeAgent true
                            allOf {
                                environment name: 'SHAPE',
                                            value: 'none'
                                anyOf {
                                    environment name: 'COLOR',
                                                value: 'red'
                                    environment name: 'COLOR',
                                                value: 'green'
                                } // anyOF
                            } // allOf
                        } // when
*/
                        agent any
                          steps {
                            hack_step skip_shapes: ['none'],
                                      do_colors: ['red', 'green'],
                                      label: env.STAGE_NAME,
                                      script: '''sleep 30
                                                 if [ "${COLOR}" == 'red' ]; then
                                                   exit 1
                                                 fi'''
                        } // steps
                    } // stage('Build_1')
                } // stages
            } // matrix
        } // stage('Build')
    } // stages
} //pipeline
