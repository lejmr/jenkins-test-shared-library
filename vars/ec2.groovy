// Function allowing register an EC2 instance as a Jenkins agent
String register(String serverId, String sshCredentials, String awsCredentials, Map ec2Filter){

  // Calculate filter section out of ec2Filter
  String awsFilter = ec2Filter.collect{
    key,val -> "--filters '${key},Values=${val}'"
  }.join(" ")

  // Determine full agent name
  String agent_name = ""
  node {
    withCredentials([sshUserPrivateKey(credentialsId: sshCredentials, keyFileVariable: 'private_key', usernameVariable: 'ssh_username')]) {
      sh '''echo $TOKEN'''
      agent_name = "${serverId}_${ssh_username}".toLowerCase()
    }
  }

  echo agent_name
  return "AgentName"
}


// Function for deregistering Job from Jenkins. If no other jobs are present EC2 can be stopped
def deregister(String agentLabel, String awsCredentials, Boolean stopEc2 = true){

}
