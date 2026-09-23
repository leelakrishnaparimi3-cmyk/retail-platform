pipeline {

    agent any

    parameters {
        choice(
            name: 'DEPLOYMENT_ACTION',
            choices: ['DEPLOY', 'ROLLBACK'],
            description: 'Choose whether to deploy a release or rollback.'
        )

        choice(
            name: 'ENVIRONMENT',
            choices: ['UAT', 'PRODUCTION'],
            description: 'Target deployment environment.'
        )

        string(
            name: 'VERSION',
            defaultValue: '4.2.1',
            description: 'Release version, for example 4.2.1'
        )

        choice(
            name: 'CONFIRM_PROD',
            choices: ['NO', 'YES'],
            description: 'Must be YES for PRODUCTION deployment.'
        )
    }
tools {
    maven 'Maven-3.9.16'
}
    environment {
        APP_NAME = 'retail-app'
        PROD_CONTAINER = 'retail-app-prod'
        UAT_CONTAINER = 'retail-app-uat'
        DOCKER_NETWORK = 'retail-network'
        PROD_PORT = '8081'
        UAT_PORT = '8082'
    }

    stages {

        stage('Validate Parameters') {
            steps {
                script {
                    echo "========================================"
                    echo "DEPLOYMENT ACTION : ${params.DEPLOYMENT_ACTION}"
                    echo "ENVIRONMENT        : ${params.ENVIRONMENT}"
                    echo "VERSION            : ${params.VERSION}"
                    echo "CONFIRM_PROD       : ${params.CONFIRM_PROD}"
                    echo "========================================"

                    if (!(params.VERSION ==~ /^\d+\.\d+\.\d+$/)) {
                        error "VERSION must use format X.Y.Z, for example 4.2.1"
                    }

                    if (
                        params.ENVIRONMENT == 'PRODUCTION' &&
                        params.CONFIRM_PROD != 'YES'
                    ) {
                        error "PRODUCTION deployment blocked because CONFIRM_PROD is not YES."
                    }

                    env.RELEASE_TAG = "v${params.VERSION}"

                    if (params.ENVIRONMENT == 'PRODUCTION') {
                        env.CONTAINER_NAME = env.PROD_CONTAINER
                        env.HOST_PORT = env.PROD_PORT
                    } else {
                        env.CONTAINER_NAME = env.UAT_CONTAINER
                        env.HOST_PORT = env.UAT_PORT
                    }

                    echo "Selected Git tag: ${env.RELEASE_TAG}"
                    echo "Selected container: ${env.CONTAINER_NAME}"
                    echo "Selected host port: ${env.HOST_PORT}"
                }
            }
        }

        stage('Checkout Repository') {
            steps {
                checkout scm

                bat '''
                    git fetch --tags --force
                    echo.
                    echo Available release tags:
                    git tag
                '''
            }
        }

        stage('Validate Release Tag') {
            steps {
                script {
                    def tagStatus = bat(
                        returnStatus: true,
                        script: 'git rev-parse --verify "refs/tags/%RELEASE_TAG%" >nul 2>&1'
                    )

                    if (tagStatus != 0) {
                        error "Git tag ${env.RELEASE_TAG} does not exist."
                    }

                    bat '''
                        git checkout --force "tags/%RELEASE_TAG%"
                        git rev-parse HEAD > selected_commit.txt
                    '''

                    def selectedCommit =
                        readFile('selected_commit.txt').trim()

                    echo "========================================"
                    echo "SELECTED RELEASE TAG : ${env.RELEASE_TAG}"
                    echo "SELECTED GIT COMMIT  : ${selectedCommit}"
                    echo "========================================"

                    env.SELECTED_COMMIT = selectedCommit
                }
            }
        }

        stage('Build and Test') {
            steps {
                bat '''
                    echo Running Maven tests...
                    mvn -B clean test package
                '''
            }
        }

        stage('Prepare Docker Network') {
            steps {
                bat '''
                    docker network inspect %DOCKER_NETWORK% >nul 2>&1
                    if errorlevel 1 (
                        echo Creating Docker network %DOCKER_NETWORK%
                        docker network create %DOCKER_NETWORK%
                    ) else (
                        echo Docker network %DOCKER_NETWORK% already exists
                    )
                '''
            }
        }

        stage('Build Docker Image') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'DEPLOY'
                }
            }

            steps {
                bat '''
                    echo Building Docker image...
                    docker build -t %APP_NAME%:%VERSION%-%BUILD_NUMBER% .

                    echo Creating stable release image tag...
                    docker tag %APP_NAME%:%VERSION%-%BUILD_NUMBER% %APP_NAME%:%VERSION%

                    echo.
                    echo Docker images:
                    docker images %APP_NAME%
                '''
            }
        }

        stage('Record Previous Production Image') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'DEPLOY'
                }
            }

            steps {
                script {

                    def inspectStatus = bat(
                        returnStatus: true,
                        script: 'docker inspect %CONTAINER_NAME% >nul 2>&1'
                    )

                    if (inspectStatus == 0) {

                        bat '''
                            docker inspect %CONTAINER_NAME% --format="{{.Config.Image}}" > previous_image.txt
                        '''

                        env.PREVIOUS_IMAGE =
                            readFile('previous_image.txt').trim()

                        echo "Previous running image: ${env.PREVIOUS_IMAGE}"

                    } else {

                        if (
                            params.ENVIRONMENT == 'PRODUCTION' &&
                            params.VERSION != '4.2.0'
                        ) {
                            env.PREVIOUS_IMAGE = 'retail-app:4.2.0'
                        } else {
                            env.PREVIOUS_IMAGE = 'retail-app:4.2.0'
                        }

                        echo "No existing container found."
                        echo "Using fallback previous image: ${env.PREVIOUS_IMAGE}"
                    }

                    writeFile(
                        file: 'previous-production-image.txt',
                        text: env.PREVIOUS_IMAGE
                    )
                }
            }
        }

        stage('Deploy Candidate') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'DEPLOY'
                }
            }

            steps {
                script {

                    env.CANDIDATE_CONTAINER =
                        "${env.CONTAINER_NAME}-candidate-${env.BUILD_NUMBER}"

                    /*
                     * 4.2.2 is intentionally configured as unhealthy.
                     * This is the mandatory rollback demonstration.
                     */
                    if (params.VERSION == '4.2.2') {
                        env.CANDIDATE_HEALTH = 'false'
                        echo "FAILURE TEST MODE: ${params.VERSION} will fail health check."
                    } else {
                        env.CANDIDATE_HEALTH = 'true'
                    }

                    bat '''
                        echo Starting candidate container...
                        docker rm -f %CANDIDATE_CONTAINER% >nul 2>&1

                        docker run -d ^
                          --name %CANDIDATE_CONTAINER% ^
                          --network %DOCKER_NETWORK% ^
                          -p %UAT_PORT%:8081 ^
                          -e APP_VERSION=%VERSION% ^
                          -e APP_ENV=%ENVIRONMENT% ^
                          -e APP_HEALTHY=%CANDIDATE_HEALTH% ^
                          %APP_NAME%:%VERSION%
                    '''

                    echo "Candidate container started: ${env.CANDIDATE_CONTAINER}"
                }
            }
        }

        stage('Candidate Health Check') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'DEPLOY'
                }
            }

            steps {
                script {

                    def healthy = false

                    for (int attempt = 1; attempt <= 18; attempt++) {

                        def status = bat(
                            returnStdout: true,
                            script: '''
                                @echo off
                                docker inspect --format="{{.State.Health.Status}}" %CANDIDATE_CONTAINER% 2>nul
                            '''
                        ).trim()

                        echo "Health check attempt ${attempt}: ${status}"

                        if (status == 'healthy') {
                            healthy = true
                            break
                        }

                        if (status == 'unhealthy') {
                            break
                        }

                        bat 'timeout /t 5 /nobreak >nul'
                    }

                    if (!healthy) {
                        error "Candidate health check FAILED for ${env.CANDIDATE_CONTAINER}"
                    }

                    echo "Candidate health check PASSED."
                }
            }
        }

        stage('Switch Production/UAT') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'DEPLOY'
                }
            }

            steps {
                script {

                    echo "========================================"
                    echo "Previous image : ${env.PREVIOUS_IMAGE}"
                    echo "New image      : ${env.APP_NAME}:${params.VERSION}"
                    echo "Environment    : ${params.ENVIRONMENT}"
                    echo "========================================"

                    /*
                     * Candidate has already started and passed health check.
                     * Only now do we remove the old container.
                     */
                    bat '''
                        echo Removing old container after candidate passed health check...
                        docker rm -f %CONTAINER_NAME% >nul 2>&1

                        echo Starting final container...
                        docker run -d ^
                          --name %CONTAINER_NAME% ^
                          --network %DOCKER_NETWORK% ^
                          -p %HOST_PORT%:8081 ^
                          -e APP_VERSION=%VERSION% ^
                          -e APP_ENV=%ENVIRONMENT% ^
                          -e APP_HEALTHY=true ^
                          %APP_NAME%:%VERSION%
                    '''

                    echo "Final container started."
                }
            }
        }

        stage('Final Health Check') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'DEPLOY'
                }
            }

            steps {
                script {

                    def healthy = false

                    for (int attempt = 1; attempt <= 12; attempt++) {

                        def status = bat(
                            returnStdout: true,
                            script: '''
                                @echo off
                                docker inspect --format="{{.State.Health.Status}}" %CONTAINER_NAME% 2>nul
                            '''
                        ).trim()

                        echo "Final health check attempt ${attempt}: ${status}"

                        if (status == 'healthy') {
                            healthy = true
                            break
                        }

                        if (status == 'unhealthy') {
                            break
                        }

                        bat 'timeout /t 5 /nobreak >nul'
                    }

                    if (!healthy) {
                        error "Final application health check FAILED."
                    }

                    echo "Final application health check PASSED."
                }
            }
        }

        stage('Manual Rollback') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'ROLLBACK'
                }
            }

            steps {
                script {

                    def rollbackImage =
                        "${env.APP_NAME}:${params.VERSION}"

                    echo "========================================"
                    echo "MANUAL ROLLBACK"
                    echo "Restoring image: ${rollbackImage}"
                    echo "Environment: ${params.ENVIRONMENT}"
                    echo "========================================"

                    def imageStatus = bat(
                        returnStatus: true,
                        script: 'docker image inspect "%APP_NAME%:%VERSION%" >nul 2>&1'
                    )

                    if (imageStatus != 0) {
                        error "Rollback image ${rollbackImage} does not exist."
                    }

                    bat '''
                        docker rm -f %CONTAINER_NAME% >nul 2>&1

                        docker run -d ^
                          --name %CONTAINER_NAME% ^
                          --network %DOCKER_NETWORK% ^
                          -p %HOST_PORT%:8081 ^
                          -e APP_VERSION=%VERSION% ^
                          -e APP_ENV=%ENVIRONMENT% ^
                          -e APP_HEALTHY=true ^
                          %APP_NAME%:%VERSION%
                    '''

                    echo "Rollback container started."
                }
            }
        }

        stage('Rollback Health Check') {
            when {
                expression {
                    params.DEPLOYMENT_ACTION == 'ROLLBACK'
                }
            }

            steps {
                script {

                    def healthy = false

                    for (int attempt = 1; attempt <= 12; attempt++) {

                        def status = bat(
                            returnStdout: true,
                            script: '''
                                @echo off
                                docker inspect --format="{{.State.Health.Status}}" %CONTAINER_NAME% 2>nul
                            '''
                        ).trim()

                        echo "Rollback health check attempt ${attempt}: ${status}"

                        if (status == 'healthy') {
                            healthy = true
                            break
                        }

                        bat 'timeout /t 5 /nobreak >nul'
                    }

                    if (!healthy) {
                        error "Rollback health check FAILED."
                    }

                    echo "Rollback health check PASSED."
                }
            }
        }
    }

    post {

        success {
            echo "========================================"
            echo "DEPLOYMENT RESULT: SUCCESS"
            echo "VERSION: ${params.VERSION}"
            echo "ENVIRONMENT: ${params.ENVIRONMENT}"
            echo "========================================"

            bat '''
                echo Final Docker container:
                docker ps

                echo.
                echo Final image:
                docker images %APP_NAME%

                echo.
                echo Container health:
                docker inspect --format="{{.State.Health.Status}}" %CONTAINER_NAME% 2>nul || echo Container not found
            '''
        }

        failure {
            script {

                if (
                    params.DEPLOYMENT_ACTION == 'DEPLOY'
                ) {

                    echo "========================================"
                    echo "DEPLOYMENT FAILURE DETECTED"
                    echo "Starting automatic rollback..."
                    echo "========================================"

                    def rollbackImage = env.PREVIOUS_IMAGE

                    if (
                        rollbackImage == null ||
                        rollbackImage.trim() == ''
                    ) {
                        rollbackImage = 'retail-app:4.2.0'
                    }

                    echo "Rollback image: ${rollbackImage}"
                    env.ROLLBACK_IMAGE = rollbackImage

                    bat '''
                        echo Removing failed candidate...
                        docker rm -f %CANDIDATE_CONTAINER% >nul 2>&1

                        echo Removing failed production container...
                        docker rm -f %CONTAINER_NAME% >nul 2>&1

                        echo Starting rollback container...
                        docker run -d ^
                          --name %CONTAINER_NAME% ^
                          --network %DOCKER_NETWORK% ^
                          -p %HOST_PORT%:8081 ^
                          -e APP_VERSION=rollback ^
                          -e APP_ENV=%ENVIRONMENT% ^
                          -e APP_HEALTHY=true ^
                          %ROLLBACK_IMAGE%
                    '''

                    echo "Rollback container started."

                    def rollbackHealthy = false

                    for (int attempt = 1; attempt <= 12; attempt++) {

                        def status = bat(
                            returnStdout: true,
                            script: '''
                                @echo off
                                docker inspect --format="{{.State.Health.Status}}" %CONTAINER_NAME% 2>nul
                            '''
                        ).trim()

                        echo "Rollback health check attempt ${attempt}: ${status}"

                        if (status == 'healthy') {
                            rollbackHealthy = true
                            break
                        }

                        bat 'timeout /t 5 /nobreak >nul'
                    }

                    if (rollbackHealthy) {

                        echo "========================================"
                        echo "AUTOMATIC ROLLBACK VERIFIED"
                        echo "Restored image: ${rollbackImage}"
                        echo "Final state: ROLLBACK SUCCESSFUL"
                        echo "========================================"

                    } else {

                        echo "========================================"
                        echo "AUTOMATIC ROLLBACK FAILED"
                        echo "Manual intervention required."
                        echo "========================================"
                    }
                }
            }
        }

        always {
            echo "========================================"
            echo "Build completed."
            echo "Build number: ${env.BUILD_NUMBER}"
            echo "========================================"
        }
    }
}