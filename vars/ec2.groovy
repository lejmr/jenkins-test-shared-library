import jenkins.model.*
import hudson.model.*
import hudson.slaves.*
import hudson.plugins.sshslaves.*
import groovy.transform.Field

// Function allowing register an EC2 instance as a Jenkins agent
String register(String serverId, String sshCredentials, String awsRegion, String awsCredentials, Map ec2Filter, Boolean startEc2 = true, Integer numExecutors=3){

  // Calculate filter section out of ec2Filter
  String awsFilter = "--filters "
  awsFilter += ec2Filter.collect{
    key,val -> "'Name=${key},Values=${val}'"
  }.join(" ")

  // Determine full agent name
  String agent_name = ""
  node {
    withCredentials([sshUserPrivateKey(credentialsId: sshCredentials, keyFileVariable: 'private_key', usernameVariable: 'ssh_username')]) {
      agent_name = "${serverId}_${ssh_username}".toLowerCase()
    }
  }
  String lock_name = "agent-${agent_name}".toLowerCase()

  // Check if this agent is already registered or not
  echo "Registering ${env.BUILD_TAG} to ${agent_name}"
  lock(resource: lock_name){
    if(Jenkins.instance.getNode(agent_name)){
      // Jenkins agent is already registered, so only a label can be added
      echo "Adding label ${env.BUILD_TAG}"
      String[] agentLabels = Jenkins.instance.getNode(agent_name).getLabelString().split(' ')
      agentLabels += [env.BUILD_TAG]
      Jenkins.instance.getNode(agent_name).setLabelString(agentLabels.join(' '))
      agentLabels = null
    }else{
      // Jenkins agent is not registered yet
      echo "Registering a first EC2 instance searchable by ${awsFilter}"
      node("master"){
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: awsCredentials]]){
          def result = sh returnStdout: true, script: "aws ec2 describe-instances  --region ${awsRegion} ${awsFilter}"
          def metadata = readJSON(text: result.toString())
          def i = 0

          for(instances in metadata.Reservations){
            for(instance in instances.Instances){

              // Pick first server out of describe-instances
              echo "Discovered EC2 instance: ${instance.InstanceId}(${instance.PrivateIpAddress})"

              // Start instance if not in running state (if start is allowed)
              // https://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_InstanceState.html
              if(instance.State.Code > 16){
                echo "EC2 instance ${instance.InstanceId} is not in 'running' state, but ${instance.State.Name} waiting for 'stopped' state"
                sh "aws ec2 wait instance-stopped --region ${awsRegion} --instance-ids ${instance.InstanceId}"
              }
              if(startEc2){
                  // TODO: validate that server has tag jenkins_resource = true, so the Jenkins has rights to start/stop the EC2 instance
                  sh "aws ec2 start-instances --region ${awsRegion} --instance-ids ${instance.InstanceId}"
              }

              // Register EC2 as agent
              Slave slave = new DumbSlave(
                agent_name,
                "Persistent EC2 instance - ${instance.InstanceId}",
                "jenkins",
                "${numExecutors}",
                Node.Mode.NORMAL,
                // TODO: remove the first two labels as they are no longer necessary.. they are reguired by legacy pipelines
                "${agent_name} ${instance.InstanceId} ${env.BUILD_TAG}",
                new SSHLauncher(instance.PrivateIpAddress, 22, sshCredentials,
                ,"","","","",0,0,0,null),
                new RetentionStrategy.Always(),
                new LinkedList()
              )
              Jenkins.instance.addNode(slave)

              // Stop once first EC2 has been found
              if(++i>0) break
            }
          }

          // Break if no server was found
          if(i<=0){
             error("No server was found ${awsRegion}/${awsFilter} environment!!!")
          }
        }
      }
    }
  }

  // Return agent name having label registered
  return agent_name
}


// Function for deregistering Job from Jenkins. If no other jobs are present EC2 can be stopped
def deregister(String agent_name, String awsRegion, String awsCredentials, Boolean stopEc2 = true){
    String lock_name = "agent-${agent_name}".toLowerCase()
    lock(resource: lock_name){
      // Magic..
      def agentLabels = ["do", "nothing"]

      // Remove tag
      if(Jenkins.instance.getNode(agent_name)){
        // If there is an Jenkins agent of name agent_name .. remove tag. Otherwise do nothing
        echo "Removing BUILD_TAG ($env.BUILD_TAG) from ${agent_name}"
        agentLabels = Jenkins.instance.getNode(agent_name).getLabelString().split(' ')
        agentLabels -= [env.BUILD_TAG]
        Jenkins.instance.getNode(agent_name).setLabelString(agentLabels.join(' '))
      }

      // Stop EC2 instance if possible
      if(stopEc2 && agentLabels.length <= 2){
        // Find EC2 instance ID based on IP address of agent
        def local_ip = Jenkins.instance.getNode(agent_name).getLauncher().getHost()

        node("master"){
          withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: awsCredentials]]){
            // Describe server by private IP
            def result = sh returnStdout: true,
            script: "aws ec2 describe-instances  --region ${awsRegion} --filters 'Name=network-interface.addresses.private-ip-address,Values=${local_ip}'"
            def metadata = readJSON(text: result.toString())

            // Process the EC2 instance describe JSON response
            for(instances in metadata.Reservations){
              for(instance in instances.Instances){
                echo "Discovered EC2 instance ${instance.InstanceId}(${instance.PrivateIpAddress}) to be stopped"

                // Remove Jenkins agent
                Jenkins.instance.removeNode(Jenkins.instance.getNode(agent_name))

                // Stop running/pending EC2 instance
                if(instance.State.Code <= 16){
                  // TODO validate jenkins_resource = true tag is set 
                  echo "EC2 instance ${instance.InstanceId} is not in 'running' state, but ${instance.State.Name} waiting for 'stopped' state"
                  sh "aws ec2 wait instance-running --region ${awsRegion} --instance-ids ${instance.InstanceId}"
                  sh "aws ec2 stop-instances --region ${awsRegion} --instance-ids ${instance.InstanceId}"
                }

                // Stop only one server
                break
              }
            }
          }
        }
      }
    }
}
