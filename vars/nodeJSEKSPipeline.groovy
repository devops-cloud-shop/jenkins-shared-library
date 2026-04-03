def call(Map configMap){
    pipeline {
        //This is pre-build section//
    agent { 
        node {
            label 'AGENT-1'
        }
    }
    tools {
        nodejs 'NodeJS-20'
    }
    environment {
        PROJECT = configMap.get("PROJECT")
        COMPONENT =  configMap.get("COMPONENT")
        ENVIRONMENT = "dev"
        APP_VERSION = "1.0.0"
        ACC_ID = "600442391603"
        REGION = "us-east-1"
    }
    options{
        timeout(time: 30, unit: 'MINUTES') 
        disableConcurrentBuilds()
    }
    // parameters {
    //     string(name: 'App_Version', description: 'Which app version you want to deploy')
    //     choice(name: 'deploy_to', choices: ['dev', 'qa', 'prod'], description: 'Pick something')
    // }
    //Build section//
    stages{
        stage('Read Version'){
            steps{
                script{
                    def packageJSON = readJSON file: 'package.json'
                    APP_VERSION = packageJSON.version
                    echo "App Version: ${APP_VERSION}"
                }
            }

        }
        stage('Install Dependencies'){
            steps{
                script{
                    sh """
                        npm install
                    """    
                }
            }
        }
        stage('Unit Test'){
            steps{
                script{
                    sh """
                        echo "unit test"
                    """
                }
            }
        }
        //Here you need to select scanner tool and send analysis to server//
        // stage('sonar_scan'){
        //     environment{
        //         def scannerHome = tool 'sonar-5.0'
        //     }
        //     steps{
        //         script{
        //             withSonarQubeEnv('sonar-server'){
        //                 sh "${scannerHome}/bin/sonar-scanner"
        //             }

        //         }
        //     }
        // }
        // stage('Quality Gates'){
        //     steps{
        //         timeout(time: 1, unit: 'HOURS'){
        //             //wait for quality gate status, 
        //             //waitForQualityGate abortPipeline: true will fail the jenkins job if quality gate fails
        //             waitForQualityGate abortPipeline: true
        //         }
        //     }
        // }

        stage('Dependabot Security Gate') {
            when {
                expression { false }
            }
            environment {
                GITHUB_OWNER = 'devops-cloud-shop'
                GITHUB_REPO  = 'catalogue'
                GITHUB_API   = 'https://api.github.com'
                GITHUB_TOKEN = credentials('GITHUB_TOKEN')
            }

            steps {
                script{
                    /* Use sh """ when you want to use Groovy variables inside the shell.
                    Use sh ''' when you want the script to be treated as pure shell. */
                    sh '''
                    echo "Fetching Dependabot alerts..."

                    response=$(curl -s \
                        -H "Authorization: token ${GITHUB_TOKEN}" \
                        -H "Accept: application/vnd.github+json" \
                        "${GITHUB_API}/repos/${GITHUB_OWNER}/${GITHUB_REPO}/dependabot/alerts?per_page=100")

                    echo "${response}" > dependabot_alerts.json

                    high_critical_open_count=$(echo "${response}" | jq '[.[] 
                        | select(
                            .state == "open"
                            and (.security_advisory.severity == "high"
                                or .security_advisory.severity == "critical")
                        )
                    ] | length')

                    echo "Open HIGH/CRITICAL Dependabot alerts: ${high_critical_open_count}"

                    if [ "${high_critical_open_count}" -gt 0 ]; then
                        echo "❌ Blocking pipeline due to OPEN HIGH/CRITICAL Dependabot alerts"
                        echo "Affected dependencies:"
                        echo "$response" | jq '.[] 
                        | select(.state=="open" 
                        and (.security_advisory.severity=="high" 
                        or .security_advisory.severity=="critical"))
                        | {dependency: .dependency.package.name, severity: .security_advisory.severity, advisory: .security_advisory.summary}'
                        exit 1
                    else
                        echo "✅ No OPEN HIGH/CRITICAL Dependabot alerts found"
                    fi
                    '''
                    
                }
            }
        }

        stage('Build Image'){
            steps{
                script{
                    withAWS(region: 'us-east-1', credentials: 'aws-creds'){
                        sh """
                        aws ecr get-login-password --region us-east-1 | /usr/bin/docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                        docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${APP_VERSION} .
                        docker images
                        docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${APP_VERSION}
                        """
                    }
                }
            }
        }
        /* stage('Trivy Scan'){
                steps {
                    script{
                        sh """
                            trivy image \
                            --scanners vuln \
                            --severity HIGH,CRITICAL,MEDIUM \
                            --pkg-types os \
                            --exit-code 1 \
                            --format table \
                            ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                        """
                    }
                }
            } */ 
    }  
//this is post build section//
        post{
            always{
                echo 'I will always say Hello again'
                cleanWs()
            }
            success{
                echo 'I will run if success'
            }
            failure{
                echo 'I will fail if failure'
            }
            aborted{
                echo 'pipeline is aborted'
            }
        }
    }
}
