def runCommand(String linuxCommand, String windowsCommand = null) {
    if (isUnix()) {
        sh linuxCommand
    } else {
        bat(windowsCommand ?: linuxCommand)
    }
}

def gradle(String args) {
    runCommand(
        "./gradlew --no-daemon --console=plain ${args}",
        ".\\gradlew.bat --no-daemon --console=plain ${args}"
    )
}

def deployService(Map service, String namespace, String imageRepository, String imageTag) {
    withCredentials([file(credentialsId: 'bank-kubeconfig', variable: 'KUBECONFIG')]) {
        def command = "helm upgrade --install ${service.serviceName} ${service.chartPath} --namespace ${namespace} --create-namespace --rollback-on-failure --timeout 5m -f ${service.valuesPath} --set image.repository=${imageRepository} --set image.tag=${imageTag}"
        runCommand(
            "${command} --dry-run=client"
        )
        runCommand(command)
    }
}

def runServicePipeline(Map service) {
    properties([
        parameters([
            string(name: 'IMAGE_REGISTRY', defaultValue: 'registry.example.com/my-bank', description: 'Container registry namespace'),
            string(name: 'IMAGE_TAG', defaultValue: '', description: 'Image tag. Empty value uses Jenkins BUILD_NUMBER.'),
            booleanParam(name: 'BUILD_IMAGE', defaultValue: false, description: 'Build image using Docker'),
            booleanParam(name: 'PUSH_IMAGE', defaultValue: false, description: 'Build and push image to registry'),
            booleanParam(name: 'DEPLOY_TEST', defaultValue: false, description: 'Deploy chart to test namespace'),
            booleanParam(name: 'DEPLOY_PROD', defaultValue: false, description: 'Deploy chart to prod namespace after manual approval')
        ])
    ])

    def imageTag = params.IMAGE_TAG?.trim() ? params.IMAGE_TAG.trim() : env.BUILD_NUMBER
    def registryHost = params.IMAGE_REGISTRY.tokenize('/')[0]
    def imageRepository = "${params.IMAGE_REGISTRY}/${service.imageRepository}"
    def image = "${imageRepository}:${imageTag}"

    stage('Validate') {
        gradle("${service.gradleModule}:compileJava ${service.gradleModule}:processResources")
    }

    stage('Java tests (Embedded Kafka, no Docker)') {
        gradle("${service.gradleModule}:test")
        if (fileExists("${service.serviceName}/src/contractTest")) {
            gradle("${service.gradleModule}:contractTest")
        }
    }

    stage('bootJar') {
        gradle("${service.gradleModule}:clean ${service.gradleModule}:bootJar")
    }

    stage('Docker build') {
        if (params.BUILD_IMAGE || params.PUSH_IMAGE) {
            runCommand("docker build -t ${image} ${service.serviceName}")
        } else {
            echo 'Docker build skipped by parameter.'
        }
    }

    stage('Image push') {
        if (params.PUSH_IMAGE) {
            withCredentials([usernamePassword(credentialsId: 'bank-registry-credentials', usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
                runCommand(
                    "printf '%s' \"\$REGISTRY_PASSWORD\" | docker login ${registryHost} --username \"\$REGISTRY_USERNAME\" --password-stdin",
                    "echo %REGISTRY_PASSWORD%| docker login ${registryHost} --username %REGISTRY_USERNAME% --password-stdin"
                )
                runCommand("docker push ${image}")
            }
        } else {
            echo 'Image push skipped by parameter.'
        }
    }

    stage('Helm lint and template') {
        runCommand("helm lint ${service.chartPath} -f ${service.valuesPath}")
        runCommand("helm template ${service.serviceName} ${service.chartPath} --namespace test -f ${service.valuesPath} --set image.repository=${imageRepository} --set image.tag=${imageTag}")
    }

    stage('Deploy test') {
        if (params.DEPLOY_TEST) {
            deployService(service, 'test', imageRepository, imageTag)
        } else {
            echo 'Test deploy skipped by parameter.'
        }
    }

    stage('Manual approval') {
        if (params.DEPLOY_PROD) {
            input message: "Deploy ${service.serviceName} to prod?", ok: 'Deploy'
        } else {
            echo 'Production deploy skipped by parameter.'
        }
    }

    stage('Deploy prod') {
        if (params.DEPLOY_PROD) {
            deployService(service, 'prod', imageRepository, imageTag)
        } else {
            echo 'Production deploy skipped by parameter.'
        }
    }
}

return this
