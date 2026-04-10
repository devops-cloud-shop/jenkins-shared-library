def call ( Map configMap ){
    pipeline {
    // These are pre-build sections
    agent {
        node {
            label 'AGENT-1'
        }
    }
    environment {
        PROJECT = configMap.get("PROJECT")
        COMPONENT = configMap.get("COMPONENT")
        APP_VERSION = configMap.get("APP_VERSION")
        ACC_ID = "600442391603"
        REGION = "us-east-1"
        DEPLOY_TO = configMap.get("DEPLOY_TO")
    }
    options {
        timeout(time: 30, unit: 'MINUTES') 
        disableConcurrentBuilds()
    }
    // parameters {
    //     string(name: 'APP_VERSION', description: 'Which app version you want to deploy')
    //     choice(name: 'deploy_to', choices: ['dev', 'qa', 'prod'], description: 'Pick something')
    // }
    // This is build section
    stages {
        
        stage('Deploy') {
            steps {
                script{
                    withAWS(region:'us-east-1',credentials:'aws-creds') {
                        sh """
                            aws eks update-kubeconfig --region ${REGION} --name ${PROJECT}-${DEPLOY_TO}
                            kubectl get nodes
                            sed "s/IMAGE_VERSION/${APP_VERSION}/g" values.yaml
                            helm upgrade --install ${COMPONENT} -f values-${DEPLOY_TO}.yaml -n ${PROJECT} --atomic --wait --timeout=5m .
                        """
                    }
                }
            }
        }
        
    }

        

    post{
        always{
            echo 'I will always say Hello again!'
            cleanWs()
        }
        success {
            echo 'I will run if success'
        }
        failure {
            echo 'I will run if failure'
        }
        aborted {
            echo 'pipeline is aborted'
        }
    }
}
}
