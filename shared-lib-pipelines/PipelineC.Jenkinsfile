// Application Pipeline C — mybuild(<url>, 'Build')
@Library('shared-lib') _

pipeline {
    agent any
    tools {
        jdk 'myjava'
        maven 'mymaven'
    }
    stages {
        stage('Checkout and Build') {
            steps {
                mybuild('https://github.com/zestabhijeet/SonarQubeCoverageJava.git', 'Build')
            }
        }
    }
}
