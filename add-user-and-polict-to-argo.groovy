def updateArgocdConfig(String configContent, String newUser, String newRole, String rolePolicy) {
    def yaml = new org.yaml.snakeyaml.Yaml()
    def config = yaml.load(configContent)
    
    // Add new user to accounts section
    if (!config['argo-cd'].configs.cm.containsKey("accounts.${newUser}")) {
        config['argo-cd'].configs.cm["accounts.${newUser}"] = "login"
    }
    
    // Get existing RBAC policy from rbac/policy.csv
    def currentPolicy = config['argo-cd'].configs.rbac['policy.csv'] ?: ''
    def policies = currentPolicy.trim().split('\n').toList()
    
    // Add new role policy if it doesn't exist
    if (!currentPolicy.contains(rolePolicy)) {
        policies.add(rolePolicy)
    }
    
    // Add user to role mapping
    def userRoleMapping = "g, ${newUser}, ${newRole}"
    if (!currentPolicy.contains(userRoleMapping)) {
        policies.add(userRoleMapping)
    }
    
    // Update the policy in the config maintaining the YAML structure
    config['argo-cd'].configs.rbac['policy.csv'] = policies.join('\n')
    
    // Convert back to YAML preserving the formatting
    def options = new org.yaml.snakeyaml.DumperOptions()
    options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK)
    options.setPrettyFlow(true)
    def yamlWriter = new org.yaml.snakeyaml.Yaml(options)
    return yamlWriter.dump(config)
}


// Uses Declarative syntax to run commands inside a container.
pipeline {
    agent {
        kubernetes {
           label "kaniko-git"
        }
    }
    stages {
        stage('Main') {
            steps {
                script {

                    cleanWs()
                    sh "git clone --branch tgz-chart https://github.com/volk22kamin/argoCD.git"
                    dir("argoCD/charts/argo-cd") {
                        sh "ls -la"
                        def configContent = readFile 'values.yaml'
                        
                        // Update the configuration
                        def updatedContent = updateArgocdConfig(
                            configContent,
                            "maor",
                            "role:developer",
                            'p, role:developer, applications, *, *, allow'
                        )
                        
                        // Write the updated content back to the file
                        writeFile file: 'values.yaml', text: updatedContent
                        sh "cat values.yaml"
                        sh """
                            git config --global user.email "velvel2k@gmail.com"
                            git config --global user.name "volk22kamin"
                        """
                        sh "git commit -am \"added user\""
                        withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                            sh """
                                git push https://${USERNAME}:<TOKEN>@github.com/volk22kamin/argoCD.git
                            """
                        }

                    }
                }
            }
        }
    }
}

